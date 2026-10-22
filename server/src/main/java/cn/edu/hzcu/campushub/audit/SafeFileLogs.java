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
import cn.edu.hzcu.campushub.common.RequestTrace;

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
    private volatile boolean diagnosticHealthy=true;
    public static final Set<String> ACTIONS=Set.of("ARTICLE_AUTHOR","AUTHOR_CONTACT","AUTHOR_ARTICLES","AUTHOR_HOME","CONTACT_SETTINGS","OWN_PROFILE","OWN_ARTICLES","IMAGE","AUTH","OWN_IDENTITY","PUBLIC_ARTICLES","CATALOG","HEALTH","OTHER","COMMENT_LIST","COMMENT_CREATE","COMMENT_DELETE","COMMENT_REPLIES","COMMENT_IMAGE_UPLOAD","COMMENT_IMAGE_READ","AUTH_REGISTER_CODE","AUTH_REGISTER","AUTH_LOGIN","AUTH_LOGOUT","AUTH_RESET_CODE","AUTH_PASSWORD_RESET","IDENTITY_READ","PROFILE_READ","PROFILE_UPDATE","DRAFT_CREATE","DRAFT_LIST","DRAFT_READ","DRAFT_SAVE","ARTICLE_IMAGE_UPLOAD","WRITING_PUBLISH","WRITING_UNPUBLISH","WRITING_RESTORE","WRITING_DELETE","CONTACT_READ","CONTACT_UPDATE","ARTICLE_LIST","ARTICLE_DETAIL","LIKE_SET","LIKE_CANCEL","FAVORITE_SET","FAVORITE_CANCEL","INTERACTION_READ","FAVORITES_LIST","COIN_BALANCE","COIN_LEDGER","REWARDS_READ","LOGIN_REWARD","READING_START","READING_REWARD","COIN_STATE","ARTICLE_COIN","CASE_IMAGE_UPLOAD","CASE_IMAGE_READ","CASE_SUBMIT","CASE_LIST","CASE_DETAIL","ADMIN_LOGIN","ADMIN_LOGOUT","ADMIN_ME","ADMIN_CASE_LIST","ADMIN_CASE_DETAIL","ADMIN_CASE_CONTEXT","ADMIN_CASE_MEDIA","ADMIN_CASE_CLAIM","ADMIN_CASE_RELEASE","ADMIN_CASE_DECISION","ADMIN_OTHER");
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
    private Path checked(int index)throws IOException{return checked("runtime",index);}
    public synchronized void prepareDiagnostics(){
        if(!enabled||!isStarted())throw new StorageUnavailable();
        try{
            for(int i=0;i<=archives;i++)checked("diagnostic",i);
            try(var writable=FileChannel.open(checked("diagnostic",0),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) { }
        }catch(IOException|RuntimeException failure){throw new StorageUnavailable();}
    }
    private Path checked(String channel,int index)throws IOException{
        Path path=root.resolve(index==0?"campushub-"+channel+".log":"campushub-"+channel+"."+index+".log");
        if(!path.startsWith(root)||!root.toRealPath().equals(root)||Files.isSymbolicLink(path)
                ||(Files.exists(path,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)))throw new IOException();
        return path;
    }
    @Override protected void append(ILoggingEvent event){
        String fields=fields(event);if(fields==null)return;
        String trace=event.getMDCPropertyMap().get("traceId");if(trace==null||!trace.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))trace="NONE";
        write("runtime",Instant.ofEpochMilli(event.getTimeStamp()),trace,fields);
    }
    private synchronized void write(String channel,Instant time,String trace,String fields){
        if(!enabled||!isStarted())return;
        byte[] bytes=(time+" traceId="+trace+" "+fields+"\n").getBytes(StandardCharsets.UTF_8);
        if(bytes.length>maxBytes){byte[] bounded=Arrays.copyOf(bytes,(int)maxBytes);byte[] suffix=" truncated=true\n".getBytes(StandardCharsets.US_ASCII);System.arraycopy(suffix,0,bounded,bounded.length-suffix.length,suffix.length);bytes=bounded;}
        try{
            Path current=checked(channel,0);
            if(Files.exists(current)&&Files.size(current)+bytes.length>maxBytes){
                for(int i=archives;i>=1;i--){Path source=checked(channel,i-1),target=checked(channel,i);if(Files.exists(source))Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);}
            }
            Files.write(checked(channel,0),bytes,StandardOpenOption.CREATE,StandardOpenOption.APPEND,LinkOption.NOFOLLOW_LINKS);if(channel.equals("diagnostic"))diagnosticHealthy=true;else healthy=true;
        }catch(IOException|RuntimeException e){
            if(channel.equals("diagnostic"))diagnosticHealthy=false;else healthy=false;if(failures.incrementAndGet()==1)System.err.println("RUNTIME_LOG_UNAVAILABLE channel="+channel+" traceId="+trace+"; business audit remains in database");
        }
    }
    public void startup(String version,String commit,String environment,boolean diagnostics){startup(version,commit,environment,new RunMode(diagnostics?"debug":"release",enabled,false));}
    public void startup(String version,String commit,String environment,RunMode mode){LoggerFactory.getLogger(SafeFileLogs.class).info("application ready version={} commit={} environment={} mode={} fileLogs={} diagnostics={} legacyDiagnosticsIgnored={}",version,commit,environment,mode.value(),enabled,mode.debug(),mode.legacyIgnored());}
    public void diagnostic(LocalDiagnostics.Diagnosis data){String trace=RequestTrace.current();if(trace==null||!trace.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))trace="NONE";write("diagnostic",Instant.now(),trace,data.fields());}
    static String fields(ILoggingEvent event){
        if(event.getLoggerName()==null||!event.getLoggerName().startsWith("cn.edu.hzcu.campushub."))return null;
        Object[] a=event.getArgumentArray();if(a==null)a=new Object[0];
        String message=event.getMessage();
        if(message==null||Arrays.stream(a).anyMatch(Objects::isNull))return null;
        if("request stage action={} module={} context={}".equals(message)&&event.getLevel()==Level.DEBUG&&a.length==3&&ACTIONS.contains(a[0])&&RequestTrace.module(a[0].toString()).equals(a[1])&&a[2] instanceof RequestTrace.Context context)
            return "event=DEBUG_STAGE action="+a[0]+" module="+a[1]+" "+context.fields();
        if("application ready version={} commit={} environment={} mode={} fileLogs={} diagnostics={} legacyDiagnosticsIgnored={}".equals(message)&&a.length==7&&a[0] instanceof String version&&version.matches("[A-Za-z0-9._+\\-]{1,64}")&&a[1] instanceof String commit&&(commit.equals("unknown")||commit.matches("[0-9a-f]{40}"))&&Set.of("local","test").contains(a[2])&&Set.of("debug","release").contains(a[3])&&a[4] instanceof Boolean&&a[5] instanceof Boolean&&a[6] instanceof Boolean)
            return "event=STARTUP version="+version+" commit="+commit+" environment="+a[2]+" mode="+a[3]+" fileLogs="+a[4]+" diagnostics="+a[5]+" legacyDiagnosticsIgnored="+a[6];
        if("application ready version={} commit={} environment={} fileLogs={} diagnostics={}".equals(message)&&a.length==5&&a[0] instanceof String version&&version.matches("[A-Za-z0-9._+\\-]{1,64}")&&a[1] instanceof String commit&&(commit.equals("unknown")||commit.matches("[0-9a-f]{40}"))&&Set.of("local","test").contains(a[2])&&a[3] instanceof Boolean&&a[4] instanceof Boolean)
            return "event=STARTUP version="+version+" commit="+commit+" environment="+a[2]+" fileLogs="+a[3]+" diagnostics="+a[4];
        if("request completed action={} method={} status={} result={} durationMs={}".equals(message)&&a.length==5
                &&ACTIONS.contains(a[0])
                &&Set.of("GET","POST","PUT","DELETE","PATCH","HEAD","OPTIONS","OTHER").contains(a[1])
                &&a[2] instanceof Number status&&status.longValue()>=100&&status.longValue()<=599
                &&Set.of("OK","DENIED","CONFLICT","FAILED").contains(a[3])&&a[4] instanceof Number duration&&duration.longValue()>=0)
            return "event=REQUEST action="+a[0]+" method="+a[1]+" status="+status.longValue()+" result="+a[3]+" durationMs="+duration.longValue();
        if("request completed action={} method={} status={} result={} durationMs={} module={} code={} context={}".equals(message)&&a.length==8&&ACTIONS.contains(a[0])&&Set.of("GET","POST","PUT","DELETE","PATCH","HEAD","OPTIONS","OTHER").contains(a[1])&&a[2] instanceof Number status&&status.longValue()>=100&&status.longValue()<=599&&Set.of("OK","DENIED","CONFLICT","FAILED").contains(a[3])&&a[4] instanceof Number duration&&duration.longValue()>=0&&a[5] instanceof String module&&module.equals(RequestTrace.module(a[0].toString()))&&a[6] instanceof String code&&RequestTrace.safeCode(code).equals(code)&&a[7] instanceof RequestTrace.Context context)
            return "event=REQUEST action="+a[0]+" method="+a[1]+" status="+status.longValue()+" result="+a[3]+" durationMs="+duration.longValue()+" module="+module+" code="+code+" "+context.fields();
        if("business changed action={} objectType={} beforeRevision={} afterRevision={}".equals(message)&&a.length==4
                &&a[0] instanceof BusinessAudit.Action&&a[1] instanceof BusinessAudit.ObjectType&&a[2] instanceof Number before&&a[3] instanceof Number after)
            return "event=BUSINESS_CHANGE action="+a[0]+" objectType="+a[1]+" beforeRevision="+before.longValue()+" afterRevision="+after.longValue();
        if("business unchanged action={} objectType={}".equals(message)&&a.length==2&&a[0] instanceof BusinessAudit.Action&&a[1] instanceof BusinessAudit.ObjectType)
            return "event=BUSINESS_NO_CHANGE action="+a[0]+" objectType="+a[1];
        if("business changed action={} objectType={} beforeRevision={} afterRevision={} actorDomain={}".equals(message)&&a.length==5&&a[0] instanceof BusinessAudit.Action&&a[1] instanceof BusinessAudit.ObjectType&&a[2] instanceof Number before&&a[3] instanceof Number after&&a[4] instanceof RequestTrace.Domain)
            return "event=BUSINESS_CHANGE action="+a[0]+" objectType="+a[1]+" beforeRevision="+before.longValue()+" afterRevision="+after.longValue()+" actorDomain="+a[4];
        if("business unchanged action={} objectType={} actorDomain={}".equals(message)&&a.length==3&&a[0] instanceof BusinessAudit.Action&&a[1] instanceof BusinessAudit.ObjectType&&a[2] instanceof RequestTrace.Domain)
            return "event=BUSINESS_NO_CHANGE action="+a[0]+" objectType="+a[1]+" actorDomain="+a[2];
        if("case access actorId={} caseId={} objectType={}".equals(message)&&a.length==3&&a[0] instanceof Long actor&&actor>0&&a[1] instanceof Long id&&id>0&&Set.of("CONTEXT","MEDIA").contains(a[2]))
            return "event=CASE_ACCESS actorDomain=COMMUNITY_ADMIN actorId="+actor+" caseId="+id+" objectType="+a[2];
        Map<String,String> failures=Map.of("database operation failed type={}","DATABASE","request failed type={}","UNEXPECTED",
                "media storage failed type={}","STORAGE","own upload temporary cleanup failed type={}","STORAGE_CLEANUP","own rolled-back upload cleanup failed type={}","STORAGE_CLEANUP");
        String kind=failures.get(message);
        if(kind!=null&&a.length==1&&a[0] instanceof String type&&type.matches("[A-Za-z][A-Za-z0-9]{0,99}"))return "event=FAILURE category="+kind+" type="+type;
        if("verification mail failed category={}".equals(message))return "event=FAILURE category=MAIL";
        return null;
    }
    public long failedWrites(){return failures.get();}
    public boolean healthy(){return healthy&&diagnosticHealthy;}
    private void release(){try{if(lock!=null)lock.release();}catch(IOException ignored){}try{if(lockChannel!=null)lockChannel.close();}catch(IOException ignored){}}
    @Override public void destroy(){if(logger!=null)logger.detachAppender(this);stop();release();}
}
