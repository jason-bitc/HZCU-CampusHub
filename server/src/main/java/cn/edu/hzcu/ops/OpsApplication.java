package cn.edu.hzcu.ops;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.JsonNode;

/** Independent executable entry; does not boot Spring or connect to business DB at startup. */
public final class OpsApplication implements AutoCloseable {
    public static final class Failure extends RuntimeException {
        final int status;final String code;
        Failure(int status,String code){super(code);this.status=status;this.code=code;}
    }
    private final OpsStore store;
    private final OpsAuth auth;
    private final OpsRunner runner;
    private final OpsJobs jobs;
    private final OpsDailyBackup dailyBackup;
    private final OpsConfig config;
    private final HttpServer server;
    private final ExecutorService requests=Executors.newFixedThreadPool(4);
    public OpsApplication(OpsConfig config)throws IOException {
        this(config, Clock.systemUTC());
    }
    OpsApplication(OpsConfig config,Clock clock)throws IOException {
        this.config=config;store=new OpsStore(config.controlRoot());auth=new OpsAuth(store);runner=new OpsRunner(config);jobs=new OpsJobs(store,runner);
        dailyBackup=config.dailyBackupTime()==null?null:new OpsDailyBackup(config.dailyBackupTime(),clock,jobs,runner::scheduledBackupReady);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",config.port()),32);server.createContext("/",this::handle);server.setExecutor(requests);
    }
    public void start(){server.start();if(dailyBackup!=null)dailyBackup.start();}
    private static String text(JsonNode n,String key){var value=n.path(key);if(!value.isString()||value.asString().length()>2000)throw new Failure(400,"INVALID_PARAMETER");return value.asString();}
    private static JsonNode body(HttpExchange e,String... fields)throws IOException {
        if(!"application/json".equalsIgnoreCase(e.getRequestHeaders().getFirst("Content-Type")))throw new Failure(415,"UNSUPPORTED_MEDIA_TYPE");
        byte[] bytes=e.getRequestBody().readNBytes(32769);if(bytes.length>32768)throw new Failure(413,"REQUEST_TOO_LARGE");
        JsonNode n;try{n=OpsStore.JSON.readTree(bytes);}catch(RuntimeException bad){throw new Failure(400,"INVALID_PARAMETER");}
        var keys=new HashSet<String>();if(n!=null)n.properties().forEach(v->keys.add(v.getKey()));if(n==null||!n.isObject()||!keys.equals(Set.of(fields)))throw new Failure(400,"INVALID_PARAMETER");return n;
    }
    private static void method(HttpExchange e,String expected){if(!e.getRequestMethod().equals(expected))throw new Failure(405,"METHOD_NOT_ALLOWED");}
    private static void empty(HttpExchange e)throws IOException {if(e.getRequestBody().read()!=-1)throw new Failure(400,"INVALID_PARAMETER");}
    private Object dispatch(HttpExchange e)throws Exception {
        String path=e.getRequestURI().getPath();
        String query=e.getRequestURI().getRawQuery();
        if(query!=null&&!path.equals("/api/v1/ops/logs"))throw new Failure(400,"INVALID_PARAMETER");
        if(path.equals("/health")){method(e,"GET");empty(e);return Map.of("status","UP","actorDomain","OPS_ADMIN");}
        if(path.equals("/api/v1/ops/auth/login")){
            method(e,"POST");var n=body(e,"login","password");return auth.login(text(n,"login"),text(n,"password"));
        }
        var values=e.getRequestHeaders().get("Authorization");String header=values!=null&&values.size()==1?values.getFirst():"";
        if(!header.startsWith("Bearer "))throw new Failure(401,"OPS_UNAUTHORIZED");String token=header.substring(7);auth.authenticate(token);
        if(path.equals("/api/v1/ops/auth/logout")){method(e,"POST");empty(e);auth.logout(token);return Map.of("loggedOut",true);}
        if(path.equals("/api/v1/ops/me")){method(e,"GET");empty(e);return Map.of("actorDomain","OPS_ADMIN","capabilities",List.of("STATUS_READ","TASK_READ","DEPLOY","RESTART","BACKUP","RESTORE"));}
        if(path.equals("/api/v1/ops/status")){method(e,"GET");empty(e);return status();}
        if(path.equals("/api/v1/ops/artifacts")){method(e,"GET");empty(e);return Map.of("items",runner.artifacts());}
        if(path.equals("/api/v1/ops/backups")){method(e,"GET");empty(e);return Map.of("items",runner.backups());}
        if(path.equals("/api/v1/ops/logs")){method(e,"GET");empty(e);return logs(OpsLogReader.parseQuery(query));}
        if(path.equals("/api/v1/ops/confirmations")){
            method(e,"POST");
            var n=body(e,"action","target","referenceId");
            String action=text(n,"action"),target=text(n,"target"),reference=text(n,"referenceId");
            OpsJobs.validate(action,target,reference);
            var restore=action.equals("RESTORE")?runner.preflightRestore(reference):null;
            return jobs.prepare(action,target,reference,token,restore);
        }
        if(path.equals("/api/v1/ops/jobs")){
            if(e.getRequestMethod().equals("GET")){empty(e);return Map.of("items",jobs.list());}
            method(e,"POST");var n=body(e,"action","target","referenceId","requestId","confirmationId");return jobs.submit(text(n,"action"),text(n,"target"),text(n,"referenceId"),text(n,"requestId"),text(n,"confirmationId"),token);
        }
        if(path.matches("/api/v1/ops/jobs/[a-f0-9-]{36}")){method(e,"GET");empty(e);return jobs.get(path.substring(path.lastIndexOf('/')+1));}
        throw new Failure(404,"NOT_FOUND");
    }
    private Map<String,Object> status() throws IOException {
        var result=new LinkedHashMap<>(runner.status());
        var state=store.read();
        var recovery = jobs.recoveryStatus(state);
        result.put("recoveryRequired", recovery.required());
        result.put("recoveryIncident", recovery.incident());
        var schedule=new LinkedHashMap<String,Object>();
        schedule.put("enabled",dailyBackup!=null);
        schedule.put("timeZone",OpsDailyBackup.ZONE.getId());
        schedule.put("time",config.dailyBackupTime()==null?null:config.dailyBackupTime().toString());
        schedule.put("lastScheduledFor",state.path("lastScheduledBackupDate").asString(null));
        result.put("dailyBackup",schedule);
        return result;
    }
    private Object logs(OpsLogReader.Query query) {
        var events = new ArrayList<Object>();
        for (var job : jobs.list()) {
            var phases = job.path("events");
            for (int i = phases.size() - 1; i >= 0 && events.size() < 200; i--) {
                var phase = phases.get(i);
                events.add(Map.of("jobId", job.path("id").asString(), "phase", phase.path("phase").asString(),
                        "occurredAt", phase.path("occurredAt").asString()));
            }
            if (events.size() == 200) break;
        }
        Collections.reverse(events);
        var business = OpsLogReader.read(config.runtimeRoot().resolve(".local-data/logs"), query);
        return Map.of("taskEvents",events,"businessRequests",business.requests(),"businessLogCoverage",business.coverage());
    }
    private void handle(HttpExchange e)throws IOException {
        String trace=UUID.randomUUID().toString();int status=200;String code="OK";Object data=null;
        var headers=e.getResponseHeaders();headers.set("X-Trace-Id",trace);headers.set("Cache-Control","no-store");headers.set("X-Content-Type-Options","nosniff");headers.set("Content-Type","application/json; charset=utf-8");
        try{
            String origin=e.getRequestHeaders().getFirst("Origin");if(origin!=null){if(!origin.equals(config.allowedOrigin()))throw new Failure(403,"ORIGIN_FORBIDDEN");headers.set("Access-Control-Allow-Origin",origin);headers.set("Vary","Origin");}
            if(e.getRequestMethod().equals("OPTIONS")){if(origin==null)throw new Failure(403,"ORIGIN_FORBIDDEN");headers.set("Access-Control-Allow-Methods","GET, POST, OPTIONS");headers.set("Access-Control-Allow-Headers","Authorization, Content-Type");e.sendResponseHeaders(204,-1);e.close();return;}
            data=dispatch(e);
        }catch(Failure failure){status=failure.status;code=failure.code;}
        catch(Exception failure){status=503;code="OPS_SERVICE_UNAVAILABLE";}
        Map<String,Object> result=status<400?Map.of("code",code,"data",data,"traceId",trace):Map.of("code",code,"message","操作未完成，请按错误码和追踪编号检查","traceId",trace);
        byte[] bytes=OpsStore.JSON.writeValueAsBytes(result);e.sendResponseHeaders(status,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}finally{e.close();}
        // Finite fields only; credentials, payload, SQL, paths, and subprocess output are never logged.
        System.out.println("time="+Instant.now()+" event=OPS_REQUEST traceId="+trace+" status="+status+" code="+code);
    }
    public void close()throws IOException {server.stop(1);requests.shutdownNow();if(dailyBackup!=null)dailyBackup.close();jobs.close();store.close();}
    public static void main(String[] args)throws Exception {
        if (args.length == 4 && args[0].equals("--export-backup")) {
            var config = OpsConfig.load(Path.of(args[1]));
            System.out.println(OpsStore.JSON.writeValueAsString(OpsBackupExport.export(config, args[2], Path.of(args[3]))));
            return;
        }
        if (args.length == 3 && args[0].equals("--verify-backup-export")) {
            System.out.println(OpsStore.JSON.writeValueAsString(OpsBackupExport.verify(Path.of(args[1]), args[2])));
            return;
        }
        if(args.length==4&&args[0].equals("--register")){var config=OpsConfig.load(Path.of(args[1]));try(var store=new OpsStore(config.controlRoot())){new OpsRunner(config).register(args[2],Path.of(args[3]));}System.out.println("OPS_ARTIFACT_REGISTERED");return;}
        if(args.length==2&&args[0].equals("--reconcile")){
            var config=OpsConfig.load(Path.of(args[1]));var console=System.console();if(console==null)throw new IOException("OPS_RECONCILE_REQUIRES_CONSOLE");
            try(var store=new OpsStore(config.controlRoot())){
                var status = new LinkedHashMap<>(new OpsRunner(config).status());
                var journal = store.read();
                status.put("recoveryRequired", journal.path("recoveryRequired").asBoolean());
                status.put("recoveryIncident", journal.path("recoveryRequired").asBoolean() ? OpsStore.recoveryIncident(journal,null) : null);
                console.printf("%s%n",OpsStore.JSON.writeValueAsString(status));
                if(!"RECONCILED".equals(console.readLine("After checking DB, media, process and protection backup, type RECONCILED: ")))throw new IOException("OPS_RECONCILE_CANCELLED");
                store.reconcile();}return;
        }
        if(args.length==2&&args[0].equals("--init")){OpsAuth.initialize(Path.of(args[1]));System.out.println("OPS_SETUP_COMPLETED");return;}
        if(args.length!=2||!args[0].equals("--config"))throw new IllegalArgumentException(
                "Use --config <local-json>, --init <control-root>, --export-backup <local-json> <backup-id> <destination>, or --verify-backup-export <copy-directory> <manifest-sha256>");
        var config=OpsConfig.load(Path.of(args[1]));var app=new OpsApplication(config);Runtime.getRuntime().addShutdownHook(new Thread(()->{try{app.close();}catch(IOException ignored){}}));app.start();System.out.println("OPS_READY port="+config.port());
    }
}
