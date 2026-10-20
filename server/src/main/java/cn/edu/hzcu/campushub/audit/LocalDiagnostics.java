package cn.edu.hzcu.campushub.audit;

import cn.edu.hzcu.campushub.common.RequestTrace;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LocalDiagnostics {
    public record Diagnosis(String code,String action,String module,RequestTrace.Context context,List<String> causes,List<String> frames,List<String> sql){
        public String fields(){return "event=DIAGNOSTIC action="+action+" module="+module+" code="+code+" "+context.fields()+" frames="+String.join(",",frames)+" causes="+String.join(",",causes)+" sql="+String.join(",",sql);}
    }
    private final SafeFileLogs logs;
    private final boolean enabled;
    private final String environment;
    private final ObjectProvider<BuildProperties> build;
    private final RunMode mode;
    public LocalDiagnostics(SafeFileLogs logs,RunMode mode,@Value("${campushub.runtime-environment}") String environment,ObjectProvider<BuildProperties> build){
        if(!Set.of("local","test").contains(environment))throw new IllegalArgumentException("Invalid runtime environment");this.logs=logs;this.mode=mode;this.enabled=mode.debug();this.environment=environment;this.build=build;
        if(enabled)logs.prepareDiagnostics();
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("cn.edu.hzcu.campushub.audit.SafeDebug")).setLevel(enabled?ch.qos.logback.classic.Level.DEBUG:ch.qos.logback.classic.Level.INFO);
    }
    @jakarta.annotation.PreDestroy public void close(){((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("cn.edu.hzcu.campushub.audit.SafeDebug")).setLevel(ch.qos.logback.classic.Level.INFO);}
    @EventListener(ApplicationReadyEvent.class) public void ready(){
        var info=build.getIfAvailable();String version=info==null?"unknown":info.getVersion(),commit=info==null?"unknown":info.get("commit");
        if(version==null||!version.matches("[A-Za-z0-9._+\\-]{1,64}"))version="unknown";if(commit==null||!commit.matches("[0-9a-f]{40}"))commit="unknown";
        logs.startup(version,commit,environment,mode);
    }
    public boolean enabled(){return enabled;}
    public void failure(Throwable failure,int status,String code){if(enabled&&status>=500)logs.diagnostic(describe(failure,code));}
    public static Diagnosis describe(Throwable failure,String code){
        var causes=new ArrayList<String>();var frames=new ArrayList<String>();var sql=new ArrayList<String>();var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        Throwable cause=failure;
        for(int depth=0;cause!=null&&depth<4&&seen.add(cause);depth++,cause=cause.getCause()){
            String type=cause.getClass().getName();if(type.matches("[A-Za-z0-9_.$]{1,180}"))causes.add(type);
            for(StackTraceElement frame:cause.getStackTrace()){
                String file=frame.getFileName();if(frames.size()>=8)break;
                if(frame.getClassName().startsWith("cn.edu.hzcu.campushub.")&&frame.getClassName().matches("[A-Za-z0-9_.$]{1,180}")&&frame.getMethodName().matches("[A-Za-z0-9_$<>]{1,100}")&&file!=null&&file.matches("[A-Za-z0-9_$]{1,100}\\.java")&&frame.getLineNumber()>0)
                    frames.add(frame.getClassName()+"."+frame.getMethodName()+"("+file+":"+frame.getLineNumber()+")");
            }
            if(cause instanceof SQLException database){String state=database.getSQLState();if(state!=null&&state.matches("[A-Z0-9]{5}"))sql.add("state:"+state+"/vendor:"+database.getErrorCode());}
        }
        String action=MDC.get("action");if(!SafeFileLogs.ACTIONS.contains(action==null?"":action))action="OTHER";
        return new Diagnosis(RequestTrace.safeCode(code),action,RequestTrace.module(action),RequestTrace.context(),List.copyOf(causes),List.copyOf(frames),List.copyOf(sql));
    }
}
