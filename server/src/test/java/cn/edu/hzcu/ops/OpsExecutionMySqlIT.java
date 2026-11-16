package cn.edu.hzcu.ops;

import cn.edu.hzcu.campushub.auth.AuthTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP -> independent executor -> real JVM + mysqldump/mysql, exclusively campushub_test. */
class OpsExecutionMySqlIT {
    @TempDir Path root;
    static final HttpClient HTTP=HttpClient.newHttpClient();
    String base,token;OpsConfig config;long fixture;String imageId;
    String username=System.getenv("CAMPUSHUB_TEST_DB_USERNAME"),password=System.getenv("CAMPUSHUB_TEST_DB_PASSWORD");
    final String mysqlPort = Integer.toString(Integer.parseInt(System.getenv().getOrDefault("CAMPUSHUB_DB_PORT", "3306")));
    Connection connection()throws Exception {return DriverManager.getConnection("jdbc:mysql://127.0.0.1:"+mysqlPort+"/campushub_test?connectTimeout=3000&socketTimeout=5000",username,password);}
    JsonNode request(String method,String path,Object body)throws Exception {
        return response(method,path,body,200).path("data");
    }
    JsonNode response(String method,String path,Object body,int status)throws Exception {
        var q=HttpRequest.newBuilder(URI.create(base+path)).timeout(java.time.Duration.ofSeconds(10));if(token!=null)q.header("Authorization","Bearer "+token);if(body!=null)q.header("Content-Type","application/json");
        var r=HTTP.send(q.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(OpsStore.JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).as(path+" "+r.body()).isEqualTo(status);return OpsStore.JSON.readTree(r.body());
    }
    JsonNode job(String action,String reference)throws Exception {
        var semantic=Map.of("action",action,"target","business","referenceId",reference);
        return submitConfirmed(semantic,request("POST","/confirmations",semantic));
    }
    JsonNode submitConfirmed(Map<String,String> semantic,JsonNode confirmation)throws Exception {
        var input=new LinkedHashMap<String,Object>(semantic);input.put("requestId",UUID.randomUUID().toString());input.put("confirmationId",confirmation.path("confirmationId").asString());String id=request("POST","/jobs",input).path("id").asString();
        return awaitJob(id);
    }
    JsonNode awaitJob(String id)throws Exception {
        for(int i=0;i<600;i++){var job=request("GET","/jobs/"+id,null);if(Set.of("SUCCEEDED","FAILED","INTERRUPTED").contains(job.path("state").asString()))return job;Thread.sleep(200);}throw new AssertionError("Task did not finish");
    }
    private OpsConfig opsConfig(Path environment, int opsPort, int businessPort, LocalTime time) {
        return new OpsConfig(root.resolve("control"),root.resolve("runtime"),root.resolve("artifacts"),
                root.resolve("backups"),root.resolve("media"),Path.of(System.getProperty("java.home"),"bin","java.exe"),
                Path.of(Objects.requireNonNull(System.getenv("CAMPUSHUB_OPS_TEST_MYSQL"))),
                Path.of(Objects.requireNonNull(System.getenv("CAMPUSHUB_OPS_TEST_DUMP"))),environment,opsPort,
                "http://127.0.0.1:"+businessPort+"/actuator/health","http://127.0.0.1:5173","campushub_test",time);
    }
    @Test void realDeployRestartBackupRestoreAndDamageRejectionThroughHttp()throws Exception {
        try(var app=AuthTestSupport.application(new AuthTestSupport.ControlledMailer(),new AuthTestSupport.MutableClock()).run("--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=demo")){}
        fixture=2_000_000_000_000L+new java.security.SecureRandom().nextLong(1_000_000_000L);
        try(var c=connection();var q=c.prepareStatement("INSERT INTO category(id,kind,name) VALUES(?,'TOPIC',?)")){q.setLong(1,fixture);q.setString(2,"ops-before-"+fixture);q.executeUpdate();}
        int businessPort,opsPort;try(var socket=new java.net.ServerSocket(0)){businessPort=socket.getLocalPort();}try(var socket=new java.net.ServerSocket(0)){opsPort=socket.getLocalPort();}
        Path env=root.resolve("business-env.json");OpsStore.atomic(env,OpsStore.JSON.writeValueAsBytes(Map.of(
                "CAMPUSHUB_DB_HOST","127.0.0.1","CAMPUSHUB_DB_PORT",mysqlPort,"CAMPUSHUB_DB_NAME","campushub_test",
                "CAMPUSHUB_DB_USERNAME",username,"CAMPUSHUB_DB_PASSWORD",password,"CAMPUSHUB_AUTH_PEPPER",AuthTestSupport.TEST_KEY,
                "CAMPUSHUB_LOG_ENABLED","true","CAMPUSHUB_RUN_MODE","release",
                "CAMPUSHUB_MAIL_HOST","127.0.0.1","CAMPUSHUB_MAIL_PASSWORD","smtp-not-exercised")));
        config=opsConfig(env,opsPort,businessPort,null);config.validate();
        Path media=config.mediaRoot().resolve("objects");
        Files.createDirectories(media);
        imageId=UUID.randomUUID().toString();
        Path image=media.resolve(imageId);
        byte[] original=png(0xff123456), newer=png(0xff654321);
        Files.write(image,original);
        try(var c=connection();var q=c.prepareStatement("""
                INSERT INTO media_image(id,article_id,request_id,content_digest,mime_type,size_bytes,width,height,created_at)
                VALUES(?,1001,?,?,'image/png',?,1,1,NOW(6))
                """)){
            q.setString(1,imageId);q.setString(2,UUID.randomUUID().toString());
            q.setString(3,OpsRunner.sha(image));q.setLong(4,Files.size(image));q.executeUpdate();
        }
        Path artifact=config.artifactRoot().resolve("release-one");Files.createDirectories(artifact);Path jar=artifact.resolve("application.jar");Files.copy(Path.of("target/campushub-server-0.1.0.jar"),jar);
        StringBuilder schema=new StringBuilder();try(var c=connection();var q=c.createStatement();var rows=q.executeQuery("SELECT version,checksum FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank")){while(rows.next())schema.append(rows.getString(1)).append(':').append(rows.getString(2)).append('\n');}
        OpsStore.atomic(artifact.resolve("manifest.json"),OpsStore.JSON.writeValueAsBytes(Map.of("id","release-one","sha256",OpsRunner.sha(jar),"schemaFingerprint",OpsAuth.digest(schema.toString()))));
        OpsStore.atomic(config.controlRoot().resolve("credentials.json"),OpsStore.JSON.writeValueAsBytes(Map.of("login","testops","passwordHash",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("test-password"))));
        try {
          try(var app=new OpsApplication(config)){
            app.start();base="http://127.0.0.1:"+opsPort+"/api/v1/ops";token=request("POST","/auth/login",Map.of("login","testops","password","test-password")).path("accessToken").asString();
            assertThat(request("GET","/status",null).path("healthy").asBoolean()).isFalse();
            var deployed=job("DEPLOY","release-one");assertThat(deployed.path("state").asString()).as(deployed.toString()).isEqualTo("SUCCEEDED");assertThat(request("GET","/status",null).path("release").path("id").asString()).isEqualTo("release-one");
            long pid=OpsStore.JSON.readTree(Files.readString(config.runtimeRoot().resolve("pid.json"))).path("pid").asLong();var restarted=job("RESTART","");assertThat(restarted.path("state").asString()).as(restarted.toString()).isEqualTo("SUCCEEDED");assertThat(OpsStore.JSON.readTree(Files.readString(config.runtimeRoot().resolve("pid.json"))).path("pid").asLong()).isNotEqualTo(pid);
            var backed=job("BACKUP","");assertThat(backed.path("state").asString()).as(backed.toString()).isEqualTo("SUCCEEDED");String backup=backed.path("result").path("backupId").asString();
            var inventory = request("GET", "/backups", null).path("items");
            assertThat(inventory.get(0).path("id").asString()).isEqualTo(backup);
            assertThat(inventory.get(0).path("withinRetentionWindow").asBoolean()).isTrue();
            Instant snapshotAt = Instant.parse(inventory.get(0).path("snapshotAt").asString());
            assertThat(snapshotAt).isBeforeOrEqualTo(Instant.parse(inventory.get(0).path("createdAt").asString()));
            Path handoff = Files.createDirectory(root.resolve("handoff"));
            var receipt = OpsBackupExport.export(config, backup, handoff);
            assertThat(receipt.verifiedFileCount()).isEqualTo(4);
            assertThat(OpsBackupExport.verify(handoff.resolve(backup), receipt.manifestSha256()).backupId()).isEqualTo(backup);
            assertThat(request("GET", "/status", null).path("healthy").asBoolean()).isTrue();
            updateLiveImage(image,newer,"ops-after-");
            var restoreRequest=Map.of("action","RESTORE","target","business","referenceId",backup);
            var confirmation=request("POST","/confirmations",restoreRequest);
            var preview=confirmation.path("restorePreview");
            assertThat(preview.path("backupId").asString()).isEqualTo(backup);
            assertThat(preview.path("backupCreatedAt").asString()).isNotBlank();
            assertThat(Instant.parse(preview.path("backupSnapshotAt").asString())).isEqualTo(snapshotAt);
            assertThat(preview.path("backupReleaseId").asString()).isEqualTo("release-one");
            assertThat(preview.path("affectedComponents").toString()).isEqualTo("[\"DATABASE\",\"MEDIA\",\"RELEASE\"]");
            assertThat(preview.path("verifiedFileCount").asInt()).isEqualTo(4);
            assertThat(preview.path("mediaFileCount").asInt()).isEqualTo(1);
            assertThat(preview.path("businessSnapshotAvailable").asBoolean()).isTrue();
            assertThat(preview.toString()).doesNotContain(config.backupRoot().toString(),"database.sql");
            var restored=submitConfirmed(restoreRequest,confirmation);
            assertThat(restored.path("state").asString()).as(restored.toString()).isEqualTo("SUCCEEDED");
            assertThat(restored.path("result").path("verifiedMediaReferences").asLong()).isEqualTo(1);
            var businessCheck = restored.path("result").path("businessVerification");
            assertThat(businessCheck.path("status").asString()).isEqualTo("VERIFIED");
            assertThat(businessCheck.path("checkedTables").size()).isEqualTo(8);
            assertThat(businessCheck.toString()).doesNotContain("sha256", "rows", "ops-before-");
            var recovery = restored.path("result").path("recoveryEvidence");
            var restoreStartedAt = Instant.parse(recovery.path("restoreStartedAt").asString());
            assertThat(Instant.parse(recovery.path("backupSnapshotAt").asString())).isEqualTo(snapshotAt);
            assertThat(recovery.path("snapshotAgeMillis").asLong())
                    .isEqualTo(java.time.Duration.between(snapshotAt, restoreStartedAt).toMillis());
            assertThat(recovery.path("restoreElapsedMillis").asLong()).isPositive();
            assertThat(recovery.path("rpoTargetStatus").asString()).isEqualTo("WITHIN_TARGET");
            assertThat(recovery.path("rtoTargetStatus").asString()).isEqualTo("WITHIN_TARGET");
            assertThat(Files.readAllBytes(image)).isEqualTo(original);
            assertLiveRows(image,"ops-before-");
            Path selected=config.backupRoot().resolve(backup),manifestPath=selected.resolve("manifest.json");
            assertThat(restored.path("backupManifestSha256").asString()).isEqualTo(OpsRunner.sha(manifestPath));
            var repeat=request("POST","/confirmations",restoreRequest);
            long livePid=runningPid().path("pid").asLong();
            String unchangedManifest=Files.readString(manifestPath);
            Files.writeString(manifestPath,unchangedManifest+"\n");
            var changed=submitConfirmed(restoreRequest,repeat);
            assertThat(changed.path("state").asString()).isEqualTo("FAILED");
            assertThat(changed.path("errorCode").asString()).isEqualTo("OPS_BACKUP_CHANGED");
            assertThat(changed.path("events").toString()).doesNotContain("STOPPING");
            Files.writeString(manifestPath,unchangedManifest);

            // A legacy package can have valid file checksums but inconsistent image metadata.
            updateLiveImage(image,newer,"ops-protected-");
            String mediaFile="media/objects/"+imageId;
            Path selectedImage=selected.resolve(mediaFile);
            Files.write(selectedImage,newer);
            var changedManifest=OpsStore.JSON.readTree(unchangedManifest);
            ((tools.jackson.databind.node.ObjectNode)changedManifest.path("files")).put(mediaFile,OpsRunner.sha(selectedImage));
            OpsStore.atomic(manifestPath,OpsStore.JSON.writeValueAsBytes(changedManifest));
            var mismatch=job("RESTORE",backup);
            assertThat(mismatch.path("state").asString()).isEqualTo("FAILED");
            assertThat(mismatch.path("errorCode").asString()).isEqualTo("OPS_MEDIA_REFERENCE_MISMATCH");
            assertThat(mismatch.path("result").has("recoveryEvidence")).isFalse();
            assertThat(mismatch.path("events").toString()).contains("VERIFYING_MEDIA","ROLLBACK_VERIFIED");
            assertThat(request("GET","/status",null).path("healthy").asBoolean()).isTrue();
            assertThat(Files.readAllBytes(image)).isEqualTo(newer);
            assertLiveRows(image,"ops-protected-");

            Files.write(selectedImage,original);
            Files.writeString(manifestPath,unchangedManifest);
            var inconsistentManifest = OpsStore.JSON.readTree(unchangedManifest);
            var categoryCheckpoint = (tools.jackson.databind.node.ObjectNode) inconsistentManifest
                    .path("businessSnapshot").path("tables").path("category");
            categoryCheckpoint.put("sha256", "0".repeat(64));
            OpsStore.atomic(manifestPath, OpsStore.JSON.writeValueAsBytes(inconsistentManifest));
            var businessMismatch = job("RESTORE", backup);
            assertThat(businessMismatch.path("state").asString()).isEqualTo("FAILED");
            assertThat(businessMismatch.path("errorCode").asString()).isEqualTo("OPS_BUSINESS_SNAPSHOT_MISMATCH");
            assertThat(businessMismatch.path("events").toString()).contains("VERIFYING_DATA", "ROLLBACK_VERIFIED");
            assertThat(businessMismatch.path("result").has("recoveryEvidence")).isFalse();
            assertThat(request("GET", "/status", null).path("healthy").asBoolean()).isTrue();
            assertLiveRows(image, "ops-protected-");
            Files.writeString(manifestPath,unchangedManifest);
            livePid=runningPid().path("pid").asLong();
            Files.writeString(selected.resolve("database.sql"),"broken");
            assertThat(response("POST","/confirmations",restoreRequest,409).path("code").asString()).isEqualTo("OPS_BACKUP_INVALID");
            assertThat(response("POST","/confirmations",Map.of("action","RESTORE","target","business",
                    "referenceId",UUID.randomUUID().toString()),404).path("code").asString()).isEqualTo("OPS_BACKUP_NOT_FOUND");
            assertThat(runningPid().path("pid").asLong()).isEqualTo(livePid);
            assertThat(request("GET","/status",null).path("healthy").asBoolean()).isTrue();
            assertThat(request("GET","/logs",null).has("taskEvents")).isTrue();assertThat(request("GET","/jobs",null).path("items").size()).isEqualTo(7);
          }
          try(var app=new OpsApplication(opsConfig(env,opsPort,businessPort,LocalTime.of(5,30)),
                Clock.fixed(Instant.parse("2030-01-01T21:30:00Z"),ZoneOffset.UTC))){
            long previousPid = runningPid().path("pid").asLong();
            app.start();token=null;
            token=request("POST","/auth/login",Map.of("login","testops","password","test-password")).path("accessToken").asString();
            JsonNode scheduled = null;
            for (int i=0;i<100;i++) {
                var latest = request("GET","/jobs",null).path("items").get(0);
                if ("OPS_SCHEDULER".equals(latest.path("actorDomain").asString())) {
                    scheduled = awaitJob(latest.path("id").asString());
                    break;
                }
                Thread.sleep(100);
            }
            assertThat(scheduled).isNotNull();
            assertThat(scheduled.path("state").asString()).as(scheduled.toString()).isEqualTo("SUCCEEDED");
            assertThat(scheduled.path("scheduledFor").asString()).isEqualTo("2030-01-02");
            assertThat(runningPid().path("pid").asLong()).isNotEqualTo(previousPid);
            var status = request("GET","/status",null);
            assertThat(status.path("healthy").asBoolean()).isTrue();
            assertThat(status.path("dailyBackup").path("timeZone").asString()).isEqualTo("Asia/Shanghai");
            assertThat(status.path("dailyBackup").path("lastScheduledFor").asString()).isEqualTo("2030-01-02");
            Path saved = config.backupRoot().resolve(scheduled.path("result").path("backupId").asString());
            var manifest = OpsStore.JSON.readTree(Files.readString(saved.resolve("manifest.json")));
            assertThat(manifest.path("complete").asBoolean()).isTrue();
            assertThat(manifest.path("verifiedMediaReferences").asLong()).isEqualTo(1);
            assertThat(manifest.path("businessSnapshot").path("version").asInt()).isEqualTo(1);
            assertThat(manifest.path("businessSnapshot").path("tables").size()).isEqualTo(8);
            assertThat(Files.readString(saved.resolve("database.sql"))).contains("ops-protected-"+fixture);
            for (String file : List.of("database.sql","media/objects/"+image.getFileName(),"application.jar","release.json")) {
                assertThat(manifest.path("files").path(file).asString()).isEqualTo(OpsRunner.sha(saved.resolve(file)));
            }
            stopTestBusiness();
            assertThat(request("GET","/status",null).path("healthy").asBoolean()).isFalse();
            assertThat(request("GET","/jobs",null).path("items").size()).isEqualTo(8);
          }
        } finally {
            stopTestBusiness();
            deleteFixture();
        }
        // Tasks and results survive an independent control-plane restart; sessions deliberately do not.
        try(var app=new OpsApplication(config)){app.start();token=null;token=request("POST","/auth/login",Map.of("login","testops","password","test-password")).path("accessToken").asString();assertThat(request("GET","/jobs",null).path("items").size()).isEqualTo(8);}
    }
    private static byte[] png(int rgb)throws Exception {
        var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);
        image.setRGB(0,0,rgb);
        var bytes=new ByteArrayOutputStream();
        ImageIO.write(image,"png",bytes);
        return bytes.toByteArray();
    }
    private void updateLiveImage(Path image,byte[] bytes,String label)throws Exception {
        Files.write(image,bytes);
        try(var c=connection();
            var cat=c.prepareStatement("UPDATE category SET name=? WHERE id=?");
            var media=c.prepareStatement("UPDATE media_image SET content_digest=?,size_bytes=? WHERE id=?")){
            cat.setString(1,label+fixture);cat.setLong(2,fixture);cat.executeUpdate();
            media.setString(1,OpsRunner.sha(image));media.setLong(2,Files.size(image));media.setString(3,imageId);
            media.executeUpdate();
        }
    }
    private void assertLiveRows(Path image,String label)throws Exception {
        try(var c=connection();
            var cat=c.prepareStatement("SELECT name FROM category WHERE id=?");
            var media=c.prepareStatement("SELECT content_digest,size_bytes FROM media_image WHERE id=?")){
            cat.setLong(1,fixture);media.setString(1,imageId);
            try(var rows=cat.executeQuery()){
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(label+fixture);
            }
            try(var rows=media.executeQuery()){
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(OpsRunner.sha(image));
                assertThat(rows.getLong(2)).isEqualTo(Files.size(image));
            }
        }
    }
    private JsonNode runningPid() throws Exception {
        return OpsStore.JSON.readTree(Files.readString(config.runtimeRoot().resolve("pid.json")));
    }
    private void stopTestBusiness() throws Exception {
        if (!Files.isRegularFile(config.runtimeRoot().resolve("pid.json"))) return;
        var state = runningPid();
        var process = ProcessHandle.of(state.path("pid").asLong()).filter(p ->
                p.info().startInstant().map(Instant::toString).orElse("").equals(state.path("started").asString()));
        if (process.isPresent()) {
            process.get().destroy();
            process.get().onExit().get(30,java.util.concurrent.TimeUnit.SECONDS);
        }
    }
    private void deleteFixture() throws Exception {
        try(var c=connection();
            var image=c.prepareStatement("DELETE FROM media_image WHERE id=?");
            var category=c.prepareStatement("DELETE FROM category WHERE id=?")){
            image.setString(1,imageId);image.executeUpdate();
            category.setLong(1,fixture);category.executeUpdate();
        }
    }
}
