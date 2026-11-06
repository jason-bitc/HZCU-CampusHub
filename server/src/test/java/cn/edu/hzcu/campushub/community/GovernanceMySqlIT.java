package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.auth.*;
import java.net.*;
import java.net.http.*;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GovernanceMySqlIT {
    static ConfigurableApplicationContext context;static JdbcTemplate db;static String base,token,userToken,login,password,email;
    static long admin,user,article,version,comment;static Long category;
    static final JsonMapper JSON=JsonMapper.builder().build();static final HttpClient HTTP=HttpClient.newHttpClient();
    static final AuthTestSupport.MutableClock CLOCK=new AuthTestSupport.MutableClock();
    static String key(){return UUID.randomUUID().toString();}
    record Reply(int status,JsonNode data,String code){}
    static Reply call(String method,String path,Object body,String bearer)throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+"/api/v1"+path));if(bearer!=null)request.header("Authorization","Bearer "+bearer);if(body!=null)request.header("Content-Type","application/json");
        var r=HTTP.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());var n=JSON.readTree(r.body());return new Reply(r.statusCode(),n.path("data"),n.path("code").asString());
    }
    @BeforeAll static void start()throws Exception {
        var mailer=new AuthTestSupport.ControlledMailer();context=AuthTestSupport.application(mailer,CLOCK).run("--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=demo");db=context.getBean(JdbcTemplate.class);assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("campushub_test");base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");
        password="g10-"+key();email="g10"+key().replace("-","")+"@stu.hzcu.edu.cn";var auth=context.getBean(AuthService.class);var code=auth.requestCode(email,AuthModels.Purpose.REGISTER);user=auth.register(email,password,code.challengeId(),mailer.code(email,"REGISTER")).userId();userToken=auth.login(email,password).accessToken();
        login="g10"+key().replace("-","");db.update("INSERT INTO community_admin(login_name,password_hash,created_at) VALUES(?,?,NOW(6))",login,context.getBean(PasswordEncoder.class).encode(password));admin=db.queryForObject("SELECT id FROM community_admin WHERE login_name=?",Long.class,login);token=context.getBean(AdminAuthService.class).login(login,password).accessToken();
        article=1_000_000_000_000L+new java.security.SecureRandom().nextLong(1_000_000_000L);version=article;comment=article;
        db.update("INSERT INTO article(id,state,owner_user_id,created_at) VALUES(?,'DRAFT',?,NOW(6))",article,user);
        db.update("INSERT INTO article_version(id,article_id,version_number,title,summary,body,experience_stage,source_type,source_note,published_at,created_at) VALUES(?,?,1,'governance test','summary','published-only','大三','FIRST_HAND','',NOW(6),NOW(6))",version,article);
        db.update("UPDATE article_version SET updated_at=published_at WHERE id=?",version);
        db.update("UPDATE article SET state='PUBLISHED',public_version_id=?,first_published_at=NOW(6),updated_at=NOW(6) WHERE id=?",version,article);
        db.update("INSERT INTO article_draft(article_id,body) VALUES(?,'')",article);
        db.update("INSERT INTO discussion_comment(id,article_id,user_id,body,created_at) VALUES(?,?,?,'test-comment',NOW(6))",comment,article,user);
    }
    static Map<String,Object> command(String action,int revision,String request){return Map.of("action",action,"expectedRevision",revision,"requestId",request,"reason","confirmed test reason");}
    @Test @Order(1) void communityRoutesRequireIndependentAdminAndExposeOnlyPublishedContext()throws Exception {
        assertThat(call("GET","/admin/overview",null,null).status).isEqualTo(401);assertThat(call("GET","/admin/overview",null,userToken).status).isEqualTo(401);
        assertThat(call("GET","/admin/me",null,token).data.path("capabilities").toString()).contains("CONTENT_MODERATE");
        assertThat(call("GET","/admin/articles/"+article,null,token).data.path("body").asString()).isEqualTo("published-only");
        assertThat(call("GET","/admin/articles?size=2",null,token).status).isEqualTo(200);assertThat(call("GET","/admin/articles/"+article+"/comments",null,token).status).isEqualTo(200);
        assertThat(call("GET","/admin/articles?size=100000",null,token).status).isEqualTo(400);assertThat(call("GET","/admin/articles?state=PUBLISHED&state=DELETED",null,token).status).isEqualTo(400);
    }
    @Test @Order(2) void hidingAndRestoringArticlesChangesPublicVisibilityWithDurableIdempotency()throws Exception {
        String request=key(),path="/admin/articles/"+article+"/moderation";var body=command("HIDE",0,request);var hidden=call("POST",path,body,token);assertThat(hidden.status).isEqualTo(200);assertThat(hidden.data.path("state").asString()).isEqualTo("ADMIN_HIDDEN");
        assertThat(call("GET","/articles/"+article,null,null).status).isEqualTo(404);assertThat(call("POST",path,body,token).data).isEqualTo(hidden.data);
        assertThat(call("POST",path,command("RESTORE",0,request),token).code).isEqualTo("REQUEST_KEY_REUSED");assertThat(call("POST",path,command("RESTORE",0,key()),token).code).isEqualTo("REVISION_CONFLICT");
        assertThat(call("POST",path,command("RESTORE",1,key()),token).data.path("state").asString()).isEqualTo("PUBLISHED");assertThat(call("GET","/articles/"+article,null,null).status).isEqualTo(200);
        // An author-offline article must return to author-offline, never be republished by moderation.
        db.update("UPDATE article SET state='AUTHOR_OFFLINE' WHERE id=?",article);assertThat(call("POST",path,command("HIDE",2,key()),token).status).isEqualTo(200);assertThat(call("POST",path,command("RESTORE",3,key()),token).data.path("state").asString()).isEqualTo("AUTHOR_OFFLINE");db.update("UPDATE article SET state='PUBLISHED' WHERE id=?",article);
    }
    @Test @Order(3) void commentModerationIsAuditedAndStaleWritesCannotOverrideIt()throws Exception {
        String path="/admin/comments/"+comment+"/moderation";assertThat(call("POST",path,command("HIDE",1,key()),token).status).isEqualTo(200);assertThat(db.queryForObject("SELECT state FROM discussion_comment WHERE id=?",String.class,comment)).isEqualTo("ADMIN_HIDDEN");
        assertThat(call("POST",path,command("RESTORE",1,key()),token).status).isEqualTo(409);assertThat(call("POST",path,command("RESTORE",2,key()),token).status).isEqualTo(200);assertThat(call("GET","/admin/governance-events",null,token).data.path("total").asLong()).isGreaterThanOrEqualTo(6);
    }
    @Test @Order(4) void categoriesAreRevisionedAndDisabledWithoutDeletingHistory()throws Exception {
        var input=new LinkedHashMap<String,Object>(Map.of("kind","JOB","name","g10-"+key(),"enabled",true,"displayOrder",5,"expectedRevision",0,"requestId",key(),"reason","test category"));
        var created=call("POST","/admin/categories",input,token);assertThat(created.status).isEqualTo(200);category=created.data.path("targetId").asLong();assertThat(category).isPositive();assertThat(call("POST","/admin/categories",input,token).data).isEqualTo(created.data);
        input.put("expectedRevision",1);input.put("requestId",key());input.put("enabled",false);assertThat(call("PUT","/admin/categories/"+category,input,token).status).isEqualTo(200);assertThat(db.queryForObject("SELECT enabled FROM category WHERE id=?",Boolean.class,category)).isFalse();assertThat(call("GET","/admin/categories?kind=JOB",null,token).status).isEqualTo(200);
    }
    @Test @Order(5) void failedAuditInsertionRollsBackModerationAndRetryCanSucceed()throws Exception {
        String trigger="g10_"+key().replace("-","");db.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON governance_event FOR EACH ROW BEGIN IF NEW.admin_id="+admin+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled'; END IF; END");
        String request=key();var body=command("HIDE",4,request);try{assertThat(call("POST","/admin/articles/"+article+"/moderation",body,token).status).isEqualTo(503);assertThat(db.queryForObject("SELECT state FROM article WHERE id=?",String.class,article)).isEqualTo("PUBLISHED");assertThat(db.queryForObject("SELECT COUNT(*) FROM governance_article_hold WHERE article_id=?",Long.class,article)).isZero();}finally{db.execute("DROP TRIGGER "+trigger);}
        assertThat(call("POST","/admin/articles/"+article+"/moderation",body,token).status).isEqualTo(200);
    }
    @Test @Order(6) void restrictionsApplyToWritesButPreservePrivateAppealsAndAcceptedAppealReleasesOnlyThatScope()throws Exception {
        assertThat(call("POST","/admin/articles/"+article+"/moderation",command("RESTORE",5,key()),token).status).isEqualTo(200);
        var restriction=call("POST","/admin/users/"+user+"/restrictions",Map.of("scope","COMMENT","enabled",true,"expectedRevision",0,"requestId",key(),"reason","reviewed restriction"),token);
        assertThat(restriction.status).isEqualTo(200);long id=restriction.data.path("restrictionId").asLong();
        var commentBody=new LinkedHashMap<String,Object>();commentBody.put("requestId",key());commentBody.put("body","test allowed after appeal");commentBody.put("imageIds",List.of());commentBody.put("replyToCommentId",null);
        assertThat(call("POST","/articles/"+article+"/comments",commentBody,userToken).code).isEqualTo("BEHAVIOR_RESTRICTED");assertThat(call("GET","/me/restrictions",null,userToken).data.path("items").size()).isEqualTo(1);
        var appeal=call("POST","/me/appeals",Map.of("restrictionId",id,"requestId",key(),"reason","please review"),userToken);assertThat(appeal.status).isEqualTo(200);long appealId=appeal.data.path("id").asLong();
        assertThat(call("GET","/admin/appeals",null,token).status).isEqualTo(200);assertThat(call("GET","/me/appeals",null,userToken).data.path("total").asLong()).isEqualTo(1);
        var decision=Map.of("outcome","ACCEPTED","expectedRevision",1,"requestId",key(),"reply","restriction lifted after review");assertThat(call("POST","/admin/appeals/"+appealId+"/decisions",decision,token).data.path("state").asString()).isEqualTo("ACCEPTED");assertThat(call("POST","/admin/appeals/"+appealId+"/decisions",decision,token).status).isEqualTo(200);
        assertThat(call("POST","/articles/"+article+"/comments",commentBody,userToken).status).isEqualTo(201);
        assertThat(call("POST","/admin/users/"+user+"/restrictions",Map.of("scope","PUBLISH","enabled",true,"expectedRevision",0,"requestId",key(),"reason","publication restriction"),token).status).isEqualTo(200);
        assertThat(call("POST","/me/articles/"+article+"/publish",Map.of("expectedRevision",0,"requestId",key()),userToken).code).isEqualTo("BEHAVIOR_RESTRICTED");assertThat(call("GET","/admin/users?size=2",null,token).status).isEqualTo(200);
    }
    @Test @Order(7) void confirmedGovernanceCannotCloseAReportWithoutAnActualLinkedAction()throws Exception {
        CLOCK.advance(java.time.Duration.ofSeconds(1));var report=call("POST","/me/cases",Map.of("requestId",key(),"kind","REPORT","category","OTHER","targetType","ARTICLE","targetId",article,"description","controlled report","imageIds",List.of()),userToken);assertThat(report.status).isEqualTo(200);long id=report.data.path("caseId").asLong();
        assertThat(call("POST","/admin/cases/"+id+"/claim",Map.of("expectedRevision",1,"requestId",key()),token).status).isEqualTo(200);var decision=new LinkedHashMap<String,Object>(Map.of("outcome","GOVERNANCE_COMPLETED","expectedRevision",2,"requestId",key(),"reply","content processed","internalNote",""));
        assertThat(call("POST","/admin/cases/"+id+"/decisions",decision,token).code).isEqualTo("GOVERNANCE_NOT_RECORDED");assertThat(call("POST","/admin/articles/"+article+"/moderation",command("HIDE",6,key()),token).status).isEqualTo(200);
        assertThat(call("POST","/admin/cases/"+id+"/decisions",decision,token).data.path("state").asString()).isEqualTo("CLOSED");
    }
    @AfterAll static void cleanup(){if(context==null)return;try{db.update("DELETE FROM community_appeal_receipt WHERE admin_id=?",admin);db.update("DELETE FROM community_appeal WHERE user_id=?",user);db.update("DELETE FROM community_restriction_event WHERE admin_id=?",admin);db.update("DELETE FROM community_restriction WHERE user_id=?",user);for(String table:List.of("community_admin_receipt","community_case_history","community_case_comment","community_case_media"))db.update("DELETE FROM "+table+" WHERE case_id IN (SELECT id FROM community_case WHERE user_id=?)",user);db.update("DELETE FROM community_case WHERE user_id=?",user);db.update("DELETE FROM business_audit WHERE actor_id=? AND actor_domain='USER'",user);db.update("DELETE FROM business_audit WHERE actor_id=? AND actor_domain='COMMUNITY_ADMIN'",admin);db.update("DELETE FROM comment_create_receipt WHERE user_id=?",user);db.update("DELETE FROM governance_event WHERE admin_id=?",admin);db.update("DELETE FROM governance_article_hold WHERE article_id=?",article);db.update("DELETE FROM discussion_comment WHERE article_id=?",article);db.update("DELETE FROM article_draft WHERE article_id=?",article);db.update("UPDATE article SET state='DELETED',public_version_id=NULL WHERE id=?",article);db.update("DELETE FROM article_version WHERE id=?",version);db.update("DELETE FROM article WHERE id=?",article);if(category!=null)db.update("DELETE FROM category WHERE id=?",category);db.update("DELETE FROM community_admin_session WHERE admin_id=?",admin);db.update("DELETE FROM community_admin WHERE id=?",admin);db.update("DELETE FROM community_admin_login_gate WHERE name_digest=?",AuthCrypto.tokenDigest(login));for(String table:List.of("auth_session","campus_certification"))db.update("DELETE FROM "+table+" WHERE user_id=?",user);for(String table:List.of("email_challenge","auth_email_gate"))db.update("DELETE FROM "+table+" WHERE email=?",email);db.update("DELETE FROM platform_user WHERE id=?",user);}finally{context.close();}}
}
