package cn.edu.hzcu.campushub.audit;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.*;

class SafeFileLogsTest {
    @TempDir Path root;
    static LoggingEvent event(){
        return event("request completed action={} method={} status={} result={} durationMs={}",new Object[]{"AUTHOR_CONTACT","GET",200,"OK",1L});
    }
    static LoggingEvent event(String message,Object[] args){
        var logger=((LoggerContext)LoggerFactory.getILoggerFactory()).getLogger("cn.edu.hzcu.campushub.common.RequestTrace");
        var e=new LoggingEvent("test",logger,Level.INFO,message,null,args);e.setMDCPropertyMap(Map.of("traceId",UUID.randomUUID().toString()));return e;
    }
    @Test void rotationIsBoundedAndRestartAppendsToExistingFile()throws Exception{
        var logs=new SafeFileLogs(true,root.toString(),1024,2);logs.afterPropertiesSet();
        try{
            var collision=new SafeFileLogs(true,root.toString(),1024,2);assertThatThrownBy(collision::afterPropertiesSet).isInstanceOf(SafeFileLogs.StorageUnavailable.class);
            for(int i=0;i<40;i++)logs.doAppend(event());assertThat(logs.failedWrites()).isZero();
        }
        finally{logs.destroy();}
        var files=Files.list(root);try(files){assertThat(files.map(p->p.getFileName().toString()).toList()).containsExactlyInAnyOrder("runtime.lock","campushub-runtime.log","campushub-runtime.1.log","campushub-runtime.2.log");}
        for(String name:List.of("campushub-runtime.log","campushub-runtime.1.log","campushub-runtime.2.log"))assertThat(Files.size(root.resolve(name))).isLessThanOrEqualTo(1024);
        String before=Files.readString(root.resolve("campushub-runtime.log"));var reboot=new SafeFileLogs(true,root.toString(),2048,2);reboot.afterPropertiesSet();try{reboot.doAppend(event());}finally{reboot.destroy();}
        assertThat(Files.readString(root.resolve("campushub-runtime.log"))).startsWith(before);
    }
    @Test void fileFailurePreservesOriginalAndRecoveryIsObservable()throws Exception{
        var logs=new SafeFileLogs(true,root.toString(),1024,2);logs.afterPropertiesSet();
        Path current=root.resolve("campushub-runtime.log"),backup=root.resolve("own-existing-log");
        try{
            logs.doAppend(event());Files.move(current,backup);Files.createDirectory(current);
            logs.doAppend(event());assertThat(logs.healthy()).isFalse();assertThat(logs.failedWrites()).isEqualTo(1);
            Files.delete(current);Files.move(backup,current);String original=Files.readString(current);logs.doAppend(event());assertThat(logs.healthy()).isTrue();assertThat(Files.readString(current)).startsWith(original);
        }finally{logs.destroy();}
    }
    @Test void unsafeSchemasAndUserPayloadsNeverEnterFileAndConfigFailureIsSanitized()throws Exception{
        String secret="private-payload-"+UUID.randomUUID();var e=event(secret,new Object[]{secret});assertThat(SafeFileLogs.fields(e)).isNull();
        e=event("request completed action={} method={} status={} result={} durationMs={}",new Object[]{secret,"GET",200,"OK",1L});assertThat(SafeFileLogs.fields(e)).isNull();
        Path blocked=root.resolve(secret);Files.writeString(blocked,"preserved");var logs=new SafeFileLogs(true,blocked.toString(),1024,2);
        assertThatThrownBy(logs::afterPropertiesSet).isInstanceOf(IllegalStateException.class).hasMessage("LOG_STORAGE_UNAVAILABLE").hasNoCause();assertThat(Files.readString(blocked)).isEqualTo("preserved");
        var disabled=new SafeFileLogs(false,root.resolve("disabled").toString(),1024,2);disabled.afterPropertiesSet();disabled.destroy();assertThat(Files.exists(root.resolve("disabled"))).isFalse();
    }
}
