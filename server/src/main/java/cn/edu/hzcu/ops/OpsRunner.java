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
    record RestorePreview(String backupId,String backupCreatedAt,String backupSnapshotAt,String backupReleaseId,
                          List<String> affectedComponents,int verifiedFileCount,long mediaFileCount,
                          boolean businessSnapshotAvailable) {}
    record RestorePlan(String manifestSha256,RestorePreview preview,Instant snapshotAt,
                       OpsBusinessSnapshot.Snapshot businessSnapshot) {}
    private record RestoreVerification(long mediaReferences, OpsBusinessSnapshot.Verification business) {}
    record BackupManifest(JsonNode data, String sha256, Instant createdAt, Instant snapshotAt) {}
    record BackupEntry(String id, String createdAt, String snapshotAt, boolean complete, String schemaFingerprint,
                       boolean withinRetentionWindow) {}
    private static final int RETAIN_RECENT_BACKUPS = 7;
    final OpsConfig config;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    OpsRunner(OpsConfig config){this.config=config;}
    static OpsApplication.Failure failure(String code){return new OpsApplication.Failure(409,code);}
    static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static String sha(Path file)throws IOException {var digest=sha256();try(var in=Files.newInputStream(file)){byte[] buffer=new byte[65536];int length;while((length=in.read(buffer))>0)digest.update(buffer,0,length);}return HexFormat.of().formatHex(digest.digest());}
    private static boolean validId(String id) { return id.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}"); }
    static Path child(Path root,String id)throws IOException {if(!validId(id))throw failure("INVALID_PARAMETER");Path path=root.resolve(id).normalize();OpsStore.rejectLinks(path);if(!path.getParent().equals(root))throw failure("INVALID_PARAMETER");return path;}
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
    private Connection database()throws IOException,SQLException {
        var env=config.environment();String port=env.getOrDefault("CAMPUSHUB_DB_PORT","3306");if(!port.matches("[0-9]{1,5}"))throw failure("OPS_DATABASE_CONFIG_INVALID");
        return DriverManager.getConnection("jdbc:mysql://127.0.0.1:"+port+"/"+config.databaseName()+"?connectTimeout=3000&socketTimeout=5000",
                env.get("CAMPUSHUB_DB_USERNAME"),env.get("CAMPUSHUB_DB_PASSWORD"));
    }
    private String schema()throws Exception {
        try(var connection=database();var statement=connection.createStatement();var rows=statement.executeQuery("SELECT version,checksum FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank")){
            StringBuilder fingerprint=new StringBuilder();while(rows.next())fingerprint.append(rows.getString(1)).append(':').append(rows.getString(2)).append('\n');if(fingerprint.isEmpty())throw failure("OPS_SCHEMA_MISSING");return OpsAuth.digest(fingerprint.toString());
        }
    }
    private long verifyMedia(Path mediaRoot)throws IOException,SQLException {
        try(var connection=database()) { return OpsMediaIntegrity.verify(connection,mediaRoot); }
    }
    private OpsBusinessSnapshot.Snapshot captureBusinessSnapshot() throws SQLException, IOException {
        try (var connection = database()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            return OpsBusinessSnapshot.capture(connection);
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
        String schema=schema();
        Instant snapshotAt = Instant.now();
        var businessSnapshot = captureBusinessSnapshot();
        sql(root.resolve("database.sql"),true);copyTree(config.mediaRoot(),root.resolve("media"));
        if(Files.isRegularFile(config.runtimeRoot().resolve("current.jar")))Files.copy(config.runtimeRoot().resolve("current.jar"),root.resolve("application.jar"));
        if(Files.isRegularFile(config.runtimeRoot().resolve("release.json")))Files.copy(config.runtimeRoot().resolve("release.json"),root.resolve("release.json"));
        var hashes=new TreeMap<String,String>();try(var files=Files.walk(root)){for(Path file:files.filter(Files::isRegularFile).toList())hashes.put(root.relativize(file).toString().replace('\\','/'),sha(file));}
        long checked=verifyMedia(root.resolve("media"));
        var manifest=Map.of("id",id,"database",config.databaseName(),"schemaFingerprint",schema,"createdAt",Instant.now().toString(),
                "snapshotAt",snapshotAt.toString(),"files",hashes,"complete",true,"verifiedMediaReferences",checked,
                "businessSnapshot",businessSnapshot);
        OpsStore.atomic(root.resolve("manifest.json"),OpsStore.JSON.writeValueAsBytes(manifest));return id;
    }
    RestorePlan preflightRestore(String id) throws Exception {
        return validateBackup(id,null);
    }

    private RestorePlan validateBackup(String id,String confirmedHash) throws Exception {
        Path root=child(config.backupRoot(),id);
        var manifest = readBackupManifest(root, id, confirmedHash);
        if (!manifest.data().path("database").asString().equals(config.databaseName())
                || !manifest.data().path("schemaFingerprint").asString().equals(schema())) {
            throw failure("OPS_BACKUP_INCOMPATIBLE");
        }
        return verifyBackupFiles(root, id, manifest);
    }

    static BackupManifest readBackupManifest(Path root, String id, String confirmedHash) throws IOException {
        OpsStore.rejectLinks(root);
        if (Files.notExists(root)) throw new OpsApplication.Failure(404,"OPS_BACKUP_NOT_FOUND");
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root)) {
            throw failure("OPS_BACKUP_INVALID");
        }
        Path manifest=root.resolve("manifest.json");
        if (!Files.isRegularFile(manifest,LinkOption.NOFOLLOW_LINKS)) throw failure("OPS_BACKUP_INVALID");
        byte[] bytes=Files.readAllBytes(manifest);
        String hash=HexFormat.of().formatHex(sha256().digest(bytes));
        if (confirmedHash!=null&&!confirmedHash.equals(hash)) throw failure("OPS_BACKUP_CHANGED");
        var n=backupJson(bytes);
        var files=n.path("files");
        if (!n.path("complete").isBoolean()||!n.path("complete").asBoolean()||!n.path("id").asString().equals(id)
                ||!n.path("schemaFingerprint").asString().matches("[0-9a-f]{64}")
                ||!files.isObject()||!files.has("database.sql")) {
            throw failure("OPS_BACKUP_INVALID");
        }
        try {
            Instant createdAt = Instant.parse(n.path("createdAt").asString());
            return new BackupManifest(n, hash, createdAt, readSnapshotAt(n, createdAt));
        }
        catch (DateTimeException invalid) { throw failure("OPS_BACKUP_INVALID"); }
    }

    private static Instant readSnapshotAt(JsonNode manifest, Instant createdAt) {
        var value = manifest.path("snapshotAt");
        if (!value.isString()) return null;
        try {
            Instant snapshotAt = Instant.parse(value.asString());
            return snapshotAt.isAfter(createdAt) ? null : snapshotAt;
        } catch (DateTimeException invalid) {
            return null;
        }
    }

    static RestorePlan verifyBackupFiles(Path root, String id, BackupManifest backup) throws IOException {
        var businessSnapshot = OpsBusinessSnapshot.read(backup.data().path("businessSnapshot"));
        if (!Files.isDirectory(root.resolve("media"), LinkOption.NOFOLLOW_LINKS)) throw failure("OPS_BACKUP_INVALID");
        Path manifest = root.resolve("manifest.json");
        var files = backup.data().path("files");
        String fingerprint = backup.data().path("schemaFingerprint").asString();
        var actual=new TreeSet<String>();
        try(var walk=Files.walk(root)){
            for(Path file:walk.toList()){
                OpsStore.rejectLinks(file);
                if (!file.toRealPath().equals(file) || !(Files.isRegularFile(file) || Files.isDirectory(file))) {
                    throw failure("OPS_BACKUP_INVALID");
                }
                if(Files.isRegularFile(file)&&!file.equals(manifest))actual.add(root.relativize(file).toString().replace('\\','/'));
            }
        }
        var expected=new TreeSet<String>();files.properties().forEach(entry->expected.add(entry.getKey()));
        if (!actual.equals(expected)) throw failure("OPS_BACKUP_INVALID");
        for (String name:expected) {
            Path path=root.resolve(name).normalize();
            if (!path.startsWith(root)||!sha(path).equals(files.path(name).asString())) throw failure("OPS_BACKUP_INVALID");
        }
        if (!sha(manifest).equals(backup.sha256())) throw failure("OPS_BACKUP_CHANGED");
        String releaseId=null;
        if (expected.contains("application.jar")!=expected.contains("release.json")) throw failure("OPS_BACKUP_INVALID");
        if (expected.contains("release.json")) {
            var release=backupJson(Files.readAllBytes(root.resolve("release.json")));
            releaseId=release.path("id").asString();
            if (!releaseId.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")
                    ||!release.path("sha256").asString().equals(files.path("application.jar").asString())
                    ||!release.path("schemaFingerprint").asString().equals(fingerprint)) throw failure("OPS_BACKUP_INVALID");
        }
        var components=releaseId==null?List.of("DATABASE","MEDIA"):List.of("DATABASE","MEDIA","RELEASE");
        var preview = new RestorePreview(id, backup.createdAt().toString(), Objects.toString(backup.snapshotAt(), null),
                releaseId, components, expected.size(), expected.stream().filter(name->name.startsWith("media/")).count(),
                businessSnapshot != null);
        return new RestorePlan(backup.sha256(), preview, backup.snapshotAt(), businessSnapshot);
    }

    private static JsonNode backupJson(byte[] data) {
        try {
            var parsed=OpsStore.JSON.readTree(data);
            if (parsed!=null&&parsed.isObject()) return parsed;
        } catch (RuntimeException invalid) {
            throw failure("OPS_BACKUP_INVALID");
        }
        throw failure("OPS_BACKUP_INVALID");
    }

    private RestoreVerification restore(String id,String manifestHash,Consumer<String> phase)throws Exception {
        var plan = validateBackup(id,manifestHash);
        Path backup=child(config.backupRoot(),id),staged=config.runtimeRoot().resolve("media-"+UUID.randomUUID());copyTree(backup.resolve("media"),staged);
        sql(backup.resolve("database.sql"),false);
        var expected = plan.businessSnapshot();
        if (expected != null) phase.accept("VERIFYING_DATA");
        var business = OpsBusinessSnapshot.verify(expected, expected == null ? null : captureBusinessSnapshot());
        phase.accept("VERIFYING_MEDIA");
        long checked=verifyMedia(staged);
        Path held=config.mediaRoot().resolveSibling(config.mediaRoot().getFileName()+"-held-"+UUID.randomUUID());Files.move(config.mediaRoot(),held,StandardCopyOption.ATOMIC_MOVE);
        Files.move(staged,config.mediaRoot(),StandardCopyOption.ATOMIC_MOVE);
        if(Files.exists(backup.resolve("application.jar")))install(backup.resolve("application.jar"),config.runtimeRoot().resolve("current.jar"));
        if(Files.exists(backup.resolve("release.json")))install(backup.resolve("release.json"),config.runtimeRoot().resolve("release.json"));
        return new RestoreVerification(checked, business);
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
            for(String resource:List.of("V1__article_reading_schema.sql","V3__account_email_auth.sql","V4__author_profiles_drafts_and_media.sql","V5__author_contact_and_business_audit.sql","V6__authenticated_comments_and_images.sql","V7__likes_favorites_and_live_counts.sql","V8__coin_wallet_ledger_and_reading.sql","V9__community_cases_and_admin_identity.sql","V10__community_governance.sql","V11__user_restrictions_and_appeals.sql","V12__contribution_invalidation.sql","V13__personal_notifications.sql","V14__campus_email_reverification.sql","V15__campus_certification_revocation.sql","V16__campus_certification_appeals.sql","V17__category_canonical_mapping.sql","V18__case_linked_moderation.sql"))if(!names.contains("db/migration/"+resource))throw failure("OPS_ARTIFACT_SCHEMA_CHANGE");
        }
        return n;
    }
    private JsonNode release(){try{return OpsStore.JSON.readTree(Files.readString(config.runtimeRoot().resolve("release.json")));}catch(Exception e){return OpsStore.JSON.createObjectNode();}}
    Map<String,Object> status()throws IOException {return Map.of("target","business","healthy",healthy(),"managedProcessRunning",managed().isPresent(),"release",release());}
    boolean scheduledBackupReady() {
        try {
            return managed().isPresent() && healthy();
        } catch (IOException | OpsApplication.Failure unavailable) {
            return false;
        }
    }
    List<BackupEntry> backups() throws IOException {
        var manifests = new ArrayList<BackupManifest>();
        try (var paths = Files.list(config.backupRoot())) {
            for (Path path : paths.toList()) {
                String id = path.getFileName().toString();
                if (!validId(id)) continue;
                try {
                    var manifest = readBackupManifest(path, id, null);
                    if (manifest.data().path("database").asString().equals(config.databaseName())) manifests.add(manifest);
                } catch (IOException | OpsApplication.Failure invalid) {
                    // Incomplete snapshots and unrelated entries are excluded from the retention window.
                }
            }
        }
        manifests.sort(Comparator.comparing(BackupManifest::createdAt).reversed()
                .thenComparing(manifest -> manifest.data().path("id").asString()));
        var entries = new ArrayList<BackupEntry>();
        for (int i = 0; i < Math.min(100, manifests.size()); i++) {
            var manifest = manifests.get(i);
            entries.add(new BackupEntry(manifest.data().path("id").asString(), manifest.createdAt().toString(),
                    Objects.toString(manifest.snapshotAt(), null), true,
                    manifest.data().path("schemaFingerprint").asString(), i < RETAIN_RECENT_BACKUPS));
        }
        return entries;
    }
    List<Object> artifacts(){var out=new ArrayList<>();try(var dirs=Files.list(config.artifactRoot())){for(Path path:dirs.sorted().limit(100).toList())try{var n=OpsStore.JSON.readTree(Files.readString(path.resolve("manifest.json")));out.add(Map.of("id",path.getFileName().toString(),"sha256",n.path("sha256").asString(),"schemaFingerprint",n.path("schemaFingerprint").asString()));}catch(Exception ignored){}}catch(IOException ignored){}return out;}
    void register(String id,Path source)throws Exception {
        Path root=child(config.artifactRoot(),id);if(Files.exists(root))throw failure("OPS_ARTIFACT_EXISTS");Files.createDirectory(root);
        OpsStore.rejectLinks(source);Path jar=root.resolve("application.jar");Files.copy(source,jar);
        OpsStore.atomic(root.resolve("manifest.json"),OpsStore.JSON.writeValueAsBytes(Map.of("id",id,"sha256",sha(jar),"schemaFingerprint",schema(),"registeredAt",Instant.now().toString())));
        artifact(id);
    }
    public Map<String,Object> execute(String action,String reference,String backupManifestSha256,OpsJobs.Progress phase)throws Exception {
        OpsJobs.validate(action,"business",reference);phase.accept("PREFLIGHT");
        if(action.equals("DEPLOY"))artifact(reference);
        RestorePlan restorePlan = null;
        if(action.equals("RESTORE")){
            if(backupManifestSha256==null)throw failure("OPS_CONFIRMATION_INVALID");
            restorePlan = validateBackup(reference,backupManifestSha256);
        }
        boolean wasRunning=managed().isPresent();phase.accept("STOPPING");
        Instant restoreStartedAt = restorePlan == null ? null : Instant.now();
        long restoreStartedNanos = restorePlan == null ? 0 : System.nanoTime();
        stop();String protection=null;
        try{
            if(action.equals("RESTART")){phase.accept("STARTING");start();return Map.of("healthy",true);}
            phase.accept("PROTECTING");protection=backup();
            if(action.equals("BACKUP")){if(wasRunning){phase.accept("STARTING");start();}return Map.of("backupId",protection,"healthy",healthy());}
            phase.protectionReady(protection);
            var result=new LinkedHashMap<String,Object>();
            if(action.equals("DEPLOY")){
                phase.accept("INSTALLING");Path root=child(config.artifactRoot(),reference);artifact(reference);install(root.resolve("application.jar"),config.runtimeRoot().resolve("current.jar"));install(root.resolve("manifest.json"),config.runtimeRoot().resolve("release.json"));
            } else {
                phase.accept("RESTORING");
                var verification = restore(reference, backupManifestSha256, phase);
                result.put("verifiedMediaReferences", verification.mediaReferences());
                result.put("businessVerification", verification.business());
            }
            phase.accept("STARTING");start();
            if (restorePlan != null) {
                Duration elapsed = Duration.ofNanos(System.nanoTime() - restoreStartedNanos);
                result.put("recoveryEvidence", OpsRecoveryEvidence.measure(restorePlan.snapshotAt(), restoreStartedAt,
                        Instant.now(), elapsed));
            }
            phase.accept("VERIFIED");
            result.put("healthy",true);result.put("protectionBackupId",protection);result.put("referenceId",reference);
            return result;
        }catch(Exception failed){
            phase.accept("ROLLING_BACK");try{stop();if(protection!=null){restore(protection,null,phase);if(Files.exists(config.runtimeRoot().resolve("current.jar")))start();}else if(wasRunning)start();phase.accept("ROLLBACK_VERIFIED");}
            catch(Exception rollback){phase.accept("MANUAL_RECOVERY_REQUIRED");throw failure("OPS_MANUAL_RECOVERY_REQUIRED");}
            throw failed;
        }
    }
}
