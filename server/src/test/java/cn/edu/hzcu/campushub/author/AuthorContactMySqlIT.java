package cn.edu.hzcu.campushub.author;

import cn.edu.hzcu.campushub.auth.*;
import cn.edu.hzcu.campushub.audit.SafeFileLogs;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthorContactMySqlIT {
    @TempDir static Path root;
    private static final AuthTestSupport.ControlledMailer MAIL=new AuthTestSupport.ControlledMailer();
    private static final AuthTestSupport.MutableClock CLOCK=new AuthTestSupport.MutableClock();
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final HttpClient HTTP=HttpClient.newHttpClient();
    private static ConfigurableApplicationContext context;
    private static JdbcTemplate db;
    private static String base,writerToken,readerToken,otherToken,writerEmail,readerEmail,otherEmail,password;
    private static long writer,reader,other,articleA,articleB;
    private static List<Map<String,Object>> baseline;
    private static long auditBaseline,contactBaseline;
    private static final List<String> secrets=new ArrayList<>();
    private static final List<String> emails=new ArrayList<>();
    private static final List<Long> users=new ArrayList<>();
    private static String contactValue;
    record Reply(int status,JsonNode data,String trace,String raw){}
    @BeforeAll static void start()throws Exception{
        boot();db=context.getBean(JdbcTemplate.class);assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("campushub_test");
        baseline=db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes FROM article ORDER BY id");auditBaseline=db.queryForObject("SELECT COUNT(*) FROM business_audit",Long.class);contactBaseline=db.queryForObject("SELECT COUNT(*) FROM author_contact_setting",Long.class);
        password=UUID.randomUUID().toString();secrets.add(password);
        secrets.addAll(List.of("reader-private","更新简介","should-not-save"));
        writerEmail=email();readerEmail=email();otherEmail=email();
        writer=register(writerEmail);reader=register(readerEmail);other=register(otherEmail);
        writerToken=login(writerEmail);readerToken=login(readerEmail);otherToken=login(otherEmail);
        contactValue="contact-private-"+UUID.randomUUID();secrets.add(contactValue);
    }
    static void boot(){
        var app=AuthTestSupport.application(MAIL,CLOCK);
        app.addInitializers(ctx->ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("author-contact-test",Map.of("campushub.media.root",root.resolve("media").toString(),"campushub.logs.enabled","true","campushub.logs.root",root.resolve("logs").toString()))));
        context=app.run("--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=demo");base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");
    }
    static String email(){String email="b4"+UUID.randomUUID().toString().replace("-","")+"@stu.hzcu.edu.cn";emails.add(email);secrets.add(email);return email;}
    static long register(String email){var auth=context.getBean(AuthService.class);var challenge=auth.requestCode(email,AuthModels.Purpose.REGISTER);String code=MAIL.code(email,"REGISTER");secrets.add(code);long id=auth.register(email,password,challenge.challengeId(),code).userId();users.add(id);return id;}
    static String login(String email){String token=context.getBean(AuthService.class).login(email,password).accessToken();secrets.add(token);return token;}
    static String key(){return UUID.randomUUID().toString();}
    static Reply call(String method,String path,Object body,String token)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20)).header("X-Trace-Id","untrusted-supplied-trace");
        if(token!=null)builder.header("Authorization","Bearer "+token);if(body!=null)builder.header("Content-Type","application/json");
        var response=HTTP.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body instanceof String s?s:JSON.writeValueAsString(body),StandardCharsets.UTF_8)).build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var json=JSON.readTree(response.body());String trace=json.path("traceId").asString();assertThat(trace).matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");assertThat(response.headers().firstValue("X-Trace-Id")).contains(trace);
        if(path.startsWith("/api/v1/authors/")||path.startsWith("/api/v1/me/")||path.matches("/api/v1/articles/[^/]+/author.*"))assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        if(response.statusCode()>=400){assertThat(json.has("data")).isFalse();for(String secret:secrets)assertThat(response.body().contains(secret)).isFalse();}
        return new Reply(response.statusCode(),json.path("data"),trace,response.body());
    }
    static Reply get(String path,String token)throws Exception{return call("GET",path,null,token);}
    static Map<String,Object> setting(long revision,boolean enabled,Object type,Object value){var body=new LinkedHashMap<String,Object>();body.put("expectedRevision",revision);body.put("enabled",enabled);body.put("type",type);body.put("value",value);return body;}
    static Reply set(long revision,boolean enabled,Object type,Object value,String token)throws Exception{return call("PUT","/api/v1/me/contact-settings",setting(revision,enabled,type,value),token);}
    static long contactRevision()throws Exception{return get("/api/v1/me/contact-settings",writerToken).data.path("revision").asLong();}
    static long revision(long id)throws Exception{return get("/api/v1/me/articles/"+id+"/draft",writerToken).data.path("revision").asLong();}
    static long create(String token)throws Exception{var r=call("POST","/api/v1/me/articles",Map.of("requestId",key()),token);assertThat(r.status).isEqualTo(201);return r.data.path("articleId").asLong();}
    static Map<String,Object> draft(long rev){var body=new LinkedHashMap<String,Object>();body.put("expectedRevision",rev);body.put("title","【DEMO】第四阶段接口测试");body.put("summary","");String text="body-private-marker-"+key();secrets.add(text);body.put("body",text);body.put("jobIds",List.of(1));body.put("majorIds",List.of(11));body.put("companyIds",List.of());body.put("topicIds",List.of());body.put("experienceStage","DEMO演示阶段");body.put("sourceType","DEMO");body.put("sourceNote","后端测试示例");body.put("sourceLinks",List.of());return body;}
    static long publishNew(String token)throws Exception{long id=create(token);assertThat(call("PUT","/api/v1/me/articles/"+id+"/draft",draft(0),token).status).isEqualTo(200);assertThat(call("POST","/api/v1/me/articles/"+id+"/publish",Map.of("expectedRevision",1,"requestId",key()),token).status).isEqualTo(200);return id;}
    static Reply state(long id,long rev,String action)throws Exception{return call("POST","/api/v1/me/articles/"+id+"/"+action,Map.of("expectedRevision",rev),writerToken);}
    static long auditCount(){return db.queryForObject("SELECT COUNT(*) FROM business_audit",Long.class);}
    static void audit(Reply response,String action,long object,long before,long after){var rows=db.queryForList("SELECT * FROM business_audit WHERE trace_id=?",response.trace);assertThat(rows).hasSize(1);var row=rows.getFirst();assertThat(row.get("actor_id")).isEqualTo(writer);assertThat(row.get("action")).isEqualTo(action);assertThat(row.get("object_id")).isEqualTo(object);assertThat(row.get("before_revision")).isEqualTo(before);assertThat(row.get("after_revision")).isEqualTo(after);assertThat(row.get("result")).isEqualTo("SUCCESS");}
    static Map<String,Object> profile(long revision,String nickname){var body=new LinkedHashMap<String,Object>();body.put("expectedRevision",revision);body.put("nickname",nickname);body.put("majorId",11);body.put("currentStage","ENROLLED");body.put("bio","");return body;}
    @AfterAll static void cleanup(){
        if(context==null)return;
        try{
            var jdbc=context.getBean(NamedParameterJdbcTemplate.class);var p=new MapSqlParameterSource("users",users).addValue("emails",emails);
            if(!users.isEmpty()){
                jdbc.update("DELETE FROM business_audit WHERE actor_id IN (:users)",p);jdbc.update("DELETE FROM author_contact_setting WHERE user_id IN (:users)",p);
                List<Long> ids=jdbc.queryForList("SELECT id FROM article WHERE owner_user_id IN (:users)",p,Long.class);
                if(!ids.isEmpty()){
                    p.addValue("articles",ids);
                    for(String table:List.of("article_publish_request","article_version_image","article_draft_image","article_draft_category","article_draft_source_link","author_create_request","media_image","article_draft"))jdbc.update("DELETE FROM "+table+" WHERE article_id IN (:articles)",p);
                    jdbc.update("UPDATE article SET state='DELETED',public_version_id=NULL WHERE id IN (:articles)",p);
                    jdbc.update("DELETE FROM article_version_category WHERE version_id IN(SELECT id FROM article_version WHERE article_id IN (:articles))",p);jdbc.update("DELETE FROM article_source_link WHERE version_id IN(SELECT id FROM article_version WHERE article_id IN (:articles))",p);jdbc.update("DELETE FROM article_version WHERE article_id IN (:articles)",p);jdbc.update("DELETE FROM article WHERE id IN (:articles)",p);
                }
                for(String table:List.of("author_profile","auth_session","campus_certification"))jdbc.update("DELETE FROM "+table+" WHERE user_id IN (:users)",p);
                jdbc.update("DELETE FROM email_challenge WHERE email IN (:emails)",p);jdbc.update("DELETE FROM auth_email_gate WHERE email IN (:emails)",p);jdbc.update("DELETE FROM platform_user WHERE id IN (:users)",p);
            }
            assertThat(db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes FROM article ORDER BY id")).isEqualTo(baseline);assertThat(auditCount()).isEqualTo(auditBaseline);assertThat(db.queryForObject("SELECT COUNT(*) FROM author_contact_setting",Long.class)).isEqualTo(contactBaseline);
        }finally{context.close();}
    }

    @Test @Order(1) void restrictedRoutesCheckReaderBeforeTargetAndNeverCacheFailures()throws Exception{
        for(String path:List.of("/api/v1/articles/1001/author","/api/v1/articles/not-an-id/author","/api/v1/authors/999999","/api/v1/authors/999999/articles","/api/v1/authors/999999/contact","/api/v1/me/contact-settings")){
            assertThat(get(path,null).status).isEqualTo(401);assertThat(get(path,"invalid").status).isEqualTo(401);
        }
        assertThat(get("/api/v1/articles/1001/author",readerToken).status).isEqualTo(404);assertThat(get("/api/v1/articles/not-an-id/author",readerToken).status).isEqualTo(400);assertThat(get("/api/v1/authors/999999",readerToken).status).isEqualTo(404);
        assertThat(get("/api/v1/articles/1001",null).status).isEqualTo(200);assertThat(db.queryForObject("SELECT COUNT(*) FROM author_contact_setting WHERE user_id=?",Long.class,writer)).isZero();
    }
    @Test @Order(2) void realAuthorEntryWhitelistedHomeAndOnlyOwnCurrentPublicArticles()throws Exception{
        articleA=publishNew(writerToken);CLOCK.advance(Duration.ofMinutes(1));articleB=publishNew(writerToken);publishNew(otherToken);create(writerToken);
        long offline=publishNew(writerToken);state(offline,2,"unpublish");long hidden=publishNew(writerToken);db.update("UPDATE article SET state='ADMIN_HIDDEN' WHERE id=?",hidden);long deleted=publishNew(writerToken);assertThat(call("DELETE","/api/v1/me/articles/"+deleted+"?expectedRevision=2",null,writerToken).status).isEqualTo(200);
        var entry=get("/api/v1/articles/"+articleA+"/author",readerToken);assertThat(entry.status).isEqualTo(200);assertThat(entry.data.size()).isEqualTo(2);assertThat(entry.data.path("authorId").asLong()).isEqualTo(writer);
        var home=get("/api/v1/authors/"+writer,readerToken);assertThat(home.status).isEqualTo(200);assertThat(home.data.path("nickname").asString()).isEqualTo("城院同学");assertThat(home.data.path("major").isNull()).isTrue();assertThat(home.data.path("campusCertification").asString()).isEqualTo("VALID");assertThat(home.data.path("publicArticleCount").asLong()).isEqualTo(2);assertThat(home.data.size()).isEqualTo(7);
        assertThat(home.raw).doesNotContain("email","role","verifiedAt","password","contact","honor");
        var first=get("/api/v1/authors/"+writer+"/articles?size=1&page=1",readerToken);var second=get("/api/v1/authors/"+writer+"/articles?size=1&page=2",readerToken);assertThat(first.data.path("total").asLong()).isEqualTo(2);assertThat(first.data.path("items").get(0).path("id").asLong()).isEqualTo(articleB);assertThat(second.data.path("items").get(0).path("id").asLong()).isEqualTo(articleA);assertThat(get("/api/v1/authors/"+writer+"/articles?page=3&size=1",readerToken).data.path("items").isEmpty()).isTrue();
        for(String path:List.of("/api/v1/articles","/api/v1/articles/"+articleA)){var publicResult=get(path,null);assertThat(publicResult.status).isEqualTo(200);assertThat(publicResult.raw).doesNotContain("authorId","nickname","contact","campusCertification",writerEmail);}
        assertThat(get("/api/v1/authors/"+reader,writerToken).status).isEqualTo(404);
    }
    @Test @Order(3) void contactDefaultsOffCanEnableThenImmediatelyCloseAndClear()throws Exception{
        var initial=get("/api/v1/me/contact-settings",writerToken);assertThat(initial.data.path("revision").asLong()).isZero();assertThat(initial.data.path("enabled").asBoolean()).isFalse();assertThat(initial.data.path("type").isNull()).isTrue();
        assertThat(get("/api/v1/authors/"+writer+"/contact",readerToken).data).isEqualTo(JSON.readTree("{\"enabled\":false}"));
        var enabled=set(0,true,"WECHAT","  "+contactValue+"  ",writerToken);assertThat(enabled.status).isEqualTo(200);audit(enabled,"CONTACT_UPDATE",writer,0,1);
        assertThat(get("/api/v1/authors/"+writer+"/contact",readerToken).data.path("value").asString()).isEqualTo(contactValue);
        assertThat(set(1,false,"WECHAT",contactValue,writerToken).status).isEqualTo(200);
        for(int i=0;i<5;i++)assertThat(get("/api/v1/authors/"+writer+"/contact",readerToken).data).isEqualTo(JSON.readTree("{\"enabled\":false}"));
        assertThat(get("/api/v1/me/contact-settings",writerToken).data.path("value").asString()).isEqualTo(contactValue);
        assertThat(set(2,false,null,null,writerToken).status).isEqualTo(200);assertThat(get("/api/v1/me/contact-settings",writerToken).data.path("value").isNull()).isTrue();
    }
    @Test @Order(4) void contactRevisionIsIndependentAndConcurrentOldCopiesCannotOverwrite()throws Exception{
        long rev=contactRevision();String nickname="昵称-private-"+key().substring(0,20);secrets.add(nickname);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var results=pool.invokeAll(List.<Callable<Reply>>of(()->set(rev,true,"QQ",contactValue,writerToken),()->set(rev,true,"WECHAT",contactValue,writerToken),()->call("PUT","/api/v1/me/profile",profile(0,nickname),writerToken)));
            assertThat(List.of(results.get(0).get().status,results.get(1).get().status)).containsExactlyInAnyOrder(200,409);assertThat(results.get(2).get().status).isEqualTo(200);
        }
        var before=get("/api/v1/me/contact-settings",writerToken).data;assertThat(before.path("revision").asLong()).isEqualTo(rev+1);
        var p=profile(1,nickname);p.put("bio","更新简介");assertThat(call("PUT","/api/v1/me/profile",p,writerToken).status).isEqualTo(200);assertThat(get("/api/v1/me/contact-settings",writerToken).data).isEqualTo(before);
        assertThat(set(0,true,"QQ","reader-private",readerToken).status).isEqualTo(200);assertThat(get("/api/v1/me/contact-settings",writerToken).data).isEqualTo(before);
        assertThat(get("/api/v1/authors/"+writer,readerToken).data.path("major").path("id").asLong()).isEqualTo(11);
        assertThat(db.queryForObject("SELECT email FROM platform_user WHERE id=?",String.class,writer)).isEqualTo(writerEmail);
    }
    @Test @Order(5) void strictContactInputPaginationAndNoOtherUsersWriteEndpoint()throws Exception{
        long rev=contactRevision(),before=auditCount();var invalid=new ArrayList<Map<String,Object>>();invalid.add(setting(rev,true,null,null));invalid.add(setting(rev,true,"WEB","javascript:alert(1)"));invalid.add(setting(rev,true,"WEB","https://user:pass@example.test/"));invalid.add(setting(rev,true,"WEB","/relative"));invalid.add(setting(rev,true,"QQ","x\n"));invalid.add(setting(rev,true,"QQ","x".repeat(201)));invalid.add(setting(rev,true,"QR","value"));
        var forged=setting(rev,true,"QQ",contactValue);forged.put("ownerUserId",other);invalid.add(forged);
        for(var body:invalid)assertThat(call("PUT","/api/v1/me/contact-settings",body,writerToken).status).isEqualTo(400);
        assertThat(call("PUT","/api/v1/me/contact-settings?userId="+other,setting(rev,true,"QQ",contactValue),writerToken).status).isEqualTo(400);
        assertThat(call("PUT","/api/v1/authors/"+writer+"/contact",setting(rev,true,"QQ",contactValue),readerToken).status).isEqualTo(405);
        for(String suffix:List.of("?page=0","?size=101","?page=1&page=2","?sort=latest","?majorId=11"))assertThat(get("/api/v1/authors/"+writer+"/articles"+suffix,readerToken).status).isEqualTo(400);
        assertThat(get("/api/v1/authors/"+writer+"?extra=1",readerToken).status).isEqualTo(400);assertThat(get("/api/v1/me/contact-settings?enabled=true",writerToken).status).isEqualTo(400);assertThat(auditCount()).isEqualTo(before);assertThat(contactRevision()).isEqualTo(rev);
        String web="https://example.test/"+key();secrets.add(web);assertThat(set(rev,true,"WEB",web,writerToken).status).isEqualTo(200);assertThat(get("/api/v1/authors/"+writer+"/contact",readerToken).data.path("value").asString()).isEqualTo(web);
    }
    @Test @Order(6) void readerAndTargetCertificationAccountStateAndLogoutRechecked()throws Exception{
        String home="/api/v1/authors/"+writer;
        for(String targetState:List.of("certification","account")){
            if(targetState.equals("certification"))db.update("UPDATE campus_certification SET state='REVOKED' WHERE user_id=?",writer);else db.update("UPDATE platform_user SET account_state='RESTRICTED' WHERE id=?",writer);
            try{for(String path:List.of(home,home+"/contact",home+"/articles","/api/v1/articles/"+articleA+"/author"))assertThat(get(path,readerToken).status).isEqualTo(404);assertThat(get("/api/v1/articles/"+articleA,null).status).isEqualTo(200);}
            finally{db.update("UPDATE campus_certification SET state='VALID' WHERE user_id=?",writer);db.update("UPDATE platform_user SET account_state='ACTIVE' WHERE id=?",writer);}
        }
        db.update("UPDATE campus_certification SET state='REVOKED' WHERE user_id=?",reader);
        try{assertThat(get(home,readerToken).status).isEqualTo(403);assertThat(get("/api/v1/authors/999999/contact",readerToken).status).isEqualTo(403);assertThat(set(1,false,null,null,readerToken).status).isEqualTo(403);}finally{db.update("UPDATE campus_certification SET state='VALID' WHERE user_id=?",reader);}
        db.update("UPDATE platform_user SET account_state='RESTRICTED' WHERE id=?",reader);try{assertThat(get(home,readerToken).status).isEqualTo(403);}finally{db.update("UPDATE platform_user SET account_state='ACTIVE' WHERE id=?",reader);}
        assertThat(call("POST","/api/v1/auth/logout",null,readerToken).status).isEqualTo(200);assertThat(get(home,readerToken).status).isEqualTo(401);readerToken=login(readerEmail);
        for(String path:List.of("/api/v1/admin/audits","/api/v1/ops/logs","/api/v1/business-audit"))assertThat(get(path,readerToken).status).isEqualTo(404);
    }
    @Test @Order(7) void LastPublicArticleControlsHomeAndContactWithoutHidingOwnSettings()throws Exception{
        assertThat(state(articleA,2,"unpublish").status).isEqualTo(200);assertThat(get("/api/v1/authors/"+writer,readerToken).data.path("publicArticleCount").asLong()).isEqualTo(1);
        assertThat(state(articleB,2,"unpublish").status).isEqualTo(200);
        for(String path:List.of("/api/v1/authors/"+writer,"/api/v1/authors/"+writer+"/contact","/api/v1/authors/"+writer+"/articles","/api/v1/articles/"+articleB+"/author"))assertThat(get(path,readerToken).status).isEqualTo(404);
        assertThat(get("/api/v1/me/profile",writerToken).status).isEqualTo(200);assertThat(get("/api/v1/me/contact-settings",writerToken).status).isEqualTo(200);assertThat(state(articleB,3,"restore").status).isEqualTo(200);
    }
    @Test @Order(8) void successfulMutationsHaveTraceAuditAndStateRetriesOnlyRecordOneChange()throws Exception{
        long id=create(writerToken);call("PUT","/api/v1/me/articles/"+id+"/draft",draft(0),writerToken);String request=key();
        var published=call("POST","/api/v1/me/articles/"+id+"/publish",Map.of("expectedRevision",1,"requestId",request),writerToken);audit(published,"ARTICLE_PUBLISH",id,1,2);long count=auditCount();
        assertThat(call("POST","/api/v1/me/articles/"+id+"/publish",Map.of("expectedRevision",1,"requestId",request),writerToken).status).isEqualTo(200);assertThat(auditCount()).isEqualTo(count);
        var off=state(id,2,"unpublish");audit(off,"ARTICLE_UNPUBLISH",id,2,3);count=auditCount();state(id,2,"unpublish");assertThat(auditCount()).isEqualTo(count);
        var restored=state(id,3,"restore");audit(restored,"ARTICLE_RESTORE",id,3,4);count=auditCount();state(id,3,"restore");assertThat(auditCount()).isEqualTo(count);
        var deleted=call("DELETE","/api/v1/me/articles/"+id+"?expectedRevision=4",null,writerToken);audit(deleted,"ARTICLE_DELETE",id,4,5);count=auditCount();call("DELETE","/api/v1/me/articles/"+id+"?expectedRevision=4",null,writerToken);assertThat(auditCount()).isEqualTo(count);
        long profileRev=get("/api/v1/me/profile",writerToken).data.path("revision").asLong();String nickname="private-profile-"+key().substring(0,20);secrets.add(nickname);var changedProfile=call("PUT","/api/v1/me/profile",profile(profileRev,nickname),writerToken);assertThat(changedProfile.status).isEqualTo(200);audit(changedProfile,"PROFILE_UPDATE",writer,profileRev,profileRev+1);
        long rev=contactRevision();var changedContact=set(rev,false,null,null,writerToken);audit(changedContact,"CONTACT_UPDATE",writer,rev,rev+1);count=auditCount();assertThat(set(rev,false,null,null,writerToken).status).isEqualTo(409);assertThat(auditCount()).isEqualTo(count);
    }
    interface Checked {void run()throws Exception;}
    static void auditFailure(Checked check)throws Exception{db.execute("CREATE TRIGGER g04_contact_audit_failure BEFORE INSERT ON business_audit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled audit failure'");try{check.run();}finally{db.execute("DROP TRIGGER g04_contact_audit_failure");}}
    @Test @Order(9) void auditInsertFailureRollsBackContactProfilePublicationAndEveryStateChange()throws Exception{
        JsonNode settings=get("/api/v1/me/contact-settings",writerToken).data,profileBefore=get("/api/v1/me/profile",writerToken).data;long before=auditCount();
        long id=create(writerToken);call("PUT","/api/v1/me/articles/"+id+"/draft",draft(0),writerToken);String request=key();long versions=db.queryForObject("SELECT COUNT(*) FROM article_version",Long.class);
        auditFailure(()->{
            assertThat(set(settings.path("revision").asLong(),true,"WECHAT",contactValue,writerToken).status).isEqualTo(503);
            assertThat(call("PUT","/api/v1/me/profile",profile(profileBefore.path("revision").asLong(),"should-not-save"),writerToken).status).isEqualTo(503);
            assertThat(call("POST","/api/v1/me/articles/"+id+"/publish",Map.of("expectedRevision",1,"requestId",request),writerToken).status).isEqualTo(503);
        });
        assertThat(get("/api/v1/me/contact-settings",writerToken).data).isEqualTo(settings);assertThat(get("/api/v1/me/profile",writerToken).data).isEqualTo(profileBefore);assertThat(auditCount()).isEqualTo(before);assertThat(revision(id)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM article_version",Long.class)).isEqualTo(versions);assertThat(db.queryForObject("SELECT COUNT(*) FROM article_publish_request WHERE article_id=?",Long.class,id)).isZero();assertThat(get("/api/v1/articles/"+id,null).status).isEqualTo(404);
        assertThat(call("POST","/api/v1/me/articles/"+id+"/publish",Map.of("expectedRevision",1,"requestId",request),writerToken).status).isEqualTo(200);
        auditFailure(()->{assertThat(state(id,2,"unpublish").status).isEqualTo(503);assertThat(call("DELETE","/api/v1/me/articles/"+id+"?expectedRevision=2",null,writerToken).status).isEqualTo(503);});assertThat(revision(id)).isEqualTo(2);assertThat(get("/api/v1/articles/"+id,null).status).isEqualTo(200);
        state(id,2,"unpublish");auditFailure(()->assertThat(state(id,3,"restore").status).isEqualTo(503));assertThat(revision(id)).isEqualTo(3);assertThat(get("/api/v1/articles/"+id,null).status).isEqualTo(404);assertThat(state(id,3,"restore").status).isEqualTo(200);
    }
    @Test @Order(10) void runtimeFilesRejectPayloadLeaksAndFileFaultDoesNotForgeAuditFailure()throws Exception{
        String marker="url-secret-"+key();secrets.add(marker);assertThat(get("/api/v1/authors/"+writer+"?unknown="+marker,readerToken).status).isEqualTo(400);assertThat(get("/"+marker,readerToken).status).isEqualTo(404);assertThat(call("PUT","/api/v1/me/contact-settings","{\"value\":\""+marker,writerToken).status).isEqualTo(400);
        Path current=root.resolve("logs/campushub-runtime.log"),backup=root.resolve("logs/original-preserved");String original=Files.readString(current);for(String secret:secrets)assertThat(original.contains(secret)).isFalse();String audits=JSON.writeValueAsString(db.queryForList("SELECT * FROM business_audit WHERE actor_id IN (?,?,?)",writer,reader,other));for(String secret:secrets)assertThat(audits.contains(secret)).isFalse();
        assertThat(original).contains("event=REQUEST action=AUTHOR_HOME","result=DENIED","result=CONFLICT","event=BUSINESS_CHANGE","event=BUSINESS_NO_CHANGE","event=FAILURE category=DATABASE");
        var logs=context.getBean(SafeFileLogs.class);Files.move(current,backup);Files.createDirectory(current);long before=auditCount();
        try{var changed=set(contactRevision(),false,null,null,writerToken);assertThat(changed.status).isEqualTo(200);assertThat(auditCount()).isEqualTo(before+1);assertThat(logs.failedWrites()).isGreaterThan(0);assertThat(logs.healthy()).isFalse();}
        finally{Files.delete(current);Files.move(backup,current);}
        get("/api/v1/me/contact-settings",writerToken);assertThat(logs.healthy()).isTrue();assertThat(Files.readString(current)).startsWith(original);
    }
    @Test @Order(11) void restartRetainsProfilesContactAuditsAndExpiredSessionsRemainRejected()throws Exception{
        var settings=get("/api/v1/me/contact-settings",writerToken).data;var profile=get("/api/v1/me/profile",writerToken).data;var audits=db.queryForList("SELECT * FROM business_audit WHERE actor_id=? ORDER BY occurred_at,event_id",writer);String logBefore=Files.readString(root.resolve("logs/campushub-runtime.log"));
        context.close();boot();db=context.getBean(JdbcTemplate.class);
        assertThat(get("/api/v1/me/contact-settings",writerToken).data).isEqualTo(settings);assertThat(get("/api/v1/me/profile",writerToken).data).isEqualTo(profile);assertThat(db.queryForList("SELECT * FROM business_audit WHERE actor_id=? ORDER BY occurred_at,event_id",writer)).isEqualTo(audits);assertThat(Files.readString(root.resolve("logs/campushub-runtime.log"))).startsWith(logBefore);assertThat(get("/api/v1/authors/"+writer,readerToken).status).isEqualTo(200);
        CLOCK.advance(Duration.ofDays(7));for(String path:List.of("/api/v1/authors/"+writer,"/api/v1/authors/"+writer+"/contact","/api/v1/me/contact-settings"))assertThat(get(path,readerToken).status).isEqualTo(401);assertThat(get("/api/v1/articles/"+articleB,null).status).isEqualTo(200);
        assertThat(db.queryForList("SELECT version FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank",String.class)).containsExactly("1","2","3","4","5","6","7");
        String finalLogs=Files.readString(root.resolve("logs/campushub-runtime.log"));
        String auditExport=JSON.writeValueAsString(db.queryForList("SELECT event_id,occurred_at,trace_id,actor_id,action,object_type,object_id,before_revision,after_revision,before_state,after_state,result FROM business_audit WHERE actor_id IN (?,?,?) ORDER BY occurred_at,event_id",writer,reader,other));
        for(String secret:secrets){assertThat(finalLogs.contains(secret)).isFalse();assertThat(auditExport.contains(secret)).isFalse();}
        Files.writeString(Path.of("target","batch4-runtime-sample.txt"),finalLogs);Files.writeString(Path.of("target","batch4-audit-sample.json"),auditExport+"\n");
    }
}
