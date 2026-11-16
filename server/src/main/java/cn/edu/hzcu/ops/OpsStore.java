package cn.edu.hzcu.ops;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Fsynced control-plane journal independent of business MySQL. */
public final class OpsStore implements AutoCloseable {
    record RecoveryIncident(String jobId, String action, String state, String errorCode, String lastPhase,
                            String protectionBackupId) {}
    static final JsonMapper JSON=JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    final Path root;
    private final FileChannel channel;
    private final FileLock lock;
    private ObjectNode state;
    public OpsStore(Path root)throws IOException {
        this.root=root.toAbsolutePath().normalize();Files.createDirectories(this.root);
        rejectLinks(this.root);channel=FileChannel.open(this.root.resolve("control.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        lock=channel.tryLock();if(lock==null)throw new IOException("OPS_ALREADY_RUNNING");
        Path file=this.root.resolve("state.json");state=Files.exists(file)?(ObjectNode)JSON.readTree(Files.readString(file)):JSON.createObjectNode();
        if(!state.has("jobs"))state.set("jobs",JSON.createArrayNode());
        for(var job:state.path("jobs"))if(Set.of("QUEUED","RUNNING").contains(job.path("state").asString())){
            ((ObjectNode)job).put("state","INTERRUPTED").put("errorCode","EXECUTOR_INTERRUPTED")
                    .put("finishedAt",Instant.now().toString());
            state.put("recoveryRequired",true).put("recoveryJobId",job.path("id").asString());
        }
        save(state);
    }
    static void rejectLinks(Path path)throws IOException {for(Path p=path.toAbsolutePath().normalize();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("SYMLINK_NOT_ALLOWED");}
    static void atomic(Path path,byte[] bytes)throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());rejectLinks(path);
        Path tmp=path.resolveSibling(path.getFileName()+".tmp");rejectLinks(tmp);
        try(var out=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
            var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true);
        }
        Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    synchronized ObjectNode read(){return state.deepCopy();}
    synchronized void save(ObjectNode next)throws IOException {atomic(root.resolve("state.json"),JSON.writeValueAsBytes(next));state=next.deepCopy();}
    synchronized void change(java.util.function.Consumer<ObjectNode> action)throws IOException {var next=read();action.accept(next);save(next);}
    static RecoveryIncident recoveryIncident(JsonNode journal, String unsafeJobId) {
        var job = recoveryJob(journal, unsafeJobId);
        if (job == null) return null;
        var events = job.path("events");
        String lastPhase = events.isEmpty() ? null : events.path(events.size() - 1).path("phase").asString(null);
        return new RecoveryIncident(job.path("id").asString(), job.path("action").asString(null),
                job.path("state").asString(), job.path("errorCode").asString(null), lastPhase,
                job.path("protectionBackupId").asString(null));
    }
    private static ObjectNode recoveryJob(JsonNode journal, String unsafeJobId) {
        String jobId = unsafeJobId != null ? unsafeJobId : journal.path("recoveryJobId").asString(null);
        var jobs = journal.path("jobs");
        for (int i = jobs.size() - 1; i >= 0; i--) {
            var job = jobs.get(i);
            if (jobId != null ? jobId.equals(job.path("id").asString())
                    : !job.has("reconciledAt") && (job.path("state").asString().equals("INTERRUPTED")
                    || job.path("errorCode").asString().equals("OPS_MANUAL_RECOVERY_REQUIRED"))) {
                return (ObjectNode) job;
            }
        }
        return null;
    }
    void reconcile() throws IOException {
        change(journal -> {
            String timestamp = Instant.now().toString();
            if (journal.path("recoveryRequired").asBoolean()) {
                var job = recoveryJob(journal, null);
                if (job != null) job.put("reconciledAt", timestamp);
            }
            journal.put("recoveryRequired", false).put("manualReconciliationAt", timestamp);
            journal.remove("recoveryJobId");
        });
    }
    public void close()throws IOException{lock.release();channel.close();}
}
