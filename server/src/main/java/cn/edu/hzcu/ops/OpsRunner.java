package cn.edu.hzcu.ops;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import tools.jackson.databind.JsonNode;

/** No shell, arbitrary command, user path, or user SQL is accepted. */
final class OpsRunner implements OpsJobs.Executor {
    final OpsConfig config;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    OpsRunner(OpsConfig config){this.config=config;}
    static OpsApplication.Failure failure(String code){return new OpsApplication.Failure(409,code);}
    static String sha(Path file)throws IOException {try{var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(file)){byte[] buffer=new byte[65536];int length;while((length=in.read(buffer))>0)digest.update(buffer,0,length);}return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static Path child(Path root,String id)throws IOException {if(!id.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}"))throw failure("INVALID_PARAMETER");Path path=root.resolve(id).normalize();OpsStore.rejectLinks(path);if(!path.getParent().equals(root))throw failure("INVALID_PARAMETER");return path;}
    boolean healthy(){try{var response=http.send(HttpRequest.newBuilder(URI.create(config.healthUrl())).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.ofString());var n=OpsStore.JSON.readTree(response.body());return response.statusCode()==200&&(n.path("status").asString().equals("UP")||n.path("data").path("status").asString().equals("UP"));}catch(Exception e){return false;}}
    private Optional<ProcessHandle> managed()throws IOException {
        Path pid=config.runtimeRoot().resolve("pid.json");if(!Files.exists(pid))return Optional.empty();
        var n=OpsStore.JSON.readTree(Files.readString(pid));var process=ProcessHandle.of(n.path("pid").asLong());
        if(process.isPresent()&&process.get().isAlive()){
            if(!process.get().info().startInstant().map(Instant::toString).orElse("").equals(n.path("started").asString()))throw failure("OPS_PROCESS_ID_REUSED");return process;
        }return Optional.empty();
    }
    private void stop()throws Exception {
        var process=managed();if(process.isEmpty()){if(healthy())throw failure("OPS_UNMANAGED_PROCESS");return;}
        process.get().destroy();try{process.get().onExit().get(30,TimeUnit.SECONDS);}catch(java.util.concurrent.TimeoutException e){throw failure("OPS_STOP_FAILED");}
        Files.deleteIfExists(config.runtimeRoot().resolve("pid.json"));if(healthy())throw failure("OPS_PORT_STILL_ACTIVE");
    }
    private void start()throws Exception {
        Path jar=config.runtimeRoot().resolve("current.jar");if(!Files.isRegularFile(jar))throw failure("OPS_NO_RELEASE");
        var env=config.environment();var builder=new ProcessBuilder(config.javaExecutable().toString(),"-Xmx512m","-jar",jar.toString());builder.directory(config.runtimeRoot().toFile());builder.environment().putAll(env);
        // Business logger writes its safe logs; untrusted child stdout is never exposed by the ops API.
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);Process process=builder.start();
        String started=process.info().startInstant().orElseThrow().toString();OpsStore.atomic(config.runtimeRoot().resolve("pid.json"),OpsStore.JSON.writeValueAsBytes(Map.of("pid",process.pid(),"started",started)));
        for(int i=0;i<120&&process.isAlive();i++){if(healthy())return;Thread.sleep(500);}if(process.isAlive()){process.destroy();process.waitFor(30,TimeUnit.SECONDS);}throw failure("OPS_HEALTH_CHECK_FAILED");
    }
    private String schema()throws Exception {
        var env=config.environment();String port=env.getOrDefault("CAMPUSHUB_DB_PORT","3306");if(!port.matches("[0-9]{1,5}"))throw failure("OPS_DATABASE_CONFIG_INVALID");
        try(var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:"+port+"/"+config.databaseName()+"?connectTimeout=3000&socketTimeout=5000",env.get("CAMPUSHUB_DB_USERNAME"),env.get("CAMPUSHUB_DB_PASSWORD"));var statement=connection.createStatement();var rows=statement.executeQuery("SELECT version,checksum FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank")){
            StringBuilder fingerprint=new StringBuilder();while(rows.next())fingerprint.append(rows.getString(1)).append(':').append(rows.getString(2)).append('\n');if(fingerprint.isEmpty())throw failure("OPS_SCHEMA_MISSING");return OpsAuth.digest(fingerprint.toString());
        }
    }
    private void sql(Path file,boolean dump)throws Exception {
        var env=config.environment();String port=env.getOrDefault("CAMPUSHUB_DB_PORT","3306");if(!port.matches("[0-9]{1,5}"))throw failure("OPS_DATABASE_CONFIG_INVALID");
        var args=new ArrayList<>(List.of((dump?config.dumpExecutable():config.mysqlExecutable()).toString(),"--host=127.0.0.1","--protocol=TCP","--port="+port,"--user="+env.get("CAMPUSHUB_DB_USERNAME"),"--default-character-set=utf8mb4"));
        if(dump)args.addAll(List.of("--single-transaction","--no-tablespaces","--skip-comments","--skip-dump-date","--hex-blob"));else args.add("--binary-mode");args.add(config.databaseName());
        var builder=new ProcessBuilder(args);builder.environment().put("MYSQL_PWD",env.get("CAMPUSHUB_DB_PASSWORD"));builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        if(dump)builder.redirectOutput(file.toFile());else builder.redirectInput(file.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process=builder.start();try{if(!process.waitFor(120,TimeUnit.SECONDS))throw failure("OPS_DATABASE_TIMEOUT");if(process.exitValue()!=0)throw failure("OPS_DATABASE_COMMAND_FAILED");}
        finally{if(process.isAlive()){process.destroyForcibly();process.waitFor(10,TimeUnit.SECONDS);}}
    }
    private static void copyTree(Path source,Path dest)throws IOException {
        if(!Files.exists(source))throw new IOException("SOURCE_MISSING");
        try(var walk=Files.walk(source)){for(Path path:walk.toList()){OpsStore.rejectLinks(path);Path target=dest.resolve(source.relativize(path));if(Files.isDirectory(path))Files.createDirectories(target);else if(Files.isRegularFile(path))Files.copy(path,target,StandardCopyOption.REPLACE_EXISTING);else throw new IOException("UNSUPPORTED_FILE");}}
    }
    private String backup()throws Exception {
        String id=UUID.randomUUID().toString();Path root=child(config.backupRoot(),id);Files.createDirectory(root);
        String schema=schema();sql(root.resolve("database.sql"),true);copyTree(config.mediaRoot(),root.resolve("media"));
        if(Files.isRegularFile(config.runtimeRoot().resolve("current.jar")))Files.copy(config.runtimeRoot().resolve("current.jar"),root.resolve("application.jar"));
        if(Files.isRegularFile(config.runtimeRoot().resolve("release.json")))Files.copy(config.runtimeRoot().resolve("release.json"),root.resolve("release.json"));
        var hashes=new TreeMap<String,String>();try(var files=Files.walk(root)){for(Path file:files.filter(Files::isRegularFile).toList())hashes.put(root.relativize(file).toString().replace('\\','/'),sha(file));}
        var manifest=Map.of("id",id,"database",config.databaseName(),"schemaFingerprint",schema,"createdAt",Instant.now().toString(),"files",hashes,"complete",true);
        OpsStore.atomic(root.resolve("manifest.json"),OpsStore.JSON.writeValueAsBytes(manifest));return id;
    }
    JsonNode validateBackup(String id)throws Exception {
        Path root=child(config.backupRoot(),id);var n=OpsStore.JSON.readTree(Files.readString(root.resolve("manifest.json")));
        if(!n.path("complete").asBoolean()||!n.path("id").asString().equals(id)||!n.path("database").asString().equals(config.databaseName())||!n.path("schemaFingerprint").asString().equals(schema())||!n.path("files").has("database.sql"))throw failure("OPS_BACKUP_INCOMPATIBLE");
        Set<String> actual=new TreeSet<>();try(var files=Files.walk(root)){for(Path file:files.toList()){OpsStore.rejectLinks(file);if(Files.isRegularFile(file)&&!file.equals(root.resolve("manifest.json")))actual.add(root.relativize(file).toString().replace('\\','/'));}}
        var expected=new TreeSet<String>();n.path("files").properties().forEach(entry->{expected.add(entry.getKey());});if(!actual.equals(expected))throw failure("OPS_BACKUP_INVALID");
        for(String name:expected){Path path=root.resolve(name).normalize();if(!path.startsWith(root)||!sha(path).equals(n.path("files").path(name).asString()))throw failure("OPS_BACKUP_INVALID");}
        if(!Files.isDirectory(root.resolve("media")))throw failure("OPS_BACKUP_INVALID");return n;
    }
    private void restore(String id)throws Exception {
        validateBackup(id);Path backup=child(config.backupRoot(),id),staged=config.runtimeRoot().resolve("media-"+UUID.randomUUID());copyTree(backup.resolve("media"),staged);
        sql(backup.resolve("database.sql"),false);
        Path held=config.mediaRoot().resolveSibling(config.mediaRoot().getFileName()+"-held-"+UUID.randomUUID());Files.move(config.mediaRoot(),held,StandardCopyOption.ATOMIC_MOVE);
        Files.move(staged,config.mediaRoot(),StandardCopyOption.ATOMIC_MOVE);
        if(Files.exists(backup.resolve("application.jar")))install(backup.resolve("application.jar"),config.runtimeRoot().resolve("current.jar"));
        if(Files.exists(backup.resolve("release.json")))install(backup.resolve("release.json"),config.runtimeRoot().resolve("release.json"));
    }
    private static void install(Path source,Path target)throws IOException {Path tmp=target.resolveSibling(target.getFileName()+".install");Files.copy(source,tmp,StandardCopyOption.REPLACE_EXISTING);Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
    JsonNode artifact(String id)throws Exception {
        Path root=child(config.artifactRoot(),id),jar=root.resolve("application.jar");OpsStore.rejectLinks(jar);
        var n=OpsStore.JSON.readTree(Files.readString(root.resolve("manifest.json")));
        if(!id.equals(n.path("id").asString())||!sha(jar).equals(n.path("sha256").asString())||!schema().equals(n.path("schemaFingerprint").asString()))throw failure("OPS_ARTIFACT_INVALID");
        // A manifest cannot authorize unreviewed schema changes. Only exactly the running executor's migration resources are accepted.
        try(var zip=new ZipFile(jar.toFile())){
            var entries=zip.stream().filter(e->!e.isDirectory()&&e.getName().startsWith("BOOT-INF/classes/db/migration/")).toList();
            if(entries.isEmpty())throw failure("OPS_ARTIFACT_INVALID");
            var names=new TreeSet<String>();for(var entry:entries){String resource=entry.getName().substring("BOOT-INF/classes/".length());names.add(resource);try(var expected=OpsRunner.class.getClassLoader().getResourceAsStream(resource);var actual=zip.getInputStream(entry)){if(expected==null||!MessageDigest.isEqual(expected.readAllBytes(),actual.readAllBytes()))throw failure("OPS_ARTIFACT_SCHEMA_CHANGE");}}
            for(String resource:List.of("V1__article_reading_schema.sql","V3__account_email_auth.sql","V4__author_profiles_drafts_and_media.sql","V5__author_contact_and_business_audit.sql","V6__authenticated_comments_and_images.sql","V7__likes_favorites_and_live_counts.sql","V8__coin_wallet_ledger_and_reading.sql","V9__community_cases_and_admin_identity.sql","V10__community_governance.sql","V11__user_restrictions_and_appeals.sql","V12__contribution_invalidation.sql"))if(!names.contains("db/migration/"+resource))throw failure("OPS_ARTIFACT_SCHEMA_CHANGE");
        }
        return n;
    }
    private JsonNode release(){try{return OpsStore.JSON.readTree(Files.readString(config.runtimeRoot().resolve("release.json")));}catch(Exception e){return OpsStore.JSON.createObjectNode();}}
    Map<String,Object> status()throws IOException {return Map.of("target","business","healthy",healthy(),"managedProcessRunning",managed().isPresent(),"release",release());}
    List<Object> backups(){var out=new ArrayList<>();try(var dirs=Files.list(config.backupRoot())){for(Path path:dirs.sorted(Comparator.reverseOrder()).limit(100).toList())try{var n=OpsStore.JSON.readTree(Files.readString(path.resolve("manifest.json")));out.add(Map.of("id",path.getFileName().toString(),"createdAt",n.path("createdAt").asString(),"complete",n.path("complete").asBoolean(),"schemaFingerprint",n.path("schemaFingerprint").asString()));}catch(Exception ignored){}}catch(IOException ignored){}return out;}
    List<Object> artifacts(){var out=new ArrayList<>();try(var dirs=Files.list(config.artifactRoot())){for(Path path:dirs.sorted().limit(100).toList())try{var n=OpsStore.JSON.readTree(Files.readString(path.resolve("manifest.json")));out.add(Map.of("id",path.getFileName().toString(),"sha256",n.path("sha256").asString(),"schemaFingerprint",n.path("schemaFingerprint").asString()));}catch(Exception ignored){}}catch(IOException ignored){}return out;}
    void register(String id,Path source)throws Exception {
        Path root=child(config.artifactRoot(),id);if(Files.exists(root))throw failure("OPS_ARTIFACT_EXISTS");Files.createDirectory(root);
        OpsStore.rejectLinks(source);Path jar=root.resolve("application.jar");Files.copy(source,jar);
        OpsStore.atomic(root.resolve("manifest.json"),OpsStore.JSON.writeValueAsBytes(Map.of("id",id,"sha256",sha(jar),"schemaFingerprint",schema(),"registeredAt",Instant.now().toString())));
        artifact(id);
    }
    public Map<String,Object> execute(String action,String reference,Consumer<String> phase)throws Exception {
        OpsJobs.validate(action,"business",reference);phase.accept("PREFLIGHT");
        if(action.equals("DEPLOY"))artifact(reference);if(action.equals("RESTORE"))validateBackup(reference);
        boolean wasRunning=managed().isPresent();phase.accept("STOPPING");stop();String protection=null;
        try{
            if(action.equals("RESTART")){phase.accept("STARTING");start();return Map.of("healthy",true);}
            phase.accept("PROTECTING");protection=backup();
            if(action.equals("BACKUP")){if(wasRunning){phase.accept("STARTING");start();}return Map.of("backupId",protection,"healthy",healthy());}
            if(action.equals("DEPLOY")){
                phase.accept("INSTALLING");Path root=child(config.artifactRoot(),reference);artifact(reference);install(root.resolve("application.jar"),config.runtimeRoot().resolve("current.jar"));install(root.resolve("manifest.json"),config.runtimeRoot().resolve("release.json"));
            }else{phase.accept("RESTORING");restore(reference);}
            phase.accept("STARTING");start();phase.accept("VERIFIED");return Map.of("healthy",true,"protectionBackupId",protection,"referenceId",reference);
        }catch(Exception failed){
            phase.accept("ROLLING_BACK");try{stop();if(protection!=null){restore(protection);if(Files.exists(config.runtimeRoot().resolve("current.jar")))start();}else if(wasRunning)start();phase.accept("ROLLBACK_VERIFIED");}
            catch(Exception rollback){phase.accept("MANUAL_RECOVERY_REQUIRED");throw failure("OPS_MANUAL_RECOVERY_REQUIRED");}
            throw failed;
        }
    }
}
