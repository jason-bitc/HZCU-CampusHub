package cn.edu.hzcu.campushub.audit;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Synchronous bounded files containing only finite runtime-event schemas. */
@Component
public class SafeFileLogs extends AppenderBase<ILoggingEvent> implements InitializingBean,DisposableBean {
    public static final class StorageUnavailable extends IllegalStateException {
        public StorageUnavailable(){super("LOG_STORAGE_UNAVAILABLE");}
    }
    private final boolean enabled;
    private final Path root;
    private final long maxBytes;
    private final int archives;
    private FileChannel lockChannel;
    private FileLock lock;
    private Logger logger;
    private final AtomicLong failures=new AtomicLong();
    private volatile boolean healthy=true;
    public SafeFileLogs(@Value("${campushub.logs.enabled}") boolean enabled,@Value("${campushub.logs.root}") String directory,
            @Value("${campushub.logs.max-bytes}") long maxBytes,@Value("${campushub.logs.archives}") int archives){
        if(maxBytes<1024||maxBytes>100L*1024*1024||archives<1||archives>30)throw new IllegalArgumentException("Invalid runtime-log limits");
        this.enabled=enabled;this.root=Path.of(directory).toAbsolutePath().normalize();this.maxBytes=maxBytes;this.archives=archives;
    }
    @Override public void afterPropertiesSet(){
        if(!enabled)return;
        Path probe=null;
        try{
            for(Path p=root;p!=null;p=p.getParent())if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)&&(!p.toRealPath().equals(p)||Files.isSymbolicLink(p)))throw new IOException();
            Files.createDirectories(root);
            for(int i=0;i<=archives;i++)checked(i);
            Path lockPath=root.resolve("runtime.lock");if(Files.isSymbolicLink(lockPath))throw new IOException();
            lockChannel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            lock=lockChannel.tryLock();if(lock==null)throw new IOException();
            probe=Files.createTempFile(root,"probe-",".tmp");Files.writeString(probe,"probe");Files.delete(probe);probe=null;
            var context=(LoggerContext)LoggerFactory.getILoggerFactory();setContext(context);setName("campushub-safe-runtime");start();
            logger=context.getLogger("cn.edu.hzcu.campushub");logger.addAppender(this);
        }catch(IOException|RuntimeException e){
            release();if(probe!=null)try{Files.deleteIfExists(probe);}catch(IOException ignored){}
            throw new StorageUnavailable();
        }
    }
    private Path checked(int index)throws IOException{
        Path path=root.resolve(index==0?"campushub-runtime.log":"campushub-runtime."+index+".log");
        if(!path.startsWith(root)||!root.toRealPath().equals(root)||Files.isSymbolicLink(path)
                ||(Files.exists(path,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)))throw new IOException();
        return path;
    }
    @Override protected void append(ILoggingEvent event){
        String fields=fields(event);if(fields==null)return;
        String trace=event.getMDCPropertyMap().get("traceId");if(trace==null||!trace.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))trace="NONE";
        byte[] bytes=(Instant.ofEpochMilli(event.getTimeStamp())+" traceId="+trace+" "+fields+"\n").getBytes(StandardCharsets.UTF_8);
        try{
            Path current=checked(0);
            if(Files.exists(current)&&Files.size(current)+bytes.length>maxBytes){
                for(int i=archives;i>=1;i--){Path source=checked(i-1),target=checked(i);if(Files.exists(source))Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);}
            }
            Files.write(checked(0),bytes,StandardOpenOption.CREATE,StandardOpenOption.APPEND,LinkOption.NOFOLLOW_LINKS);healthy=true;
        }catch(IOException|RuntimeException e){
            healthy=false;if(failures.incrementAndGet()==1)System.err.println("RUNTIME_LOG_UNAVAILABLE traceId="+trace+"; business audit remains in database");
        }
    }
    static String fields(ILoggingEvent event){
        if(event.getLoggerName()==null||!event.getLoggerName().startsWith("cn.edu.hzcu.campushub."))return null;
        Object[] a=event.getArgumentArray();if(a==null)a=new Object[0];
        String message=event.getMessage();
        if(message==null||Arrays.stream(a).anyMatch(Objects::isNull))return null;
        if("request completed action={} method={} status={} result={} durationMs={}".equals(message)&&a.length==5
                &&Set.of("ARTICLE_AUTHOR","AUTHOR_CONTACT","AUTHOR_ARTICLES","AUTHOR_HOME","CONTACT_SETTINGS","OWN_PROFILE","OWN_ARTICLES","IMAGE","AUTH","OWN_IDENTITY","PUBLIC_ARTICLES","CATALOG","HEALTH","OTHER").contains(a[0])
                &&Set.of("GET","POST","PUT","DELETE","PATCH","HEAD","OPTIONS","OTHER").contains(a[1])
                &&a[2] instanceof Number status&&status.longValue()>=100&&status.longValue()<=599
                &&Set.of("OK","DENIED","CONFLICT","FAILED").contains(a[3])&&a[4] instanceof Number duration&&duration.longValue()>=0)
            return "event=REQUEST action="+a[0]+" method="+a[1]+" status="+status.longValue()+" result="+a[3]+" durationMs="+duration.longValue();
        if("business changed action={} objectType={} beforeRevision={} afterRevision={}".equals(message)&&a.length==4
                &&a[0] instanceof BusinessAudit.Action&&a[1] instanceof BusinessAudit.ObjectType&&a[2] instanceof Number before&&a[3] instanceof Number after)
            return "event=BUSINESS_CHANGE action="+a[0]+" objectType="+a[1]+" beforeRevision="+before.longValue()+" afterRevision="+after.longValue();
        if("business unchanged action={} objectType={}".equals(message)&&a.length==2&&a[0] instanceof BusinessAudit.Action&&a[1] instanceof BusinessAudit.ObjectType)
            return "event=BUSINESS_NO_CHANGE action="+a[0]+" objectType="+a[1];
        Map<String,String> failures=Map.of("database operation failed type={}","DATABASE","request failed type={}","UNEXPECTED",
                "media storage failed type={}","STORAGE","own upload temporary cleanup failed type={}","STORAGE_CLEANUP","own rolled-back upload cleanup failed type={}","STORAGE_CLEANUP");
        String kind=failures.get(message);
        if(kind!=null&&a.length==1&&a[0] instanceof String type&&type.matches("[A-Za-z][A-Za-z0-9]{0,99}"))return "event=FAILURE category="+kind+" type="+type;
        if("verification mail failed category={}".equals(message))return "event=FAILURE category=MAIL";
        return null;
    }
    public long failedWrites(){return failures.get();}
    public boolean healthy(){return healthy;}
    private void release(){try{if(lock!=null)lock.release();}catch(IOException ignored){}try{if(lockChannel!=null)lockChannel.close();}catch(IOException ignored){}}
    @Override public void destroy(){if(logger!=null)logger.detachAppender(this);stop();release();}
}
