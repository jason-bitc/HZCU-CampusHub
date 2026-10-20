package cn.edu.hzcu.campushub.article;

import cn.edu.hzcu.campushub.auth.*;
import cn.edu.hzcu.campushub.audit.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Actual HTTP/MySQL/files; controlled mail only. No real account or SMTP operations. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoggingModesMySqlIT {
    @TempDir static Path root;
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final HttpClient HTTP=HttpClient.newHttpClient();
    static final AuthTestSupport.ControlledMailer MAIL=new AuthTestSupport.ControlledMailer();
    static final AuthTestSupport.MutableClock CLOCK=new AuthTestSupport.MutableClock();
    static final List<String> secrets=new CopyOnWriteArrayList<>(),emails=new ArrayList<>();
    static final List<Map<String,Object>> coverage=new CopyOnWriteArrayList<>();
    static final List<Long> users=new ArrayList<>();
    static ConfigurableApplicationContext context;
    static ArticleMySqlIT.TcpProxy proxy;
    static JdbcTemplate db;
    static String base,mode,email,password,token,image,commentImage;
    static long user,article,comment;
    static byte[] png;
    static List<Map<String,Object>> originalArticles;
    static long originalAudit,originalComments,originalImages;
    record Reply(int status,JsonNode json,String trace){JsonNode data(){return json.path("data");}}
    static String key(){return UUID.randomUUID().toString();}
    static String privateValue(){String s="b6-private-"+key();secrets.add(s);return s;}
    static Path runtime(){return root.resolve("logs/campushub-runtime.log");}
    static Path diagnostic(){return root.resolve("logs/campushub-diagnostic.log");}
    static void boot(String selected){
        mode=selected;var app=AuthTestSupport.application(MAIL,CLOCK);
        app.addInitializers(ctx->ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("logging-mode-test",Map.of(
            "campushub.run-mode",selected,"campushub.logs.diagnostics-enabled",true,"campushub.logs.enabled",true,"campushub.logs.root",root.resolve("logs").toString(),
            "campushub.media.root",root.resolve("media").toString(),"campushub.db.host","127.0.0.1","campushub.db.port",proxy.port(),"server.tomcat.threads.max",2,"server.tomcat.threads.min-spare",1))));
        context=app.run("--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=demo");
        base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");db=context.getBean(JdbcTemplate.class);
    }
    @BeforeAll static void start()throws Exception{
        proxy=new ArticleMySqlIT.TcpProxy(System.getenv().getOrDefault("CAMPUSHUB_DB_HOST","127.0.0.1"),Integer.parseInt(System.getenv().getOrDefault("CAMPUSHUB_DB_PORT","3306")));
        boot("debug");assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("campushub_test");
        originalArticles=db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes,count_origin FROM article ORDER BY id");originalAudit=count("business_audit");originalComments=count("discussion_comment");originalImages=count("comment_image");
        password=privateValue();email="b6"+key().replace("-","")+"@stu.hzcu.edu.cn";secrets.add(email);emails.add(email);
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(6,6,BufferedImage.TYPE_INT_RGB),"png",bytes);png=bytes.toByteArray();
    }
    static long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    static Reply call(String method,String path,Object body,String auth)throws Exception{
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(25)).header("X-Trace-Id","untrusted-private-input");
        if(auth!=null)request.header("Authorization","Bearer "+auth);if(body!=null)request.header("Content-Type","application/json");
        var response=HTTP.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        var json=JSON.readTree(response.body());String trace=json.path("traceId").asString();assertThat(response.headers().firstValue("X-Trace-Id")).contains(trace);
        if(response.statusCode()>=400)for(String secret:secrets)assertThat(response.body()).doesNotContain(secret);
        recorded(method,path,response.statusCode(),trace);return new Reply(response.statusCode(),json,trace);
    }
    static void recorded(String method,String path,int status,String trace)throws Exception{
        String route=path.split("\\?")[0].replaceAll("/[0-9]+(?=/|$)","/{id}").replaceAll("/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}","/{imageId}");
        String action=cn.edu.hzcu.campushub.common.RequestTrace.action(path.split("\\?")[0],method);
        // Tomcat can flush the binary response before the outer filter's synchronous final event.
        for(int attempt=0;attempt<100&&!Files.readString(runtime()).contains("traceId="+trace+" event=REQUEST");attempt++)Thread.sleep(10);
        assertThat(Files.readString(runtime()).lines().filter(l->l.contains(trace)&&l.contains("event=REQUEST")).toList()).anySatisfy(l->assertThat(l).contains("action="+action,"status="+status));
        coverage.add(Map.of("mode",mode,"method",method,"route",route,"action",action,"status",status,"traceId",trace));
    }
    static Reply get(String path)throws Exception{return call("GET",path,null,token);}
    static Reply login()throws Exception{var r=call("POST","/api/v1/auth/login",Map.of("email",email,"password",password),null);if(r.status==200){token=r.data().path("accessToken").asString();secrets.add(token);}return r;}
    static String upload(String path)throws Exception{
        String boundary="bound-"+key();var bytes=new ByteArrayOutputStream();bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n"+key()+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));bytes.write(png);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var response=HTTP.send(HttpRequest.newBuilder(URI.create(base+path)).header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        var json=JSON.readTree(response.body());assertThat(response.statusCode()).isEqualTo(200);recorded("POST",path,200,json.path("traceId").asString());return json.path("data").path("imageId").asString();
    }
    static HttpResponse<byte[]> binary(String path)throws Exception{
        var response=HTTP.send(HttpRequest.newBuilder(URI.create(base+path)).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        recorded("GET",path,response.statusCode(),response.headers().firstValue("X-Trace-Id").orElseThrow());return response;
    }
    static Map<String,Object> draft(long revision){
        var body=new LinkedHashMap<String,Object>();body.put("expectedRevision",revision);body.put("title",privateValue());body.put("summary",privateValue());body.put("body",privateValue()+"\n![](/api/v1/media/images/"+image+")");body.put("jobIds",List.of(1));body.put("majorIds",List.of(11));body.put("companyIds",List.of());body.put("topicIds",List.of());body.put("experienceStage","DEMO");body.put("sourceType","DEMO");body.put("sourceNote",privateValue());body.put("sourceLinks",List.of());return body;
    }
    static Map<String,Object> profile(long revision){return Map.of("expectedRevision",revision,"nickname","日志测试同学","majorId",11,"currentStage","ENROLLED","bio",privateValue());}
    static Map<String,Object> contact(long revision){return Map.of("expectedRevision",revision,"enabled",true,"type","WECHAT","value",privateValue());}
    static Map<String,Object> comment(String request,Long reply){var b=new LinkedHashMap<String,Object>();b.put("requestId",request);b.put("body",privateValue());b.put("imageIds",List.of());b.put("replyToCommentId",reply);return b;}
    @Test @Order(1) void everyExistingRouteHasAnActualSafeFileEvent()throws Exception{
        assertThat(get("/actuator/health").status).isEqualTo(200);assertThat(get("/api/v1/catalog").status).isEqualTo(200);assertThat(get("/api/v1/articles?keyword="+URLEncoder.encode(privateValue(),StandardCharsets.UTF_8)).status).isEqualTo(200);assertThat(get("/api/v1/articles/1001").status).isEqualTo(200);
        var challenge=call("POST","/api/v1/auth/registration-codes",Map.of("email",email),null);String code=MAIL.code(email,"REGISTER");secrets.add(code);
        var registered=call("POST","/api/v1/auth/register",Map.of("email",email,"password",password,"challengeId",challenge.data().path("challengeId").asString(),"code",code),null);assertThat(registered.status).isEqualTo(201);user=registered.data().path("userId").asLong();users.add(user);assertThat(login().status).isEqualTo(200);
        assertThat(get("/api/v1/me").status).isEqualTo(200);assertThat(get("/api/v1/me/profile").status).isEqualTo(200);assertThat(call("PUT","/api/v1/me/profile",profile(0),token).status).isEqualTo(200);
        article=call("POST","/api/v1/me/articles",Map.of("requestId",key()),token).data().path("articleId").asLong();assertThat(get("/api/v1/me/articles").status).isEqualTo(200);assertThat(get("/api/v1/me/articles/"+article+"/draft").status).isEqualTo(200);
        image=upload("/api/v1/me/articles/"+article+"/images");assertThat(call("PUT","/api/v1/me/articles/"+article+"/draft",draft(0),token).status).isEqualTo(200);
        var publish=Map.of("expectedRevision",1,"requestId",key());assertThat(call("POST","/api/v1/me/articles/"+article+"/publish",publish,token).status).isEqualTo(200);assertThat(call("POST","/api/v1/me/articles/"+article+"/publish",publish,token).status).isEqualTo(200);
        assertThat(binary("/api/v1/media/images/"+image).body()).isEqualTo(png);assertThat(get("/api/v1/articles/"+article+"/author").status).isEqualTo(200);assertThat(get("/api/v1/authors/"+user).status).isEqualTo(200);assertThat(get("/api/v1/authors/"+user+"/articles").status).isEqualTo(200);assertThat(get("/api/v1/me/contact-settings").status).isEqualTo(200);assertThat(call("PUT","/api/v1/me/contact-settings",contact(0),token).status).isEqualTo(200);assertThat(get("/api/v1/authors/"+user+"/contact").status).isEqualTo(200);
        commentImage=upload("/api/v1/articles/"+article+"/comment-images");var c=comment(key(),null);c.put("imageIds",List.of(commentImage));comment=call("POST","/api/v1/articles/"+article+"/comments",c,token).data().path("commentId").asLong();assertThat(get("/api/v1/articles/"+article+"/comments").status).isEqualTo(200);assertThat(binary("/api/v1/media/comment-images/"+commentImage).body()).isEqualTo(png);
        long child=call("POST","/api/v1/articles/"+article+"/comments",comment(key(),comment),token).data().path("commentId").asLong();assertThat(get("/api/v1/comments/"+comment+"/replies").status).isEqualTo(200);assertThat(call("DELETE","/api/v1/comments/"+child,null,token).status).isEqualTo(200);
        assertThat(call("POST","/api/v1/me/articles/"+article+"/unpublish",Map.of("expectedRevision",2),token).status).isEqualTo(200);assertThat(call("POST","/api/v1/me/articles/"+article+"/restore",Map.of("expectedRevision",3),token).status).isEqualTo(200);
        long disposable=call("POST","/api/v1/me/articles",Map.of("requestId",key()),token).data().path("articleId").asLong();assertThat(call("DELETE","/api/v1/me/articles/"+disposable+"?expectedRevision=0",null,token).status).isEqualTo(200);
        assertThat(call("POST","/api/v1/auth/logout",null,token).status).isEqualTo(200);assertThat(login().status).isEqualTo(200);
        var reset=call("POST","/api/v1/auth/password-reset-codes",Map.of("email",email),null);String resetCode=MAIL.code(email,"PASSWORD_RESET");secrets.add(resetCode);password=privateValue();assertThat(call("POST","/api/v1/auth/password-resets",Map.of("email",email,"password",password,"challengeId",reset.data().path("challengeId").asString(),"code",resetCode),null).status).isEqualTo(200);assertThat(login().status).isEqualTo(200);
        assertThat(coverage.stream().map(r->r.get("action")).distinct().count()).isEqualTo(35);
    }
    @Test @Order(2) void expectedFailuresHaveCodesPhasesAndValidatedContextOnly()throws Exception{
        var unauthorized=call("GET","/api/v1/me/articles/"+article+"/draft",null,null);assertThat(unauthorized.status).isEqualTo(401);
        assertThat(get("/api/v1/articles?size=101").status).isEqualTo(400);var missing=get("/api/v1/articles/999999");assertThat(missing.status).isEqualTo(404);
        var conflict=call("PUT","/api/v1/me/profile",profile(0),token);assertThat(conflict.status).isEqualTo(409);assertThat(requestLine(conflict.trace)).contains("expectedRevision=0","actualRevision=1");
        assertThat(call("PUT","/api/v1/me/contact-settings",contact(0),token).status).isEqualTo(409);assertThat(call("PUT","/api/v1/me/articles/"+article+"/draft",draft(0),token).status).isEqualTo(409);
        assertThat(get("/api/v1/authors/999999").status).isEqualTo(404);assertThat(get("/api/v1/comments/999999/replies").status).isEqualTo(404);assertThat(binary("/api/v1/media/images/"+key()).statusCode()).isEqualTo(404);
        var rate=call("POST","/api/v1/auth/registration-codes",Map.of("email",email),null);assertThat(rate.status).isEqualTo(429);assertThat(requestLine(rate.trace)).contains("code=RATE_LIMITED","stage=PERSIST");
        assertThat(requestLine(missing.trace)).doesNotContain("articleId=999999");assertThat(requestLine(unauthorized.trace)).doesNotContain("actorId=","articleId=");
    }
    static String requestLine(String trace)throws Exception{return Files.readString(runtime()).lines().filter(s->s.contains(trace)&&s.contains("event=REQUEST")).findFirst().orElseThrow();}
    interface Checked {Reply run()throws Exception;}
    static Reply fault(String table,String timing,String condition,Checked operation)throws Exception{
        String marker=privateValue();db.execute("CREATE TRIGGER b06_logging_failure BEFORE "+timing+" ON "+table+" FOR EACH ROW BEGIN IF "+condition+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='"+marker+"'; END IF; END");
        try{return operation.run();}finally{db.execute("DROP TRIGGER b06_logging_failure");}
    }
    static void diagnosed(Reply reply,String location)throws Exception{
        assertThat(reply.status).isEqualTo(503);assertThat(Files.readString(diagnostic()).lines().filter(s->s.contains(reply.trace)).toList()).anySatisfy(s->assertThat(s).contains(location,"code="+reply.json.path("code").asString()));
    }
    @Test @Order(3) void oldModuleFailuresHaveSameBusinessResultsInDebugAndRelease()throws Exception{
        var contracts=new LinkedHashMap<String,List<String>>();
        for(String selected:List.of("debug","release")){
            if(!mode.equals(selected)){context.close();boot(selected);}
            long diagBefore=Files.size(diagnostic()),debugBefore=Files.readString(runtime()).lines().filter(s->s.contains("event=DEBUG_STAGE")).count();var results=new ArrayList<String>();
            String failedMail="b6"+key().replace("-","")+"@stu.hzcu.edu.cn";emails.add(failedMail);secrets.add(failedMail);MAIL.fail=true;Reply mail;
            try{mail=call("POST","/api/v1/auth/registration-codes",Map.of("email",failedMail),null);}finally{MAIL.fail=false;}
            Reply login=fault("auth_session","INSERT","NEW.user_id="+user,()->call("POST","/api/v1/auth/login",Map.of("email",email,"password",password),null));
            proxy.cut();Reply reading;try{reading=get("/api/v1/articles");}finally{proxy.restore();}assertThat(get("/api/v1/catalog").status).isEqualTo(200);
            long audits=count("business_audit");Reply profile=fault("business_audit","INSERT","NEW.actor_id="+user,()->call("PUT","/api/v1/me/profile",profile(1),token));assertThat(count("business_audit")).isEqualTo(audits);assertThat(get("/api/v1/me/profile").data().path("revision").asLong()).isEqualTo(1);
            Reply writing=fault("article_draft","UPDATE","NEW.article_id="+article,()->call("PUT","/api/v1/me/articles/"+article+"/draft",draft(4),token));assertThat(get("/api/v1/me/articles/"+article+"/draft").data().path("revision").asLong()).isEqualTo(4);
            Reply contact=fault("author_contact_setting","INSERT","NEW.user_id="+user,()->call("PUT","/api/v1/me/contact-settings",contact(1),token));
            Path object=root.resolve("media/objects/"+image);Files.writeString(object,privateValue());Reply storage;
            try{var response=binary("/api/v1/media/images/"+image);storage=new Reply(response.statusCode(),JSON.readTree(response.body()),response.headers().firstValue("X-Trace-Id").orElseThrow());}finally{Files.write(object,png);}
            Reply comment=fault("business_audit","INSERT","NEW.actor_id="+user,()->call("POST","/api/v1/articles/"+article+"/comments",comment(key(),null),token));
            for(Reply reply:List.of(mail,login,reading,profile,writing,contact,storage,comment)){assertThat(reply.status).isEqualTo(503);results.add(reply.status+":"+reply.json.path("code").asString());}
            contracts.put(selected,results);
            if(selected.equals("debug")){diagnosed(mail,"AuthService.java:");diagnosed(login,"AuthRepository.java:");diagnosed(reading,"ArticleController.java:");diagnosed(profile,"BusinessAudit.java:");diagnosed(writing,"WritingRepository.java:");diagnosed(contact,"AuthorRepository.java:");diagnosed(storage,"MediaStore.java:");diagnosed(comment,"CommentService.java:");}
            else{assertThat(Files.size(diagnostic())).isEqualTo(diagBefore);assertThat(Files.readString(runtime()).lines().filter(s->s.contains("event=DEBUG_STAGE")).count()).isEqualTo(debugBefore);}
        }
        assertThat(contracts.get("release")).isEqualTo(contracts.get("debug"));Files.writeString(Path.of("target/batch6-mode-contracts.json"),JSON.writeValueAsString(contracts));
    }
    @Test @Order(4) void committedAuditAndConcurrentRequestsKeepTheirOwnTrace()throws Exception{
        context.close();boot("debug");long before=count("business_audit");var body=comment(key(),null);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var created=pool.invokeAll(List.<Callable<Reply>>of(()->call("POST","/api/v1/articles/"+article+"/comments",body,token),()->call("POST","/api/v1/articles/"+article+"/comments",body,token)));assertThat(created.get(0).get().data()).isEqualTo(created.get(1).get().data());
            var tasks=new ArrayList<Callable<Reply>>();for(int i=0;i<24;i++){final boolean own=i%2==0;tasks.add(()->get(own?"/api/v1/me/articles/"+article+"/draft":"/api/v1/catalog"));}
            var replies=pool.invokeAll(tasks);var traces=new HashSet<String>();
            for(var future:replies){var r=future.get();assertThat(traces.add(r.trace)).isTrue();String line=requestLine(r.trace);if(line.contains("action=CATALOG"))assertThat(line).doesNotContain("articleId=","actorId=");else assertThat(line).contains("articleId="+article,"actorId="+user);}
        }
        assertThat(count("business_audit")).isEqualTo(before+1);
        // Select the actual new comment audit by its request key rather than ordering equal test-clock timestamps.
        String trace=db.queryForObject("SELECT a.trace_id FROM business_audit a JOIN comment_create_receipt r ON r.comment_id=a.object_id WHERE r.user_id=? AND r.request_id=? AND a.action='COMMENT_CREATE'",String.class,user,body.get("requestId"));
        assertThat(Files.readString(runtime()).lines().filter(s->s.contains(trace)).toList()).anySatisfy(s->assertThat(s).contains("event=BUSINESS_CHANGE","action=COMMENT_CREATE"));assertThat(Files.readString(runtime()).lines().filter(s->s.contains(trace)).toList()).anySatisfy(s->assertThat(s).contains("stage=COMMIT"));
        assertThat(Files.readString(runtime())).contains("stage=ROLLBACK","event=BUSINESS_NO_CHANGE");
    }
    @Test @Order(5) void separateFileFaultsRemainObservableAndRecoverWithoutBusinessChanges()throws Exception{
        var logs=context.getBean(SafeFileLogs.class);Path backup=root.resolve("own-runtime-backup");Files.move(runtime(),backup);Files.createDirectory(runtime());
        try{var response=HTTP.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/catalog")).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);}
        finally{assertThat(logs.healthy()).isFalse();Files.delete(runtime());Files.move(backup,runtime());}
        assertThat(get("/api/v1/catalog").status).isEqualTo(200);assertThat(logs.healthy()).isTrue();
        Path diagBackup=root.resolve("own-diagnostic-backup");Files.move(diagnostic(),diagBackup);Files.createDirectory(diagnostic());
        try{Reply failed=fault("auth_session","INSERT","NEW.user_id="+user,()->call("POST","/api/v1/auth/login",Map.of("email",email,"password",password),null));assertThat(failed.status).isEqualTo(503);assertThat(logs.healthy()).isFalse();}
        finally{Files.delete(diagnostic());Files.move(diagBackup,diagnostic());}
        diagnosed(fault("auth_session","INSERT","NEW.user_id="+user,()->call("POST","/api/v1/auth/login",Map.of("email",email,"password",password),null)),"AuthRepository.java:");assertThat(logs.healthy()).isTrue();assertThat(logs.failedWrites()).isGreaterThanOrEqualTo(2);
    }
    @Test @Order(6) void actualJarScriptsModePriorityAndEnvironmentRestoration()throws Exception{
        ScriptModeProbe.verify(root.resolve("scripts"));
    }
    @Test @Order(7) void releaseRetainsHistoryAndExportsOnlyScannedEvidence()throws Exception{
        String oldDiagnostic=Files.readString(diagnostic());long debugLines=Files.readString(runtime()).lines().filter(s->s.contains("event=DEBUG_STAGE")).count();context.close();boot("release");
        assertThat(get("/api/v1/articles/"+article).status).isEqualTo(200);assertThat(get("/api/v1/me").status).isEqualTo(200);assertThat(Files.readString(diagnostic())).isEqualTo(oldDiagnostic);assertThat(Files.readString(runtime()).lines().filter(s->s.contains("event=DEBUG_STAGE")).count()).isEqualTo(debugLines);
        String runtime=Files.readString(runtime()),diagnostic=Files.readString(diagnostic());for(String secret:secrets){assertThat(runtime).doesNotContain(secret);assertThat(diagnostic).doesNotContain(secret);}assertThat(runtime).contains("mode=release","mode=debug","legacyDiagnosticsIgnored=true");
        Files.writeString(Path.of("target/batch6-runtime-sample.txt"),runtime);Files.writeString(Path.of("target/batch6-diagnostic-sample.txt"),diagnostic);Files.writeString(Path.of("target/batch6-route-coverage.json"),JSON.writeValueAsString(coverage));
    }
    @AfterAll static void cleanup()throws Exception{
        try{if(context!=null){
            var named=context.getBean(NamedParameterJdbcTemplate.class);var p=new MapSqlParameterSource("users",users).addValue("emails",emails);
            if(!users.isEmpty()){
                named.update("DELETE FROM business_audit WHERE actor_id IN (:users)",p);named.update("DELETE FROM comment_create_receipt WHERE user_id IN (:users)",p);named.update("DELETE FROM comment_image WHERE user_id IN (:users)",p);
                for(long id:named.queryForList("SELECT id FROM discussion_comment WHERE user_id IN (:users) ORDER BY id DESC",p,Long.class))db.update("DELETE FROM discussion_comment WHERE id=?",id);
                var articles=named.queryForList("SELECT id FROM article WHERE owner_user_id IN (:users)",p,Long.class);if(!articles.isEmpty()){p.addValue("articles",articles);
                for(String table:List.of("article_publish_request","article_version_image","article_draft_image","article_draft_category","article_draft_source_link","author_create_request","media_image","article_draft"))named.update("DELETE FROM "+table+" WHERE article_id IN (:articles)",p);
                named.update("UPDATE article SET public_version_id=NULL,state='DELETED' WHERE id IN (:articles)",p);named.update("DELETE FROM article_version_category WHERE version_id IN (SELECT id FROM article_version WHERE article_id IN (:articles))",p);named.update("DELETE FROM article_source_link WHERE version_id IN (SELECT id FROM article_version WHERE article_id IN (:articles))",p);named.update("DELETE FROM article_version WHERE article_id IN (:articles)",p);named.update("DELETE FROM article WHERE id IN (:articles)",p);}
                for(String table:List.of("author_contact_setting","author_profile","auth_session","campus_certification"))named.update("DELETE FROM "+table+" WHERE user_id IN (:users)",p);
            }
            if(!emails.isEmpty()){named.update("DELETE FROM email_challenge WHERE email IN (:emails)",p);named.update("DELETE FROM auth_email_gate WHERE email IN (:emails)",p);}
            if(!users.isEmpty())named.update("DELETE FROM platform_user WHERE id IN (:users)",p);
            assertThat(db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes,count_origin FROM article ORDER BY id")).isEqualTo(originalArticles);assertThat(count("business_audit")).isEqualTo(originalAudit);assertThat(count("discussion_comment")).isEqualTo(originalComments);assertThat(count("comment_image")).isEqualTo(originalImages);
        }}finally{if(context!=null)context.close();if(proxy!=null)proxy.close();}
    }
}
