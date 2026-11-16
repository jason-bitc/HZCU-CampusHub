package cn.edu.hzcu.campushub.article;

import cn.edu.hzcu.campushub.auth.AuthTestSupport;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Runs byte-identical scripts and the actual JAR in an isolated local config tree. */
final class ScriptModeProbe {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    static int port()throws Exception{try(var socket=new java.net.ServerSocket(0,50,InetAddress.getLoopbackAddress())){return socket.getLocalPort();}}
    static void verify(Path root)throws Exception{
        Files.createDirectories(root.resolve("scripts"));Files.createDirectories(root.resolve("target"));
        var hashes=new LinkedHashMap<String,String>();
        for(String file:List.of("scripts/Start-Server.ps1","scripts/Use-LocalConfig.ps1","target/campushub-server-0.1.0.jar")){
            Path source=Path.of(file),copy=root.resolve(file);Files.copy(source,copy);assertThat(Files.mismatch(source,copy)).isEqualTo(-1);hashes.put(file,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(copy))));
        }
        Path wrapper=root.resolve("probe.ps1");Files.writeString(wrapper,"""
            param([string]$Mode,[int]$Port,[switch]$Stub,[switch]$ForceFailure)
            $ErrorActionPreference='Stop'
            $env:CAMPUSHUB_RUN_MODE='release'
            if($Mode -eq 'Default') { Remove-Item Env:CAMPUSHUB_RUN_MODE -ErrorAction SilentlyContinue }
            $env:CAMPUSHUB_LOG_DIAGNOSTICS='false'
            $env:CAMPUSHUB_DB_NAME='outside-sentinel'
            $beforeMode=$env:CAMPUSHUB_RUN_MODE;$beforeDiagnostics=$env:CAMPUSHUB_LOG_DIAGNOSTICS;$beforeDatabase=$env:CAMPUSHUB_DB_NAME
            $rejected=$false;$global:observedMode=$null;$global:probeNativeFail=[bool]$ForceFailure
            if($Stub) {
                function global:java {
                    $global:observedMode=$env:CAMPUSHUB_RUN_MODE
                    if($global:probeNativeFail) { throw 'CONTROLLED_NATIVE_FAILURE' }
                    $global:LASTEXITCODE=0
                }
            }
            try {
                if($Mode -eq 'Default') { & ./scripts/Start-Server.ps1 -Port $Port -Profile demo }
                else { & ./scripts/Start-Server.ps1 -Port $Port -Profile demo -Mode $Mode }
            } catch { $rejected=$true }
            $restored=($env:CAMPUSHUB_RUN_MODE -ceq $beforeMode) -and ($env:CAMPUSHUB_LOG_DIAGNOSTICS -ceq $beforeDiagnostics) -and ($env:CAMPUSHUB_DB_NAME -ceq $beforeDatabase)
            @{environmentRestored=$restored;scriptRejected=$rejected;observedMode=$global:observedMode} | ConvertTo-Json | Set-Content -LiteralPath ./probe-result.json -Encoding utf8
            if(!$restored) { exit 41 }
            """);
        Map<String,String> config=new LinkedHashMap<>();config.put("CAMPUSHUB_DB_HOST",System.getenv().getOrDefault("CAMPUSHUB_DB_HOST","127.0.0.1"));config.put("CAMPUSHUB_DB_PORT",System.getenv().getOrDefault("CAMPUSHUB_DB_PORT","3306"));config.put("CAMPUSHUB_DB_NAME","campushub_test");config.put("CAMPUSHUB_DB_USERNAME",System.getenv("CAMPUSHUB_TEST_DB_USERNAME"));config.put("CAMPUSHUB_DB_PASSWORD",System.getenv("CAMPUSHUB_TEST_DB_PASSWORD"));config.put("CAMPUSHUB_AUTH_PEPPER",AuthTestSupport.TEST_KEY);config.put("CAMPUSHUB_MAIL_FROM","controlled@example.invalid");config.put("CAMPUSHUB_MAIL_USERNAME","controlled@example.invalid");config.put("CAMPUSHUB_MAIL_PASSWORD","unused-controlled-mail-password");config.put("CAMPUSHUB_LOG_ENABLED","true");config.put("CAMPUSHUB_LOG_DIAGNOSTICS","true");config.put("CAMPUSHUB_RUNTIME_ENV","test");config.put("CAMPUSHUB_LOG_ROOT",root.resolve("logs").toString());config.put("CAMPUSHUB_MEDIA_ROOT",root.resolve("media").toString());
        var checks=new ArrayList<Map<String,Object>>();
        for(String selected:List.of("Debug","Release","Default")){
            config.put("CAMPUSHUB_RUN_MODE",selected.equals("Debug")?"release":"debug");if(selected.equals("Default"))config.remove("CAMPUSHUB_RUN_MODE");
            Files.writeString(root.resolve(".env.local.json"),JSON.writeValueAsString(config));int port=port();Path output=root.resolve("real-"+selected+".log");String expected=selected.equals("Debug")?"debug":"release";
            Path diagnostic=root.resolve("logs/campushub-diagnostic.log");long before=Files.exists(diagnostic)?Files.size(diagnostic):0;
            var process=new ProcessBuilder("pwsh","-NoProfile","-File",wrapper.toString(),"-Mode",selected,"-Port",String.valueOf(port)).directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            boolean healthy=false;
            try{
                long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();var http=HttpClient.newHttpClient();
                while(System.nanoTime()<deadline&&process.isAlive()){
                    try{var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/health")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()==200){healthy=true;break;}}catch(Exception ignored){}Thread.sleep(150);
                }
                assertThat(healthy).as("actual JAR %s ready",selected).isTrue();String text=Files.readString(output);assertThat(text).contains("mode="+expected,"diagnostics="+expected.equals("debug"));
                var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/articles/1001")).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);
                if(!expected.equals("debug"))assertThat(Files.exists(diagnostic)?Files.size(diagnostic):0).isEqualTo(before);
            }finally{
                // Stop only the Java descendant created by this wrapper; let its finally restore the environment.
                process.toHandle().descendants().filter(p->p.info().command().orElse("").toLowerCase(Locale.ROOT).endsWith("java.exe")).forEach(ProcessHandle::destroy);
                if(!process.waitFor(15,TimeUnit.SECONDS)){process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();process.waitFor();}
            }
            assertThat(process.exitValue()).isZero();var result=JSON.readTree(Files.readString(root.resolve("probe-result.json")));assertThat(result.path("environmentRestored").asBoolean()).isTrue();checks.add(Map.of("kind","actual-jar-script","mode",expected,"healthUp",healthy,"portPreserved",true,"dataProfile","demo","environmentRestored",true,"oldDiagnosticTrueIgnored",true));
        }
        for(boolean failure:List.of(false,true))for(String selected:List.of("Debug","Release")){
            config.put("CAMPUSHUB_RUN_MODE",selected.equals("Debug")?"release":"debug");Files.writeString(root.resolve(".env.local.json"),JSON.writeValueAsString(config));var args=new ArrayList<>(List.of("pwsh","-NoProfile","-File",wrapper.toString(),"-Mode",selected,"-Port","18089","-Stub"));if(failure)args.add("-ForceFailure");
            var process=new ProcessBuilder(args).directory(root.toFile()).redirectErrorStream(true).redirectOutput(root.resolve("stub-output.log").toFile()).start();assertThat(process.waitFor(15,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();var result=JSON.readTree(Files.readString(root.resolve("probe-result.json")));assertThat(result.path("observedMode").asString()).isEqualTo(selected.toLowerCase(Locale.ROOT));assertThat(result.path("scriptRejected").asBoolean()).isEqualTo(failure);assertThat(result.path("environmentRestored").asBoolean()).isTrue();checks.add(Map.of("kind","script-return-boundary","mode",selected.toLowerCase(Locale.ROOT),"controlledNativeFailure",failure,"environmentRestored",true));
        }
        for(String bad:List.of("illegal","debug-no-files","debug-blocked-diagnostic")){
            var args=new ArrayList<>(List.of("java","-jar",root.resolve("target/campushub-server-0.1.0.jar").toString(),"--server.port="+port(),"--spring.profiles.active=demo","--campushub.run-mode="+(bad.equals("illegal")?"invalid":"debug")));
            if(bad.equals("debug-no-files"))args.add("--campushub.logs.enabled=false");
            if(bad.equals("debug-blocked-diagnostic")){Path logRoot=root.resolve("blocked-diag");Files.createDirectories(logRoot.resolve("campushub-diagnostic.log"));args.add("--campushub.logs.root="+logRoot);}
            Path output=root.resolve(bad+".log");var builder=new ProcessBuilder(args).directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());builder.environment().putAll(config);var process=builder.start();
            try{assertThat(process.waitFor(35,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isNotZero();String text=Files.readString(output);assertThat(text).contains(bad.equals("debug-blocked-diagnostic")?"LOG_STORAGE_UNAVAILABLE":"CONFIG_INVALID").doesNotContain("application ready");}finally{if(process.isAlive()){process.destroyForcibly();process.waitFor();}}
            checks.add(Map.of("kind","actual-jar-rejection","case",bad,"nonzeroExit",true,"ready",false));
        }
        try(var files=Files.list(root)){for(Path file:files.filter(p->p.toString().endsWith(".log")).toList()){
            String text=Files.readString(file);for(String key:List.of("CAMPUSHUB_DB_PASSWORD","CAMPUSHUB_AUTH_PEPPER","CAMPUSHUB_MAIL_PASSWORD"))assertThat(text).doesNotContain(config.get(key));
        }}
        Files.writeString(Path.of("target/batch6-script-modes.json"),JSON.writeValueAsString(Map.of("status","PASS","database","campushub_test","byteIdenticalHashes",hashes,"checks",checks)));
    }
}
