package cn.edu.hzcu.ops;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.*;

final class OpsJobs implements AutoCloseable {
    interface Executor {Map<String,Object> execute(String action,String reference,Consumer<String> phase)throws Exception;}
    private record Confirmation(String digest,String session,Instant expires){}
    private final Map<String,Confirmation> confirmations=new HashMap<>();
    private final OpsStore store;
    private final Executor executor;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean unsafe;
    OpsJobs(OpsStore store,Executor executor){this.store=store;this.executor=executor;}
    private static String digest(String action,String target,String reference){validate(action,target,reference);return OpsAuth.digest(action+"\n"+target+"\n"+reference);}
    static void validate(String action,String target,String reference){
        if(!Set.of("DEPLOY","RESTART","BACKUP","RESTORE").contains(action)||!target.equals("business"))throw new OpsApplication.Failure(400,"INVALID_PARAMETER");
        if((Set.of("RESTART","BACKUP").contains(action)&&!reference.isEmpty())||(!reference.isEmpty()&&!reference.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}"))||(Set.of("DEPLOY","RESTORE").contains(action)&&reference.isEmpty()))throw new OpsApplication.Failure(400,"INVALID_PARAMETER");
    }
    synchronized Map<String,Object> prepare(String action,String target,String reference,String token) {
        String digest=digest(action,target,reference);Instant now=Instant.now();confirmations.entrySet().removeIf(e->now.isAfter(e.getValue().expires));
        if(confirmations.size()>=100)throw new OpsApplication.Failure(429,"OPS_CONFIRMATION_LIMIT");
        String id=UUID.randomUUID().toString();Instant expiry=now.plusSeconds(300);confirmations.put(id,new Confirmation(digest,OpsAuth.digest(token),expiry));
        return Map.of("confirmationId",id,"expiresAt",expiry.toString(),"action",action,"target",target,"referenceId",reference,"impact",action.equals("RESTORE")?"覆盖业务数据库和图片；执行前保存保护备份":"业务服务可能短暂停止；运维查询保持可用");
    }
    synchronized JsonNode submit(String action,String target,String reference,String request,String confirmation,String token)throws IOException {
        if(!request.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))throw new OpsApplication.Failure(400,"INVALID_PARAMETER");
        String digest=digest(action,target,reference);var state=store.read();
        for(var job:state.path("jobs"))if(job.path("requestId").asString().equals(request)){
            if(!job.path("digest").asString().equals(digest))throw new OpsApplication.Failure(409,"REQUEST_KEY_REUSED");return publicJob(job);
        }
        if(unsafe||state.path("recoveryRequired").asBoolean())throw new OpsApplication.Failure(409,"OPS_RECOVERY_REQUIRED");
        for(var job:state.path("jobs"))if(Set.of("QUEUED","RUNNING").contains(job.path("state").asString()))throw new OpsApplication.Failure(409,"OPS_TARGET_BUSY");
        var prepared=confirmations.get(confirmation);
        if(prepared==null||!Instant.now().isBefore(prepared.expires)||!prepared.digest.equals(digest)||!prepared.session.equals(OpsAuth.digest(token)))throw new OpsApplication.Failure(409,"OPS_CONFIRMATION_INVALID");
        String id=UUID.randomUUID().toString();var job=OpsStore.JSON.createObjectNode();job.put("id",id).put("requestId",request).put("digest",digest).put("action",action).put("target",target).put("referenceId",reference).put("state","QUEUED").put("createdAt",Instant.now().toString()).put("actorDomain","OPS_ADMIN");job.set("events",OpsStore.JSON.createArrayNode());
        ((ArrayNode)state.get("jobs")).add(job);store.save(state);confirmations.remove(confirmation);
        worker.submit(()->run(id,action,reference));return publicJob(job);
    }
    private void update(String id,Consumer<ObjectNode> update)throws IOException {store.change(state->{for(var job:state.path("jobs"))if(job.path("id").asString().equals(id))update.accept((ObjectNode)job);});}
    private void phase(String id,String code){
        if(!code.matches("[A-Z_]{2,48}"))throw new IllegalArgumentException("Unsafe event code");
        try{update(id,job->{var event=OpsStore.JSON.createObjectNode();event.put("phase",code).put("occurredAt",Instant.now().toString());((ArrayNode)job.get("events")).add(event);});}
        catch(IOException e){unsafe=true;throw new java.io.UncheckedIOException(e);}
    }
    private void run(String id,String action,String reference) {
        try{update(id,n->n.put("state","RUNNING").put("startedAt",Instant.now().toString()));
            Map<String,Object> result=executor.execute(action,reference,code->phase(id,code));
            update(id,n->{n.put("state","SUCCEEDED").put("finishedAt",Instant.now().toString());n.set("result",OpsStore.JSON.valueToTree(result));});
        }catch(Exception e){
            String code=e instanceof OpsApplication.Failure f?f.code:e instanceof IOException?"OPS_IO_FAILED":"OPS_EXECUTION_FAILED";
            boolean recovery=unsafe||(e instanceof OpsApplication.Failure f&&f.code.equals("OPS_MANUAL_RECOVERY_REQUIRED"));
            try{update(id,n->n.put("state","FAILED").put("errorCode",code).put("finishedAt",Instant.now().toString()));if(recovery)store.change(n->n.put("recoveryRequired",true));}
            catch(IOException failed){unsafe=true;}
        }
    }
    JsonNode list(){var out=OpsStore.JSON.createArrayNode();var jobs=store.read().path("jobs");for(int i=jobs.size()-1;i>=0&&out.size()<100;i--)out.add(publicJob(jobs.get(i)));return out;}
    JsonNode get(String id){for(var job:store.read().path("jobs"))if(job.path("id").asString().equals(id))return publicJob(job);throw new OpsApplication.Failure(404,"NOT_FOUND");}
    private static ObjectNode publicJob(JsonNode job){var out=((ObjectNode)job).deepCopy();out.remove("digest");return out;}
    public void close(){worker.shutdown();try{if(!worker.awaitTermination(5,TimeUnit.SECONDS)){worker.shutdownNow();if(!worker.awaitTermination(10,TimeUnit.SECONDS))throw new IllegalStateException("OPS_EXECUTOR_STILL_STOPPING");}}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("OPS_EXECUTOR_STILL_STOPPING");}}
}
