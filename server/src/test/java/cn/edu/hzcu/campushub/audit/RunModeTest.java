package cn.edu.hzcu.campushub.audit;

import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RunModeTest {
    @TempDir Path root;
    @Test void modesNormalizeAndLegacyTrueCannotOverrideRelease(){
        var release=new RunMode("ReLeAsE",true,true);assertThat(release.debug()).isFalse();assertThat(release.legacyIgnored()).isTrue();
        assertThat(new RunMode(" DEBUG ",true,false).debug()).isTrue();
        for(String value:new String[]{"","trace","production","debug,release"})assertThatThrownBy(()->new RunMode(value,true,false)).hasMessage("RUN_MODE_INVALID");
    }
    @Test void debugCannotClaimReadyWithoutFileLogging(){
        assertThatThrownBy(()->new RunMode("debug",false,false)).hasMessage("DEBUG_REQUIRES_FILE_LOGS");
        assertThat(new RunMode("release",false,true).debug()).isFalse();
    }
    @Test void debugChecksDiagnosticTargetsWithoutChangingOldContent()throws Exception{
        var logs=new SafeFileLogs(true,root.toString(),4096,2);logs.afterPropertiesSet();
        Path diagnostic=root.resolve("campushub-diagnostic.log");Files.writeString(diagnostic,"old-private-record\n");
        try{
            logs.prepareDiagnostics();assertThat(Files.readString(diagnostic)).isEqualTo("old-private-record\n");
            Path backup=root.resolve("own-backup");Files.move(diagnostic,backup);Files.createDirectory(diagnostic);
            assertThatThrownBy(logs::prepareDiagnostics).isInstanceOf(SafeFileLogs.StorageUnavailable.class).hasNoCause();
            Files.delete(diagnostic);Files.move(backup,diagnostic);
        }finally{logs.destroy();}
    }
}
