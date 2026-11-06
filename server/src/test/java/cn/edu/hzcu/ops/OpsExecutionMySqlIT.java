package cn.edu.hzcu.ops;

import cn.edu.hzcu.campushub.auth.AuthTestSupport;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP -> independent executor -> real JVM + mysqldump/mysql, exclusively campushub_test. */
class OpsExecutionMySqlIT {
    @TempDir Path root;
    static final HttpClient HTTP=HttpClient.newHttpClient();
    String base,token;OpsConfig config;long fixture;
    String username=System.getenv("CAMPUSHUB_TEST_DB_USERNAME"),password=System.getenv("CAMPUSHUB_TEST_DB_PASSWORD");
    Connection connection()throws Exception {return DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/campushub_test?connectTimeout=3000&socketTimeout=5000",username,password);}
    JsonNode request(String method,String path,Object body)throws Exception {
        var q=HttpRequest.newBuilder(URI.create(base+path)).timeout(java.time.Duration.ofSeconds(10));if(token!=null)q.header("Authorization","Bearer "+token);if(body!=null)q.header("Content-Type","application/json");
        var r=HTTP.send(q.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(OpsStore.JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).as(path+" "+r.body()).isEqualTo(200);return OpsStore.JSON.readTree(r.body()).path("data");
    }
    JsonNode job(String action,String reference)throws Exception {
        var semantic=Map.of("action",action,"target","business","referenceId",reference);var confirmation=request("POST","/confirmations",semantic);var input=new LinkedHashMap<String,Object>(semantic);input.put("requestId",UUID.randomUUID().toString());input.put("confirmationId",confirmation.path("confirmationId").asString());String id=request("POST","/jobs",input).path("id").asString();
        for(int i=0;i<600;i++){var job=request("GET","/jobs/"+id,null);if(Set.of("SUCCEEDED","FAILED","INTERRUPTED").contains(job.path("state").asString()))return job;Thread.sleep(200);}throw new AssertionError("Task did not finish");
    }
    @Test void realDeployRestartBackupRestoreAndDamageRejectionThroughHttp()throws Exception {
        try(var app=AuthTestSupport.application(new AuthTestSupport.ControlledMailer(),new AuthTestSupport.MutableClock()).run("--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=demo")){}
        fixture=2_000_000_000_000L+new java.security.SecureRandom().nextLong(1_000_000_000L);
        try(var c=connection();var q=c.prepareStatement("INSERT INTO category(id,kind,name) VALUES(?,'TOPIC',?)")){q.setLong(1,fixture);q.setString(2,"ops-before-"+fixture);q.executeUpdate();}
        int businessPort,opsPort;try(var socket=new java.net.ServerSocket(0)){businessPort=socket.getLocalPort();}try(var socket=new java.net.ServerSocket(0)){opsPort=socket.getLocalPort();}
        Path env=root.resolve("business-env.json");OpsStore.atomic(env,OpsStore.JSON.writeValueAsBytes(Map.of("CAMPUSHUB_DB_HOST","127.0.0.1","CAMPUSHUB_DB_PORT","3306","CAMPUSHUB_DB_NAME","campushub_test","CAMPUSHUB_DB_USERNAME",username,"CAMPUSHUB_DB_PASSWORD",password,"CAMPUSHUB_AUTH_PEPPER",AuthTestSupport.TEST_KEY,"CAMPUSHUB_LOG_ENABLED","true","CAMPUSHUB_RUN_MODE","release")));
        config=new OpsConfig(root.resolve("control"),root.resolve("runtime"),root.resolve("artifacts"),root.resolve("backups"),root.resolve("media"),Path.of(System.getProperty("java.home"),"bin","java.exe"),Path.of(Objects.requireNonNull(System.getenv("CAMPUSHUB_OPS_TEST_MYSQL"))),Path.of(Objects.requireNonNull(System.getenv("CAMPUSHUB_OPS_TEST_DUMP"))),env,opsPort,"http://127.0.0.1:"+businessPort+"/actuator/health","http://127.0.0.1:5173","campushub_test");config.validate();
        Path media=config.mediaRoot().resolve("objects");Files.createDirectories(media);Path image=media.resolve(UUID.randomUUID().toString());byte[] original="controlled-image-before".getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(image,original);
        Path artifact=config.artifactRoot().resolve("release-one");Files.createDirectories(artifact);Path jar=artifact.resolve("application.jar");Files.copy(Path.of("target/campushub-server-0.1.0.jar"),jar);
        StringBuilder schema=new StringBuilder();try(var c=connection();var q=c.createStatement();var rows=q.executeQuery("SELECT version,checksum FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank")){while(rows.next())schema.append(rows.getString(1)).append(':').append(rows.getString(2)).append('\n');}
        OpsStore.atomic(artifact.resolve("manifest.json"),OpsStore.JSON.writeValueAsBytes(Map.of("id","release-one","sha256",OpsRunner.sha(jar),"schemaFingerprint",OpsAuth.digest(schema.toString()))));
        OpsStore.atomic(config.controlRoot().resolve("credentials.json"),OpsStore.JSON.writeValueAsBytes(Map.of("login","testops","passwordHash",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("test-password"))));
        try(var app=new OpsApplication(config)){
            app.start();base="http://127.0.0.1:"+opsPort+"/api/v1/ops";token=request("POST","/auth/login",Map.of("login","testops","password","test-password")).path("accessToken").asString();
            assertThat(request("GET","/status",null).path("healthy").asBoolean()).isFalse();
            var deployed=job("DEPLOY","release-one");assertThat(deployed.path("state").asString()).as(deployed.toString()).isEqualTo("SUCCEEDED");assertThat(request("GET","/status",null).path("release").path("id").asString()).isEqualTo("release-one");
            long pid=OpsStore.JSON.readTree(Files.readString(config.runtimeRoot().resolve("pid.json"))).path("pid").asLong();var restarted=job("RESTART","");assertThat(restarted.path("state").asString()).as(restarted.toString()).isEqualTo("SUCCEEDED");assertThat(OpsStore.JSON.readTree(Files.readString(config.runtimeRoot().resolve("pid.json"))).path("pid").asLong()).isNotEqualTo(pid);
            var backed=job("BACKUP","");assertThat(backed.path("state").asString()).as(backed.toString()).isEqualTo("SUCCEEDED");String backup=backed.path("result").path("backupId").asString();
            try(var c=connection();var q=c.prepareStatement("UPDATE category SET name=? WHERE id=?")){q.setString(1,"ops-after-"+fixture);q.setLong(2,fixture);q.executeUpdate();}Files.writeString(image,"changed-after-backup");
            var restored=job("RESTORE",backup);assertThat(restored.path("state").asString()).as(restored.toString()).isEqualTo("SUCCEEDED");assertThat(Files.readAllBytes(image)).isEqualTo(original);
            try(var c=connection();var q=c.prepareStatement("SELECT name FROM category WHERE id=?")){q.setLong(1,fixture);try(var rows=q.executeQuery()){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("ops-before-"+fixture);}}
            Files.writeString(config.backupRoot().resolve(backup).resolve("database.sql"),"broken");var rejected=job("RESTORE",backup);assertThat(rejected.path("state").asString()).isEqualTo("FAILED");assertThat(rejected.path("errorCode").asString()).isEqualTo("OPS_BACKUP_INVALID");assertThat(request("GET","/status",null).path("healthy").asBoolean()).isTrue();
            assertThat(request("GET","/logs",null).has("taskEvents")).isTrue();assertThat(request("GET","/jobs",null).path("items").size()).isEqualTo(5);
            // Stop only our exact PID + start-time-owned child, not any user's business service.
            var runner=new OpsRunner(config);var state=OpsStore.JSON.readTree(Files.readString(config.runtimeRoot().resolve("pid.json")));ProcessHandle.of(state.path("pid").asLong()).ifPresent(p->{p.destroy();try{p.onExit().get(30,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}});
            assertThat(request("GET","/status",null).path("healthy").asBoolean()).isFalse();assertThat(request("GET","/jobs",null).path("items").size()).isEqualTo(5);
        }finally{
            Path pid=config.runtimeRoot().resolve("pid.json");if(Files.isRegularFile(pid)){var state=OpsStore.JSON.readTree(Files.readString(pid));ProcessHandle.of(state.path("pid").asLong()).filter(p->p.info().startInstant().map(java.time.Instant::toString).orElse("").equals(state.path("started").asString())).ifPresent(ProcessHandle::destroy);}
            try(var c=connection();var q=c.prepareStatement("DELETE FROM category WHERE id=?")){q.setLong(1,fixture);q.executeUpdate();}
        }
        // Tasks and results survive an independent control-plane restart; sessions deliberately do not.
        try(var app=new OpsApplication(config)){app.start();token=null;token=request("POST","/auth/login",Map.of("login","testops","password","test-password")).path("accessToken").asString();assertThat(request("GET","/jobs",null).path("items").size()).isEqualTo(5);}
    }
}
