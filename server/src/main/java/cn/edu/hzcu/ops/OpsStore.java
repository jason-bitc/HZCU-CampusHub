package cn.edu.hzcu.ops;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Fsynced control-plane journal independent of business MySQL. */
public final class OpsStore implements AutoCloseable {
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
            ((ObjectNode)job).put("state","INTERRUPTED").put("errorCode","EXECUTOR_INTERRUPTED");state.put("recoveryRequired",true);
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
    public void close()throws IOException{lock.release();channel.close();}
}
