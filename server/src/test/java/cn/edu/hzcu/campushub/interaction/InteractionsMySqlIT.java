package cn.edu.hzcu.campushub.interaction;

import cn.edu.hzcu.campushub.auth.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Actual MySQL, HTTP, locks and audit failures; random users and controlled mail only. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InteractionsMySqlIT {
    @TempDir static Path root;
    static final AuthTestSupport.ControlledMailer MAIL=new AuthTestSupport.ControlledMailer();
    static final AuthTestSupport.MutableClock CLOCK=new AuthTestSupport.MutableClock();
    static final HttpClient HTTP=HttpClient.newHttpClient();
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final List<Long> users=new ArrayList<>();
    static final List<String> emails=new ArrayList<>(),tokens=new ArrayList<>(),secrets=new CopyOnWriteArrayList<>();
    static final List<Map<String,Object>> coverage=new CopyOnWriteArrayList<>();
    static ConfigurableApplicationContext context;
    static JdbcTemplate db;
    static String base,mode,password,titleA,bodyA;
    static long a,b,draft;
    static List<Map<String,Object>> originalArticles,originalVersions,originalCounters;
    static long originalLikes,originalFavorites,originalAudit,originalComments,originalMedia;
    record Reply(int status,JsonNode data,String code,String trace,String raw){}
    static String key(){return UUID.randomUUID().toString();}
    static String secret(){String s="b7-private-"+key();secrets.add(s);return s;}
    static Path runtime(){return root.resolve("logs/campushub-runtime.log");}
    static Path diagnostic(){return root.resolve("logs/campushub-diagnostic.log");}
    static void boot(String selected){
        mode=selected;var app=AuthTestSupport.application(MAIL,CLOCK);
        app.addInitializers(ctx->ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("interaction-test",Map.of("campushub.run-mode",selected,"campushub.logs.enabled",true,"campushub.logs.root",root.resolve("logs").toString(),"campushub.media.root",root.resolve("media").toString(),"server.tomcat.threads.max",3,"server.tomcat.threads.min-spare",1))));
        context=app.run("--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=demo");base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");db=context.getBean(JdbcTemplate.class);
    }
    @BeforeAll static void start()throws Exception{
        boot("debug");assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("campushub_test");
        originalArticles=db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes,count_origin FROM article ORDER BY id");originalVersions=db.queryForList("SELECT id,title,summary,body,source_type,source_note FROM article_version ORDER BY id");originalLikes=count("article_like");originalFavorites=count("article_favorite");originalAudit=count("business_audit");originalComments=count("discussion_comment");originalMedia=count("media_image");
        originalCounters=db.queryForList("SELECT id,effective_coins,effective_likes,count_origin FROM article ORDER BY id");
        password=secret();for(int i=0;i<3;i++){
            String email="b7"+key().replace("-","")+"@stu.hzcu.edu.cn";emails.add(email);secrets.add(email);var auth=context.getBean(AuthService.class);var challenge=auth.requestCode(email,AuthModels.Purpose.REGISTER);String code=MAIL.code(email,"REGISTER");secrets.add(code);users.add(auth.register(email,password,challenge.challengeId(),code).userId());tokens.add(login(i));
        }
        titleA=secret();bodyA=secret();a=article(titleA,bodyA);b=article(secret(),secret());draft=call("POST","/api/v1/me/articles",Map.of("requestId",key()),tokens.getFirst()).data.path("articleId").asLong();
    }
    static String login(int index){String token=context.getBean(AuthService.class).login(emails.get(index),password).accessToken();secrets.add(token);return token;}
    static long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    static boolean protectedPath(String path){return path.contains("/like")||path.contains("/favorite")||path.contains("/interaction-state");}
    static Reply call(String method,String path,Object body,String token)throws Exception{
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20)).header("X-Trace-Id","untrusted-private-input");if(token!=null)request.header("Authorization","Bearer "+token);if(body!=null)request.header("Content-Type","application/json");
        var response=HTTP.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body instanceof String s?s:JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        var json=JSON.readTree(response.body());String trace=json.path("traceId").asString();assertThat(response.headers().firstValue("X-Trace-Id")).contains(trace);assertThat(trace).matches("[0-9a-f-]{36}");
        if(protectedPath(path)){
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");String action=cn.edu.hzcu.campushub.common.RequestTrace.action(path.split("\\?")[0],method);
            for(int retry=0;retry<100&&!Files.readString(runtime()).contains("traceId="+trace+" event=REQUEST");retry++)Thread.sleep(10);
            assertThat(requestLine(trace)).contains("action="+action,"module=INTERACTION");
            coverage.add(Map.of("mode",mode,"method",method,"route",path.split("\\?")[0].replaceAll("/[0-9]+(?=/|$)","/{id}"),"action",action,"status",response.statusCode(),"traceId",trace));
        }
        if(response.statusCode()>=400){assertThat(json.has("data")).isFalse();for(String secret:secrets)assertThat(response.body()).doesNotContain(secret);}
        return new Reply(response.statusCode(),json.path("data"),json.path("code").asString(),trace,response.body());
    }
    static String requestLine(String trace)throws Exception{return Files.readString(runtime()).lines().filter(s->s.contains("traceId="+trace+" event=REQUEST")).findFirst().orElseThrow();}
    static Reply get(String path,int who)throws Exception{return call("GET",path,null,who<0?null:tokens.get(who));}
    static Reply set(long article,String kind,boolean enabled,int who)throws Exception{return call(enabled?"PUT":"DELETE","/api/v1/articles/"+article+"/"+kind,null,tokens.get(who));}
    static Reply state(long article,int who)throws Exception{return get("/api/v1/articles/"+article+"/interaction-state",who);}
    static Reply favorites(int who)throws Exception{return get("/api/v1/me/favorites?size=100",who);}
    static Map<String,Object> row(String kind,long article,int who){return db.queryForMap("SELECT active,revision,activated_at,updated_at FROM article_"+kind+" WHERE article_id=? AND user_id=?",article,users.get(who));}
    static long likes(long article){return db.queryForObject("SELECT COUNT(*) FROM article_like WHERE article_id=? AND active=TRUE",Long.class,article);}
    static long pairCount(String kind,long article,int who){return db.queryForObject("SELECT COUNT(*) FROM article_"+kind+" WHERE article_id=? AND user_id=?",Long.class,article,users.get(who));}
    static Map<String,Object> content(long revision,String title,String text){var d=new LinkedHashMap<String,Object>();d.put("expectedRevision",revision);d.put("title",title);d.put("summary",secret());d.put("body",text);d.put("jobIds",List.of(1));d.put("majorIds",List.of(11));d.put("companyIds",List.of(21));d.put("topicIds",List.of(31));d.put("experienceStage","DEMO");d.put("sourceType","DEMO");d.put("sourceNote",secret());d.put("sourceLinks",List.of(Map.of("label","示例来源","url","https://example.invalid/"+secret())));return d;}
    static long article(String title,String body)throws Exception{
        long id=call("POST","/api/v1/me/articles",Map.of("requestId",key()),tokens.getFirst()).data.path("articleId").asLong();assertThat(call("PUT","/api/v1/me/articles/"+id+"/draft",content(0,title,body),tokens.getFirst()).status).isEqualTo(200);assertThat(call("POST","/api/v1/me/articles/"+id+"/publish",Map.of("expectedRevision",1,"requestId",key()),tokens.getFirst()).status).isEqualTo(200);return id;
    }
    static List<Long> ids(Reply page,String field){var ids=new ArrayList<Long>();for(var item:page.data.path("items"))ids.add(item.path(field).asLong());return ids;}
    static JsonNode item(Reply page,long id){for(var item:page.data.path("items"))if(item.path("articleId").asLong()==id)return item;throw new AssertionError("Own fixture favorite absent");}
    @Test @Order(1) void authenticationPrecedesTargetsAndBodyOrIdentityOverridesAreRejected()throws Exception{
        for(String method:List.of("PUT","DELETE","GET"))for(String suffix:List.of("like","favorite","interaction-state"))assertThat(call(method,"/api/v1/articles/not-an-id/"+suffix,null,null).status).isEqualTo(401);
        assertThat(get("/api/v1/me/favorites",-1).status).isEqualTo(401);assertThat(call("PUT","/api/v1/articles/"+a+"/like",Map.of("userId",users.get(1)),tokens.getFirst()).status).isEqualTo(400);
        assertThat(call("PUT","/api/v1/articles/"+a+"/favorite","{}",tokens.getFirst()).status).isEqualTo(400);assertThat(get("/api/v1/me/favorites?userId="+users.getFirst(),0).status).isEqualTo(400);assertThat(get("/api/v1/articles/"+a+"/interaction-state?role=ADMIN",0).status).isEqualTo(400);
        for(String suffix:List.of("?page=1&page=2","?size=101","?page=0","?sort=latest"))assertThat(get("/api/v1/me/favorites"+suffix,0).status).isEqualTo(400);
        assertThat(set(0,"like",true,0).status).isEqualTo(400);assertThat(call("POST","/api/v1/articles/"+a+"/like",null,tokens.getFirst()).status).isEqualTo(405);
        db.update("UPDATE campus_certification SET state='REVOKED' WHERE user_id=?",users.get(1));try{assertThat(set(999999,"like",true,1).status).isEqualTo(403);assertThat(favorites(1).status).isEqualTo(403);}finally{db.update("UPDATE campus_certification SET state='VALID' WHERE user_id=?",users.get(1));}
        db.update("UPDATE platform_user SET account_state='RESTRICTED' WHERE id=?",users.get(1));try{assertThat(state(a,1).status).isEqualTo(403);}finally{db.update("UPDATE platform_user SET account_state='ACTIVE' WHERE id=?",users.get(1));}
        call("POST","/api/v1/auth/logout",null,tokens.get(2));assertThat(set(a,"favorite",true,2).status).isEqualTo(401);tokens.set(2,login(2));
        var bytes=new byte[]{1,2,3};var request=HttpRequest.newBuilder(URI.create(base+"/api/v1/articles/"+a+"/like")).header("Authorization","Bearer "+tokens.getFirst()).header("Content-Type","multipart/form-data; boundary=unused").PUT(HttpRequest.BodyPublishers.ofInputStream(()->new java.io.ByteArrayInputStream(bytes))).build();var response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(400);assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }
    @Test @Order(2) void emptyReadsAndAbsentCancellationDoNotCreateRelationsOrAudits()throws Exception{
        long audits=count("business_audit");assertThat(state(a,0).data.path("liked").asBoolean()).isFalse();assertThat(state(a,0).data.path("favorited").asBoolean()).isFalse();assertThat(favorites(0).data.path("total").asLong()).isZero();
        assertThat(set(a,"like",false,0).data.path("effectiveLikes").asLong()).isZero();assertThat(set(a,"favorite",false,0).data.path("favorited").asBoolean()).isFalse();assertThat(pairCount("like",a,0)).isZero();assertThat(pairCount("favorite",a,0)).isZero();assertThat(count("business_audit")).isEqualTo(audits);
        for(long target:List.of(a,1001L)){var detail=get("/api/v1/articles/"+target,-1);assertThat(detail.data.path("counts").path("origin").asString()).isEqualTo("LIVE");assertThat(detail.data.path("counts").path("effectiveCoins").asLong()).isZero();assertThat(detail.data.path("counts").path("effectiveLikes").asLong()).isZero();assertThat(detail.data.path("sourceType").asString()).isEqualTo("DEMO");assertThat(detail.raw).doesNotContain("liked","favorited","userId","actorId");}
    }
    @Test @Order(3) void ownArticleLikesAndFavoritesAreIndependentAuditedStateTransitions()throws Exception{
        long before=count("business_audit");assertThat(set(a,"like",true,0).data.path("effectiveLikes").asLong()).isEqualTo(1);assertThat(state(a,0).data.path("favorited").asBoolean()).isFalse();assertThat(set(a,"favorite",true,0).data.path("favorited").asBoolean()).isTrue();assertThat(set(a,"like",false,0).data.path("effectiveLikes").asLong()).isZero();assertThat(state(a,0).data.path("favorited").asBoolean()).isTrue();assertThat(set(a,"like",true,0).data.path("effectiveLikes").asLong()).isEqualTo(1);
        assertThat(count("business_audit")).isEqualTo(before+4);assertThat(((Number)row("like",a,0).get("revision")).longValue()).isEqualTo(3);assertThat(pairCount("like",a,0)).isEqualTo(1);
        var audits=db.queryForList("SELECT actor_id,object_type,object_id,before_revision,after_revision FROM business_audit WHERE actor_id=? AND action IN ('LIKE_SET','LIKE_CANCEL','FAVORITE_SET','FAVORITE_CANCEL')",users.getFirst());assertThat(audits).hasSize(4);for(var audit:audits){assertThat(((Number)audit.get("object_id")).longValue()).isEqualTo(a);assertThat(((Number)audit.get("after_revision")).longValue()).isEqualTo(((Number)audit.get("before_revision")).longValue()+1);}
    }
    static void concurrent(long article,String kind,boolean active,int who)throws Exception{try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var tasks=new ArrayList<Callable<Reply>>();for(int i=0;i<12;i++)tasks.add(()->set(article,kind,active,who));for(var future:pool.invokeAll(tasks))assertThat(future.get().status).isEqualTo(200);}}
    @Test @Order(4) void parallelIdenticalSettingsHaveOneRevisionAuditAndFavoriteActivationTime()throws Exception{
        long audits=count("business_audit");concurrent(b,"like",true,1);assertThat(likes(b)).isEqualTo(1);assertThat(((Number)row("like",b,1).get("revision")).longValue()).isEqualTo(1);assertThat(count("business_audit")).isEqualTo(audits+1);concurrent(b,"like",false,1);assertThat(likes(b)).isZero();assertThat(count("business_audit")).isEqualTo(audits+2);
        concurrent(b,"favorite",true,1);var original=row("favorite",b,1);CLOCK.advance(Duration.ofMinutes(5));concurrent(b,"favorite",true,1);assertThat(row("favorite",b,1)).isEqualTo(original);assertThat(count("business_audit")).isEqualTo(audits+3);concurrent(b,"favorite",false,1);CLOCK.advance(Duration.ofSeconds(1));assertThat(set(b,"favorite",true,1).status).isEqualTo(200);assertThat(row("favorite",b,1).get("activated_at")).isNotEqualTo(original.get("activated_at"));assertThat(((Number)row("favorite",b,1).get("revision")).longValue()).isEqualTo(3);
    }
    @Test @Order(5) void multipleUsersAccumulateLikesAndRestrictionsDoNotRewriteHistory()throws Exception{
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){for(var future:pool.invokeAll(List.<Callable<Reply>>of(()->set(b,"like",true,0),()->set(b,"like",true,1),()->set(b,"like",true,2))))assertThat(future.get().status).isEqualTo(200);}
        assertThat(likes(b)).isEqualTo(3);assertThat(set(b,"like",false,1).data.path("effectiveLikes").asLong()).isEqualTo(2);assertThat(state(b,0).data.path("liked").asBoolean()).isTrue();assertThat(state(b,1).data.path("liked").asBoolean()).isFalse();set(b,"favorite",true,2);assertThat(likes(b)).isEqualTo(2);
        db.update("UPDATE campus_certification SET state='REVOKED' WHERE user_id=?",users.get(2));try{assertThat(set(b,"like",false,2).status).isEqualTo(403);assertThat(likes(b)).isEqualTo(2);assertThat(get("/api/v1/articles/"+b,-1).data.path("counts").path("effectiveLikes").asLong()).isEqualTo(2);}finally{db.update("UPDATE campus_certification SET state='VALID' WHERE user_id=?",users.get(2));}
    }
    @Test @Order(6) void privateFavoritePagesUseActivationTimeAndStableArticleId()throws Exception{
        set(b,"favorite",false,2);set(a,"favorite",true,2);CLOCK.advance(Duration.ofSeconds(1));set(b,"favorite",true,2);CLOCK.advance(Duration.ofSeconds(1));set(1001,"favorite",true,2);set(1006,"favorite",true,2);
        assertThat(ids(favorites(2),"articleId")).containsExactly(1006L,1001L,b,a);var combined=new ArrayList<Long>();for(int page=1;page<=2;page++)combined.addAll(ids(get("/api/v1/me/favorites?size=2&page="+page,2),"articleId"));assertThat(combined).containsExactly(1006L,1001L,b,a);assertThat(get("/api/v1/me/favorites?size=2&page=3",2).data.path("items").isEmpty()).isTrue();
        var old=row("favorite",a,2);CLOCK.advance(Duration.ofSeconds(1));set(a,"favorite",true,2);assertThat(row("favorite",a,2)).isEqualTo(old);assertThat(ids(favorites(2),"articleId")).containsExactly(1006L,1001L,b,a);set(a,"favorite",false,2);set(a,"favorite",true,2);assertThat(ids(favorites(2),"articleId").getFirst()).isEqualTo(a);assertThat(ids(favorites(1),"articleId")).containsExactly(b);
    }
    @Test @Order(7) void unavailableFavoritesAreMinimalCancellableAndRestoredFromCurrentPublication()throws Exception{
        long count=likes(a);assertThat(call("POST","/api/v1/me/articles/"+a+"/unpublish",Map.of("expectedRevision",2),tokens.getFirst()).status).isEqualTo(200);var placeholder=item(favorites(2),a);assertThat(placeholder.size()).isEqualTo(2);assertThat(placeholder.path("available").asBoolean()).isFalse();assertThat(favorites(2).raw).doesNotContain(titleA,bodyA,"AUTHOR_OFFLINE");
        for(String kind:List.of("like","favorite"))assertThat(set(a,kind,true,0).status).isEqualTo(404);assertThat(set(a,"like",false,0).status).isEqualTo(404);assertThat(state(a,0).status).isEqualTo(404);assertThat(set(a,"favorite",false,2).status).isEqualTo(200);long audits=count("business_audit");assertThat(set(a,"favorite",false,2).status).isEqualTo(200);assertThat(count("business_audit")).isEqualTo(audits);assertThat(set(a,"favorite",false,1).status).isEqualTo(404);
        assertThat(call("POST","/api/v1/me/articles/"+a+"/restore",Map.of("expectedRevision",3),tokens.getFirst()).status).isEqualTo(200);assertThat(likes(a)).isEqualTo(count);assertThat(ids(favorites(2),"articleId")).doesNotContain(a);
        titleA=secret();bodyA=secret();assertThat(call("PUT","/api/v1/me/articles/"+a+"/draft",content(4,titleA,bodyA),tokens.getFirst()).status).isEqualTo(200);assertThat(call("POST","/api/v1/me/articles/"+a+"/publish",Map.of("expectedRevision",5,"requestId",key()),tokens.getFirst()).status).isEqualTo(200);assertThat(item(favorites(0),a).path("article").path("title").asString()).isEqualTo(titleA);
    }
    @Test @Order(8) void hiddenDeletedAndInvalidPublicVersionsDenyLikesButPreserveFavoritePlaceholders()throws Exception{
        db.update("UPDATE article SET state='ADMIN_HIDDEN' WHERE id=?",b);try{assertThat(item(favorites(1),b).size()).isEqualTo(2);assertThat(set(b,"like",true,1).status).isEqualTo(404);assertThat(set(b,"favorite",true,1).status).isEqualTo(404);assertThat(state(b,1).status).isEqualTo(404);}finally{db.update("UPDATE article SET state='PUBLISHED' WHERE id=?",b);}
        assertThat(call("DELETE","/api/v1/me/articles/"+b+"?expectedRevision=2",null,tokens.getFirst()).status).isEqualTo(200);assertThat(item(favorites(2),b).path("available").asBoolean()).isFalse();assertThat(set(b,"like",false,0).status).isEqualTo(404);assertThat(set(b,"favorite",false,1).status).isEqualTo(200);assertThat(likes(b)).isEqualTo(2);
        for(long id:List.of(draft,999999L)){assertThat(set(id,"like",true,0).status).isEqualTo(404);assertThat(set(id,"favorite",true,0).status).isEqualTo(404);assertThat(state(id,0).status).isEqualTo(404);}
        assertThatThrownBy(()->db.update("UPDATE article SET state='PUBLISHED' WHERE id=?",draft)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        long unpublished=new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class)).execute(tx->{long version=context.getBean(cn.edu.hzcu.campushub.authoring.WritingRepository.class).allocate("version");db.update("INSERT INTO article_version(id,article_id,version_number,title,summary,body,experience_stage,source_type,source_note,created_at) VALUES(?,?,1,?,?,?,?,?,?,?)",version,draft,secret(),secret(),secret(),"DEMO","DEMO",secret(),java.sql.Timestamp.from(CLOCK.instant()));return version;});
        db.update("UPDATE article SET state='PUBLISHED',public_version_id=? WHERE id=?",unpublished,draft);try{assertThat(set(draft,"like",true,0).status).isEqualTo(404);assertThat(set(draft,"favorite",true,0).status).isEqualTo(404);assertThat(state(draft,0).status).isEqualTo(404);}finally{db.update("UPDATE article SET state='DRAFT',public_version_id=NULL WHERE id=?",draft);}
    }
    @Test @Order(9) void liveCountsReplaceDemoNumbersAcrossReadPathsAndRecognitionSorting()throws Exception{
        var latest=ids(get("/api/v1/articles?sort=latest",-1),"id");set(1006,"like",true,0);set(1006,"like",true,1);set(1001,"like",true,0);set(1001,"like",true,1);set(1005,"favorite",true,0);
        var recognized=get("/api/v1/articles?sort=recognized",-1);assertThat(ids(recognized,"id").subList(0,2)).containsExactly(1001L,1006L);assertThat(ids(get("/api/v1/articles?sort=latest",-1),"id")).containsExactlyElementsOf(latest);
        assertThat(ids(get("/api/v1/articles?jobId=1&majorId=11&companyId=21&topicId=31",-1),"id")).containsExactly(1001L,1006L,a);set(1001,"like",false,1);assertThat(ids(get("/api/v1/articles?sort=recognized",-1),"id").getFirst()).isEqualTo(1006L);
        var paged=new ArrayList<Long>();for(int page=1;page<=4;page++)paged.addAll(ids(get("/api/v1/articles?size=2&page="+page,-1),"id"));assertThat(paged).containsExactlyElementsOf(ids(get("/api/v1/articles",-1),"id"));
        var liveExamples=new ArrayList<Map<String,Object>>();for(long id:List.of(a,1001L,1004L,1006L)){var detail=get("/api/v1/articles/"+id,-1);assertLive(detail.data,id);assertThat(detail.raw).doesNotContain("liked","favorited","userId","actorId");assertThat(detail.data.path("sourceType").asString()).isEqualTo("DEMO");liveExamples.add(Map.of("articleId",id,"effectiveCoins",detail.data.path("counts").path("effectiveCoins").asLong(),"effectiveLikes",detail.data.path("counts").path("effectiveLikes").asLong(),"factLikes",likes(id),"origin",detail.data.path("counts").path("origin").asString(),"sourceType",detail.data.path("sourceType").asString()));}Files.writeString(Path.of("target/batch7-live-count-examples.json"),JSON.writeValueAsString(liveExamples));
        for(var summary:get("/api/v1/authors/"+users.getFirst()+"/articles",1).data.path("items"))assertLive(summary,summary.path("id").asLong());assertLive(item(favorites(2),1001).path("article"),1001);
        assertThat(db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes,count_origin FROM article WHERE id IN (1001,1002,1003,1004,1005,1006,2001,2002,2003,2004) ORDER BY id")).isEqualTo(originalArticles);
    }
    static void assertLive(JsonNode article,long id){assertThat(article.path("counts").path("origin").asString()).isEqualTo("LIVE");assertThat(article.path("counts").path("effectiveCoins").asLong()).isZero();assertThat(article.path("counts").path("effectiveLikes").asLong()).isEqualTo(likes(id));}
    interface Checked {Reply run()throws Exception;}
    static Reply fault(String table,String operation,String condition,Checked request)throws Exception{
        String marker=secret();db.execute("CREATE TRIGGER b07_interaction_failure BEFORE "+operation+" ON "+table+" FOR EACH ROW BEGIN IF "+condition+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='"+marker+"'; END IF; END");try{return request.run();}finally{db.execute("DROP TRIGGER b07_interaction_failure");}
    }
    static Reply auditFault(Checked request)throws Exception{return fault("business_audit","INSERT","NEW.actor_id="+users.getFirst()+" AND NEW.action IN ('LIKE_SET','LIKE_CANCEL','FAVORITE_SET','FAVORITE_CANCEL')",request);}
    @Test @Order(10) void auditAndStateFailuresRollBackRowsCountsTimesAndSuccessEvents()throws Exception{
        long audits=count("business_audit");assertThat(auditFault(()->set(1002,"like",true,0)).status).isEqualTo(503);assertThat(pairCount("like",1002,0)).isZero();assertThat(likes(1002)).isZero();assertThat(count("business_audit")).isEqualTo(audits);set(1002,"like",true,0);var liked=row("like",1002,0);assertThat(auditFault(()->set(1002,"like",false,0)).status).isEqualTo(503);assertThat(row("like",1002,0)).isEqualTo(liked);set(1002,"like",false,0);
        assertThat(auditFault(()->set(1002,"favorite",true,0)).status).isEqualTo(503);assertThat(pairCount("favorite",1002,0)).isZero();set(1002,"favorite",true,0);var favorite=row("favorite",1002,0);assertThat(auditFault(()->set(1002,"favorite",false,0)).status).isEqualTo(503);assertThat(row("favorite",1002,0)).isEqualTo(favorite);set(1002,"favorite",false,0);
        assertThat(fault("article_like","INSERT","NEW.user_id="+users.getFirst(),()->set(1003,"like",true,0)).status).isEqualTo(503);assertThat(pairCount("like",1003,0)).isZero();assertThat(set(1003,"like",true,0).status).isEqualTo(200);
        var canceled=row("favorite",1002,0);assertThat(fault("article_favorite","UPDATE","NEW.user_id="+users.getFirst(),()->set(1002,"favorite",true,0)).status).isEqualTo(503);assertThat(row("favorite",1002,0)).isEqualTo(canceled);assertThat(set(1002,"favorite",true,0).status).isEqualTo(200);
    }
    @Test @Order(11) void articleLockOrdersWithdrawalAndLateWritesWithoutFalseAudits()throws Exception{
        long target=article(secret(),secret());var source=context.getBean(DataSource.class);long audits=count("business_audit");
        for(String kind:List.of("like","favorite")){
            db.update("UPDATE article SET state='PUBLISHED' WHERE id=?",target);
            try(var connection=source.getConnection();var pool=Executors.newVirtualThreadPerTaskExecutor()){
                connection.setAutoCommit(false);try(var statement=connection.prepareStatement("UPDATE article SET state='AUTHOR_OFFLINE' WHERE id=?")){statement.setLong(1,target);statement.executeUpdate();}
                var future=pool.submit(()->set(target,kind,true,0));Thread.sleep(120);assertThat(future.isDone()).isFalse();connection.commit();assertThat(future.get().status).isEqualTo(404);
            }
        }
        assertThat(count("business_audit")).isEqualTo(audits);assertThat(pairCount("like",target,0)).isZero();assertThat(pairCount("favorite",target,0)).isZero();
        db.update("UPDATE article SET state='PUBLISHED' WHERE id=?",target);assertThat(set(target,"like",true,0).status).isEqualTo(200);assertThat(call("POST","/api/v1/me/articles/"+target+"/unpublish",Map.of("expectedRevision",2),tokens.getFirst()).status).isEqualTo(200);assertThat(likes(target)).isEqualTo(1);assertThat(set(target,"like",false,0).status).isEqualTo(404);
    }
    @Test @Order(12) void allSixActionsWorkInBothModesWhileReleaseAddsNoDebugOrDiagnostics()throws Exception{
        set(1003,"like",false,0);var contracts=new LinkedHashMap<String,List<String>>();
        for(String selected:List.of("debug","release")){
            if(!mode.equals(selected)){context.close();boot(selected);}long diagBefore=Files.size(diagnostic()),debugBefore=Files.readString(runtime()).lines().filter(s->s.contains("event=DEBUG_STAGE")).count();var results=new ArrayList<Reply>();
            results.add(state(1003,0));results.add(set(1003,"like",true,0));results.add(set(1003,"like",false,0));results.add(set(1003,"favorite",true,0));results.add(favorites(0));results.add(set(1003,"favorite",false,0));Reply failed=auditFault(()->set(1003,"like",true,0));results.add(failed);assertThat(failed.status).isEqualTo(503);assertThat(likes(1003)).isZero();
            contracts.put(selected,results.stream().map(r->r.status+":"+r.code).toList());
            if(selected.equals("debug")){assertThat(Files.readString(diagnostic()).lines().filter(s->s.contains(failed.trace)).toList()).anySatisfy(s->assertThat(s).contains("module=INTERACTION","action=LIKE_SET","BusinessAudit.java:","InteractionService.java:","stage=AUDIT"));}
            else{assertThat(Files.size(diagnostic())).isEqualTo(diagBefore);assertThat(Files.readString(runtime()).lines().filter(s->s.contains("event=DEBUG_STAGE")).count()).isEqualTo(debugBefore);}
        }
        assertThat(contracts.get("release")).isEqualTo(contracts.get("debug"));Files.writeString(Path.of("target/batch7-mode-contracts.json"),JSON.writeValueAsString(contracts));
    }
    @Test @Order(13) void restartRetainsStateActivationAndAuditAndExpiredSessionsCannotOperate()throws Exception{
        var likes=db.queryForList("SELECT * FROM article_like ORDER BY user_id,article_id");var favorites=db.queryForList("SELECT * FROM article_favorite ORDER BY user_id,article_id");long audits=count("business_audit");context.close();boot("release");assertThat(db.queryForList("SELECT * FROM article_like ORDER BY user_id,article_id")).isEqualTo(likes);assertThat(db.queryForList("SELECT * FROM article_favorite ORDER BY user_id,article_id")).isEqualTo(favorites);assertThat(count("business_audit")).isEqualTo(audits);assertThat(item(favorites(2),b).size()).isEqualTo(2);assertThat(state(a,0).data.path("liked").asBoolean()).isTrue();
        CLOCK.advance(Duration.ofDays(7));for(String method:List.of("PUT","DELETE"))for(String kind:List.of("like","favorite"))assertThat(call(method,"/api/v1/articles/"+a+"/"+kind,null,tokens.getFirst()).status).isEqualTo(401);assertThat(state(a,0).status).isEqualTo(401);assertThat(favorites(0).status).isEqualTo(401);assertLive(get("/api/v1/articles/1001",-1).data,1001);
        String runtime=Files.readString(runtime()),diagnostic=Files.readString(diagnostic());String audit=JSON.writeValueAsString(db.queryForList("SELECT * FROM business_audit WHERE actor_id IN (?,?,?) ORDER BY occurred_at,event_id",users.get(0),users.get(1),users.get(2)));for(String secret:secrets){assertThat(runtime).doesNotContain(secret);assertThat(diagnostic).doesNotContain(secret);assertThat(audit).doesNotContain(secret);}
        Files.writeString(Path.of("target/batch7-runtime-sample.txt"),runtime);Files.writeString(Path.of("target/batch7-diagnostic-sample.txt"),diagnostic);Files.writeString(Path.of("target/batch7-audit-sample.json"),audit);Files.writeString(Path.of("target/batch7-route-coverage.json"),JSON.writeValueAsString(coverage));
        assertThat(db.queryForList("SELECT version FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank",String.class)).containsExactly("1","2","3","4","5","6","7");
    }
    @AfterAll static void cleanup()throws Exception{
        if(context==null)return;
        try{
            var named=context.getBean(NamedParameterJdbcTemplate.class);var p=new MapSqlParameterSource("users",users).addValue("emails",emails);
            if(!users.isEmpty()){
                for(String table:List.of("article_like","article_favorite"))named.update("DELETE FROM "+table+" WHERE user_id IN (:users)",p);named.update("DELETE FROM business_audit WHERE actor_id IN (:users)",p);
                var articles=named.queryForList("SELECT id FROM article WHERE owner_user_id IN (:users)",p,Long.class);if(!articles.isEmpty()){
                    p.addValue("articles",articles);for(String table:List.of("article_publish_request","article_version_image","article_draft_image","article_draft_category","article_draft_source_link","author_create_request","media_image","article_draft"))named.update("DELETE FROM "+table+" WHERE article_id IN (:articles)",p);
                    named.update("UPDATE article SET state='DELETED',public_version_id=NULL WHERE id IN (:articles)",p);named.update("DELETE FROM article_version_category WHERE version_id IN (SELECT id FROM article_version WHERE article_id IN (:articles))",p);named.update("DELETE FROM article_source_link WHERE version_id IN (SELECT id FROM article_version WHERE article_id IN (:articles))",p);named.update("DELETE FROM article_version WHERE article_id IN (:articles)",p);named.update("DELETE FROM article WHERE id IN (:articles)",p);
                }
                for(String table:List.of("author_contact_setting","author_profile","auth_session","campus_certification"))named.update("DELETE FROM "+table+" WHERE user_id IN (:users)",p);
            }
            if(!emails.isEmpty()){named.update("DELETE FROM email_challenge WHERE email IN (:emails)",p);named.update("DELETE FROM auth_email_gate WHERE email IN (:emails)",p);}if(!users.isEmpty())named.update("DELETE FROM platform_user WHERE id IN (:users)",p);
            if(originalArticles!=null){assertThat(db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes,count_origin FROM article ORDER BY id")).isEqualTo(originalArticles);assertThat(db.queryForList("SELECT id,title,summary,body,source_type,source_note FROM article_version ORDER BY id")).isEqualTo(originalVersions);assertThat(count("article_like")).isEqualTo(originalLikes);assertThat(count("article_favorite")).isEqualTo(originalFavorites);assertThat(count("business_audit")).isEqualTo(originalAudit);assertThat(count("discussion_comment")).isEqualTo(originalComments);assertThat(count("media_image")).isEqualTo(originalMedia);}
            if(originalCounters!=null){var after=db.queryForList("SELECT id,effective_coins,effective_likes,count_origin FROM article ORDER BY id");assertThat(after).isEqualTo(originalCounters);Files.writeString(Path.of("target/batch7-historical-counter-snapshot.json"),JSON.writeValueAsString(Map.of("historicalBefore",originalCounters,"historicalAfter",after,"matched",originalCounters.equals(after))));}
        }finally{context.close();}
    }
}
