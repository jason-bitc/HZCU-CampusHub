package cn.edu.hzcu.campushub.audit;

import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalDiagnosticsTest {
    @TempDir Path root;
    private final String secret="nested-secret-"+UUID.randomUUID();
    Throwable failure(){return new IllegalStateException("SQL text password token body "+secret,new SQLException("SQL parameter "+secret,"45000",1644));}
    @AfterEach void clean(){MDC.clear();((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("cn.edu.hzcu.campushub.audit.SafeDebug")).setLevel(ch.qos.logback.classic.Level.INFO);}
    @Test void limitedCauseChainContainsOnlyTypesProjectFramesAndSqlCodes(){
        MDC.put("action","COMMENT_CREATE");var record=LocalDiagnostics.describe(failure(),"SERVICE_UNAVAILABLE");String text=record.fields();
        assertThat(text).contains("module=COMMENT","code=SERVICE_UNAVAILABLE","LocalDiagnosticsTest.java:","state:45000/vendor:1644").doesNotContain(secret,"SQL text","SQL parameter","password","token body");
        Throwable chain=failure();for(int i=0;i<10;i++)chain=new IllegalStateException(secret,chain);var bounded=LocalDiagnostics.describe(chain,"bad code "+secret);assertThat(bounded.causes()).hasSize(4);assertThat(bounded.frames().size()).isLessThanOrEqualTo(8);assertThat(bounded.fields()).doesNotContain(secret).contains("code=INTERNAL_ERROR");
    }
    @Test void disabledDoesNotCreateDiagnosticsEnabledWritesAndCanBeTurnedOff()throws Exception{
        var logs=new SafeFileLogs(true,root.toString(),4096,2);logs.afterPropertiesSet();
        try{
            ObjectProvider<BuildProperties> build=mock(ObjectProvider.class);var disabled=new LocalDiagnostics(logs,new RunMode("release",true,false),"test",build);disabled.ready();disabled.failure(failure(),503,"SERVICE_UNAVAILABLE");assertThat(Files.exists(root.resolve("campushub-diagnostic.log"))).isFalse();
            MDC.put("traceId",UUID.randomUUID().toString());MDC.put("action","COMMENT_IMAGE_UPLOAD");var enabled=new LocalDiagnostics(logs,new RunMode("debug",true,false),"test",build);enabled.ready();enabled.failure(failure(),503,"SERVICE_UNAVAILABLE");String before=Files.readString(root.resolve("campushub-diagnostic.log"));assertThat(before).contains(MDC.get("traceId"),"COMMENT_IMAGE_UPLOAD","LocalDiagnosticsTest.java:").doesNotContain(secret);
            disabled.failure(failure(),503,"SERVICE_UNAVAILABLE");enabled.failure(failure(),400,"INVALID_PARAMETER");assertThat(Files.readString(root.resolve("campushub-diagnostic.log"))).isEqualTo(before);
            assertThat(Files.readString(root.resolve("campushub-runtime.log"))).contains("event=STARTUP version=unknown commit=unknown environment=test","diagnostics=false","diagnostics=true");
        }finally{logs.destroy();}
    }
    @Test void diagnosticRotationAndFaultHaveIndependentObservableHealth()throws Exception{
        var logs=new SafeFileLogs(true,root.toString(),1024,2);logs.afterPropertiesSet();
        try{
            var diagnostic=new LocalDiagnostics(logs,new RunMode("debug",true,false),"test",mock(ObjectProvider.class));for(int i=0;i<20;i++)diagnostic.failure(failure(),503,"SERVICE_UNAVAILABLE");
            for(String name:List.of("campushub-diagnostic.log","campushub-diagnostic.1.log","campushub-diagnostic.2.log"))assertThat(Files.size(root.resolve(name))).isLessThanOrEqualTo(1024);
            Path file=root.resolve("campushub-diagnostic.log"),backup=root.resolve("diagnostic-own-backup");Files.move(file,backup);Files.createDirectory(file);
            try{diagnostic.failure(failure(),503,"SERVICE_UNAVAILABLE");logs.startup("0.1.0","unknown","test",true);assertThat(logs.healthy()).isFalse();assertThat(logs.failedWrites()).isGreaterThan(0);}finally{Files.delete(file);Files.move(backup,file);}
            diagnostic.failure(failure(),503,"SERVICE_UNAVAILABLE");assertThat(logs.healthy()).isTrue();
        }finally{logs.destroy();}
    }
}
