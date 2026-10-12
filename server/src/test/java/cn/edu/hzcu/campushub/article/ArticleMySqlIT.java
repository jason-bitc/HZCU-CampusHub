package cn.edu.hzcu.campushub.article;

import cn.edu.hzcu.campushub.CampusHubApplication;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP + Flyway + MySQL. Opt-in Maven profile; never substitutes H2 or mock responses. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ArticleMySqlIT {
    private static ConfigurableApplicationContext context;
    private static TcpProxy proxy;
    private static String base;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @BeforeAll static void start() throws Exception {
        require("CAMPUSHUB_TEST_DB_USERNAME"); require("CAMPUSHUB_TEST_DB_PASSWORD");
        proxy = new TcpProxy(System.getenv().getOrDefault("CAMPUSHUB_DB_HOST","127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("CAMPUSHUB_DB_PORT","3306")));
        boot();
        var jdbc = context.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("campushub_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM article",Long.class)).isEqualTo(10);
    }
    static void boot() {
        var app = application();
        context = app.run("--server.port=0", "--server.address=127.0.0.1", "--spring.profiles.active=demo",
                "--campushub.db.host=127.0.0.1", "--campushub.db.port=" + proxy.port(),
                "--campushub.db.name=campushub_test");
        base = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
    }
    static SpringApplication application() {
        var app = new SpringApplication(CampusHubApplication.class);
        app.setDefaultProperties(Map.of("spring.main.banner-mode","off"));
        app.addInitializers(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("test-credentials",Map.of(
                    "campushub.db.username",require("CAMPUSHUB_TEST_DB_USERNAME"),
                    "campushub.db.password",require("CAMPUSHUB_TEST_DB_PASSWORD")))));
        // Supply secrets as properties in memory, never command-line arguments.
        return app;
    }
    static String require(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + key + " before MySQL integration tests");
        return value;
    }
    @AfterAll static void stop() throws Exception { if (context != null) context.close(); if (proxy != null) proxy.close(); }

    record Reply(int status, JsonNode json, String raw, String trace) {}
    static Reply get(String path) throws Exception { return request("GET",path); }
    static Reply request(String method, String path) throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(java.time.Duration.ofSeconds(20))
                .header("X-Trace-Id","untrusted-input").method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        JsonNode json = JSON.readTree(response.body());
        String trace = json.path("traceId").asString();
        assertThat(trace).matches("[0-9a-f-]{36}");
        assertThat(response.headers().firstValue("X-Trace-Id")).contains(trace);
        if (response.statusCode() >= 400) {
            assertThat(json.has("data")).isFalse(); assertThat(json.path("message").asString()).isNotBlank();
        } else {
            assertThat(json.path("code").asString()).isEqualTo("OK"); assertThat(json.has("message")).isFalse();
        }
        return new Reply(response.statusCode(),json,response.body(),trace);
    }
    static List<Long> ids(Reply r) {
        List<Long> values = new ArrayList<>(); r.json.path("data").path("items").forEach(i -> values.add(i.path("id").asLong())); return values;
    }
    static String keyword(String value) { return "/api/v1/articles?keyword=" + URLEncoder.encode(value,StandardCharsets.UTF_8); }

    @Test @Order(1) void healthCatalogAndEnvelope() throws Exception {
        var health = get("/actuator/health"); assertThat(health.status).isEqualTo(200);
        assertThat(health.json.path("data").path("status").asString()).isEqualTo("UP");
        assertThat(health.raw).doesNotContain("components","jdbc","password","details");
        var catalog = get("/api/v1/catalog"); assertThat(catalog.status).isEqualTo(200);
        assertThat(catalog.json.path("data").path("jobs").size()).isEqualTo(2);
        assertThat(catalog.json.path("data").path("majors").size()).isEqualTo(2);
        assertThat(catalog.json.path("data").path("companies").size()).isEqualTo(2);
        assertThat(catalog.json.path("data").path("topics").size()).isEqualTo(3);
        assertThat(get("/actuator/env").status).isEqualTo(404);
    }
    @Test @Order(2) void sortingExercisesCoinsLikesTimeAndStableId() throws Exception {
        var recognized = get("/api/v1/articles"); assertThat(recognized.status).isEqualTo(200);
        assertThat(ids(recognized)).containsExactly(1002L,1001L,1006L,1003L,1004L,1005L);
        assertThat(ids(get("/api/v1/articles?sort=latest"))).containsExactly(1005L,1004L,1003L,1002L,1001L,1006L);
        assertThat(recognized.json.path("data").path("total").asLong()).isEqualTo(6);
        assertThat(recognized.json.path("data").path("size").asInt()).isEqualTo(20);
    }
    @Test @Order(3) void keywordAndAllFiltersIntersect() throws Exception {
        assertThat(ids(get(keyword("Java")))).containsExactly(1002L,1001L,1003L);
        assertThat(ids(get(keyword("SQL特殊字符")))).containsExactly(1006L);
        assertThat(ids(get("/api/v1/articles?jobId=1&majorId=11&companyId=21&topicId=31"))).containsExactly(1001L,1006L);
        assertThat(ids(get("/api/v1/articles?jobId=2&majorId=11"))).isEmpty();
        assertThat(ids(get("/api/v1/articles?jobId=21"))).isEmpty();
        assertThat(ids(get("/api/v1/articles?companyId=999999"))).isEmpty();
        assertThat(ids(get(keyword("不存在的演示关键字")))).isEmpty();
        assertThat(ids(get(keyword("未公开修订")))).isEmpty();
        assertThat(ids(get("/api/v1/articles?jobId=1&topicId=33"))).containsExactly(1005L);
        assertThat(get(keyword("不存在的演示关键字")).json.path("data").path("total").asLong()).isZero();
    }
    @Test @Order(4) void paginationHasNoDuplicatesOrOmissionsForStableData() throws Exception {
        for (String sort : List.of("recognized","latest")) {
            var expected = ids(get("/api/v1/articles?sort=" + sort)); List<Long> paged = new ArrayList<>();
            for (int p=1; p<=3; p++) {
                var reply = get("/api/v1/articles?sort=" + sort + "&page=" + p + "&size=2");
                assertThat(reply.json.path("data").path("total").asLong()).isEqualTo(6); paged.addAll(ids(reply));
            }
            assertThat(paged).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        }
        assertThat(ids(get("/api/v1/articles?page=4&size=2"))).isEmpty();
        assertThat(ids(get("/api/v1/articles?page=1000000&size=100"))).isEmpty();
        assertThat(ids(get("/api/v1/articles?size=100"))).hasSize(6);
    }
    @Test @Order(5) void currentPublicVersionOnlyAndNoRestrictedFields() throws Exception {
        var detail = get("/api/v1/articles/1001"); assertThat(detail.status).isEqualTo(200);
        var data = detail.json.path("data");
        assertThat(data.path("bodyFormat").asString()).isEqualTo("MARKDOWN");
        assertThat(data.path("body").asString()).contains("虚构教学示例","自愿联系文字");
        assertThat(data.path("sourceLinks").get(0).path("url").asString()).isEqualTo("https://dev.java/learn/");
        assertThat(data.path("sourceType").asString()).isEqualTo("DEMO");
        assertThat(data.path("counts").path("origin").asString()).isEqualTo("DEMO");
        assertThat(data.path("categories").path("jobs").get(0).path("id").asLong()).isEqualTo(1);
        assertThat(detail.raw).doesNotContain("PRIVATE_","未公开修订");
        for (var node : List.of(data,get("/api/v1/articles").json.path("data"))) assertNoRestrictedKeys(node);
    }
    static void assertNoRestrictedKeys(JsonNode node) {
        if (node.isObject()) for (var entry : node.properties()) {
            assertThat(entry.getKey()).isNotIn("authorId","nickname","avatar","email","contact","contactInfo","comments","accountId","verificationMaterials");
            assertNoRestrictedKeys(entry.getValue());
        } else if (node.isArray()) node.forEach(ArticleMySqlIT::assertNoRestrictedKeys);
    }
    @Test @Order(6) void everyNonPublicStateAndMissingArticleReturnIdentical404() throws Exception {
        String message = null;
        for (long id : List.of(2001L,2002L,2003L,2004L,999999L)) {
            var reply = get("/api/v1/articles/" + id); assertThat(reply.status).isEqualTo(404);
            assertThat(reply.json.path("code").asString()).isEqualTo("NOT_FOUND");
            if (message == null) message=reply.json.path("message").asString();
            assertThat(reply.json.path("message").asString()).isEqualTo(message);
            assertThat(reply.raw).doesNotContain("PRIVATE_","DRAFT","AUTHOR_OFFLINE","ADMIN_HIDDEN","DELETED");
        }
        assertThat(ids(get("/api/v1/articles"))).doesNotContain(2001L,2002L,2003L,2004L);
    }
    @Test @Order(7) void invalidInputsUnknownPathsAndWriteMethods() throws Exception {
        for (String path : List.of("?page=0","?size=0","?size=101","?page=1000001","?jobId=-1","?sort=bad",
                "?page=1&page=2","?authorId=1","?majorId=", "?keyword=" + "x".repeat(101),"?keyword=x%0Ay"))
            assertThat(get("/api/v1/articles" + path).status).isEqualTo(400);
        for (String id : List.of("0","-1","abc","9223372036854775808")) assertThat(get("/api/v1/articles/"+id).status).isEqualTo(400);
        assertThat(get("/api/v1/missing").status).isEqualTo(404);
        assertThat(request("POST","/api/v1/articles").status).isEqualTo(405);
        assertThat(request("DELETE","/api/v1/articles/1001").status).isEqualTo(405);
    }
    @Test @Order(8) void sqlSpecialCharactersAreLiteralAndCannotChangeQueryStructure() throws Exception {
        for (String value : List.of("%","_","!","100%")) assertThat(ids(get(keyword(value)))).containsExactly(1006L);
        for (String value : List.of("' OR 1=1 --", "'; DROP TABLE article; --", "\\", "'")) assertThat(ids(get(keyword(value)))).isEmpty();
        assertThat(get("/api/v1/articles?jobId=1%20OR%201=1").status).isEqualTo(400);
        assertThat(ids(get("/api/v1/articles"))).hasSize(6);
    }
    @Test @Order(9) void migrationValidationRepeatAndRestartPreserveData() throws Exception {
        var flyway = context.getBean(Flyway.class); flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        var jdbc = context.getBean(JdbcTemplate.class);
        var before = jdbc.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes FROM article ORDER BY id");
        var versions = jdbc.queryForList("SELECT id,title,body FROM article_version ORDER BY id");
        context.close(); boot();
        jdbc = context.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes FROM article ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT id,title,body FROM article_version ORDER BY id")).isEqualTo(versions);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE",Long.class)).isEqualTo(2);
        assertThat(get("/api/v1/articles/1001").status).isEqualTo(200);
    }
    @Test @Order(10) void invalidConfigurationAndChangedMigrationPreventStartup(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        assertThatThrownBy(() -> application().run("--server.port=0", "--campushub.db.name=other_project"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        // Deliberately different checksum, validation only; no alteration of the database or tracked migration.
        java.nio.file.Files.writeString(directory.resolve("V1__article_reading_schema.sql"),"-- deliberately mismatched checksum\nSELECT 1;\n");
        assertThatThrownBy(() -> application().run("--server.port=0", "--campushub.db.host=127.0.0.1",
                "--campushub.db.port="+proxy.port(),"--campushub.db.name=campushub_test",
                "--spring.flyway.locations=filesystem:"+directory.toString().replace('\\','/')+",classpath:db/demo"))
                .hasRootCauseInstanceOf(org.flywaydb.core.api.exception.FlywayValidateException.class);
        assertThat(get("/actuator/health").status).isEqualTo(200);
    }
    @Test @Order(11) void actualConnectionFailureIs503AndRecoveryRestoresReading() throws Exception {
        proxy.cut();
        for (String path : List.of("/api/v1/articles","/api/v1/articles/1001","/api/v1/catalog","/actuator/health")) {
            var reply = get(path); assertThat(reply.status).isEqualTo(503);
            assertThat(reply.json.path("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(reply.raw).doesNotContain("jdbc","SQLException","root","PRIVATE_");
        }
        proxy.restore();
        assertThat(get("/api/v1/articles").status).isEqualTo(200);
        assertThat(get("/actuator/health").status).isEqualTo(200);
    }

    /** Cut only this application's test TCP connections; never stop MySQL or other project services. */
    static final class TcpProxy implements AutoCloseable {
        private final ServerSocket server;
        private final String host;
        private final int upstream;
        private volatile boolean down;
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        TcpProxy(String host,int upstream) throws Exception {
            this.host=host; this.upstream=upstream; server=new ServerSocket(0,50,InetAddress.getLoopbackAddress());
            Thread.ofVirtual().start(() -> {
                while (!server.isClosed()) try {
                    Socket client=server.accept();
                    if (down) { client.close(); continue; }
                    Socket remote=new Socket(); remote.connect(new InetSocketAddress(host,upstream),3000);
                    sockets.add(client); sockets.add(remote);
                    if (down) { client.close(); remote.close(); continue; }
                    forward(client,remote); forward(remote,client);
                } catch (Exception ignored) { /* Expected on test shutdown or intentional cut. */ }
            });
        }
        int port() { return server.getLocalPort(); }
        void forward(Socket source,Socket target) {
            Thread.ofVirtual().start(() -> {
                try { source.getInputStream().transferTo(target.getOutputStream()); }
                catch (Exception ignored) { }
                finally { try { source.close(); target.close(); } catch (Exception ignored) { } sockets.remove(source); sockets.remove(target); }
            });
        }
        void cut() { down=true; for (Socket s:sockets) try { s.close(); } catch (Exception ignored) { } }
        void restore() { down=false; }
        public void close() throws Exception { cut(); server.close(); }
    }
}
