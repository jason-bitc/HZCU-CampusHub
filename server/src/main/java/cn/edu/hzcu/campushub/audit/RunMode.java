package cn.edu.hzcu.campushub.audit;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** One runtime policy; logging modes never select a database or a data profile. */
@Component
public final class RunMode {
    private final String value;
    private final boolean legacyIgnored;
    public RunMode(@Value("${campushub.run-mode:release}") String value,
                   @Value("${campushub.logs.enabled:true}") boolean fileLogs,
                   @Value("${campushub.logs.diagnostics-enabled:false}") boolean legacyDiagnostics) {
        this.value=value==null?"":value.strip().toLowerCase(Locale.ROOT);
        if(!this.value.equals("debug")&&!this.value.equals("release"))throw new IllegalArgumentException("RUN_MODE_INVALID");
        if(debug()&&!fileLogs)throw new IllegalArgumentException("DEBUG_REQUIRES_FILE_LOGS");
        legacyIgnored=legacyDiagnostics;
    }
    public String value(){return value;}
    public boolean debug(){return value.equals("debug");}
    public boolean legacyIgnored(){return legacyIgnored;}
}
