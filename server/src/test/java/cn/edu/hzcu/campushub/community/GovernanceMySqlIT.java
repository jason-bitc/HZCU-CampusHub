package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.auth.*;
import java.net.*;
import java.net.http.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
    static final List<Long> MERGE_CATEGORIES = new ArrayList<>();
    static final JsonMapper JSON=JsonMapper.builder().build();static final HttpClient HTTP=HttpClient.newHttpClient();
    static final AuthTestSupport.MutableClock CLOCK=new AuthTestSupport.MutableClock();
    static final AuthTestSupport.ControlledMailer MAILER = new AuthTestSupport.ControlledMailer();
    static String key(){return UUID.randomUUID().toString();}
    record Reply(int status,JsonNode data,String code){}
    static Reply call(String method,String path,Object body,String bearer)throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+"/api/v1"+path));if(bearer!=null)request.header("Authorization","Bearer "+bearer);if(body!=null)request.header("Content-Type","application/json");
        var r=HTTP.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());var n=JSON.readTree(r.body());return new Reply(r.statusCode(),n.path("data"),n.path("code").asString());
    }
    @BeforeAll static void start()throws Exception {
        context=AuthTestSupport.application(MAILER,CLOCK).run("--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=demo");db=context.getBean(JdbcTemplate.class);assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("campushub_test");base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");
        password="g10-"+key();email="g10"+key().replace("-","")+"@stu.hzcu.edu.cn";var auth=context.getBean(AuthService.class);var code=auth.requestCode(email,AuthModels.Purpose.REGISTER);user=auth.register(email,password,code.challengeId(),MAILER.code(email,"REGISTER")).userId();userToken=auth.login(email,password).accessToken();
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
        var workspace = call("GET", "/me/articles?state=ADMIN_HIDDEN", null, userToken).data;
        assertThat(workspace.path("total").asLong()).isEqualTo(1);
        var hiddenItem = workspace.path("items").get(0);
        assertThat(hiddenItem.path("articleId").asLong()).isEqualTo(article);
        assertThat(hiddenItem.path("allowedActions").toString()).isEqualTo("[\"VIEW_DRAFT\",\"DELETE\"]");
        var hiddenPreview = call("GET", "/me/articles/" + article + "/preview?expectedRevision=0", null, userToken).data;
        assertThat(hiddenPreview.path("blockedBy").asString()).isEqualTo("STATE_CONFLICT");
        assertThat(hiddenPreview.path("canPublish").asBoolean()).isFalse();
        assertThat(call("GET", "/me/articles?state=PUBLISHED", null, userToken).data.path("total").asLong()).isZero();
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
        var workspace = call("GET", "/me/articles?state=PUBLISHED", null, userToken).data;
        assertThat(workspace.path("publishingRestricted").asBoolean()).isTrue();
        var restrictedPreview = call("GET", "/me/articles/" + article + "/preview?expectedRevision=0", null, userToken).data;
        assertThat(restrictedPreview.path("blockedBy").asString()).isEqualTo("BEHAVIOR_RESTRICTED");
        assertThat(restrictedPreview.path("canPublish").asBoolean()).isFalse();
        assertThat(workspace.path("items").get(0).path("allowedActions").toString())
                .contains("EDIT_DRAFT", "UNPUBLISH").doesNotContain("\"PUBLISH\"", "RESTORE");
    }
    @Test @Order(7) void confirmedGovernanceCannotCloseAReportWithoutAnActualLinkedAction()throws Exception {
        CLOCK.advance(java.time.Duration.ofSeconds(1));var report=call("POST","/me/cases",Map.of("requestId",key(),"kind","REPORT","category","OTHER","targetType","ARTICLE","targetId",article,"description","controlled report","imageIds",List.of()),userToken);assertThat(report.status).isEqualTo(200);long id=report.data.path("caseId").asLong();
        assertThat(call("POST","/admin/cases/"+id+"/claim",Map.of("expectedRevision",1,"requestId",key()),token).status).isEqualTo(200);var decision=new LinkedHashMap<String,Object>(Map.of("outcome","GOVERNANCE_COMPLETED","expectedRevision",2,"requestId",key(),"reply","content processed","internalNote",""));
        assertThat(call("POST","/admin/cases/"+id+"/decisions",decision,token).code).isEqualTo("GOVERNANCE_NOT_RECORDED");assertThat(call("POST","/admin/articles/"+article+"/moderation",command("HIDE",6,key()),token).status).isEqualTo(200);
        assertThat(call("POST","/admin/cases/"+id+"/decisions",decision,token).data.path("state").asString()).isEqualTo("CLOSED");
        assertThat(call("POST","/admin/cases/"+id+"/decisions",decision,token).status).isEqualTo(200);
        var inbox = call("GET", "/me/notifications?size=100", null, userToken);
        assertThat(inbox.status).isEqualTo(200);
        List<JsonNode> items = new ArrayList<>();
        inbox.data.path("items").forEach(items::add);
        assertThat(items).filteredOn(item -> "CASE_DECISION".equals(item.path("kind").asString()))
                .singleElement().satisfies(item -> {
                    assertThat(item.path("outcome").asString()).isEqualTo("GOVERNANCE_COMPLETED");
                    assertThat(item.path("target").path("id").asLong()).isEqualTo(id);
                });
        assertThat(items).anySatisfy(item -> {
            assertThat(item.path("kind").asString()).isEqualTo("ARTICLE_MODERATION");
            assertThat(item.path("outcome").asString()).isEqualTo("HIDDEN");
            assertThat(item.path("target").isNull()).isTrue();
        });
        assertThat(items).extracting(item -> item.path("kind").asString())
                .contains("COMMENT_MODERATION", "RESTRICTION_CHANGED", "APPEAL_DECIDED");
        assertThat(inbox.data.toString()).doesNotContain("confirmed test reason", "restriction lifted after review");
        assertThat(call("GET", "/me/notifications", null, token).status).isEqualTo(401);
    }

    @Test @Order(8) void campusRevocationIsAuthorizedIdempotentAuditedAndRecoverable() throws Exception {
        String adminRoute = "/admin/users/" + user + "/certification";
        String ownRoute = "/me/certification";
        String revoke = adminRoute + "/revocation";
        String reason = "校园邮箱归属需重新核验";
        String requestId = key();
        var command = Map.of("expectedRevision", 1, "requestId", requestId, "reason", reason);
        assertThat(call("GET", adminRoute, null, token).data.path("revision").asLong()).isEqualTo(1);
        assertThat(call("GET", adminRoute, null, userToken).status).isEqualTo(401);
        assertThat(call("POST", revoke, command, userToken).status).isEqualTo(401);
        assertThat(call("POST", revoke, Map.of("expectedRevision", 1, "requestId", key(), "reason", " "), token).status)
                .isEqualTo(400);
        assertThat(call("POST", revoke, Map.of("expectedRevision", 0, "requestId", key(), "reason", reason), token).code)
                .isEqualTo("REVISION_CONFLICT");

        String trigger = "g04_revoke_" + key().replace("-", "");
        db.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON business_audit FOR EACH ROW "
                + "BEGIN IF NEW.action='CAMPUS_REVOKE' AND NEW.object_id=" + user
                + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled'; END IF; END");
        try {
            assertThat(call("POST", revoke, command, token).status).isEqualTo(503);
            assertThat(call("GET", ownRoute, null, userToken).data.path("state").asString()).isEqualTo("VALID");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM campus_certification_revocation WHERE user_id=?",
                    Long.class, user)).isZero();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM personal_notification WHERE recipient_id=? AND kind='CAMPUS_REVOKED'",
                    Long.class, user)).isZero();
        } finally {
            db.execute("DROP TRIGGER " + trigger);
        }

        try (var pool = Executors.newFixedThreadPool(2)) {
            var replies = pool.invokeAll(List.<Callable<Reply>>of(
                    () -> call("POST", revoke, command, token), () -> call("POST", revoke, command, token)),
                    15, java.util.concurrent.TimeUnit.SECONDS);
            for (var result : replies) {
                var response = result.get();
                assertThat(response.status).isEqualTo(200);
                assertThat(response.data.path("revision").asLong()).isEqualTo(2);
            }
        }
        assertThat(call("POST", revoke, Map.of("expectedRevision", 1, "requestId", requestId, "reason", "内容有变"),
                token).code).isEqualTo("REQUEST_KEY_REUSED");
        assertThat(call("POST", revoke, Map.of("expectedRevision", 2, "requestId", key(), "reason", reason),
                token).code).isEqualTo("CERTIFICATION_ALREADY_REVOKED");
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM business_audit WHERE action='CAMPUS_REVOKE' AND object_id=?
                  AND actor_domain='COMMUNITY_ADMIN' AND actor_id=? AND before_revision=1 AND after_revision=2
                  AND before_state='VALID' AND after_state='REVOKED'
                """, Long.class, user, admin)).isEqualTo(1);

        var status = call("GET", ownRoute, null, userToken).data;
        assertThat(status.path("revocationReason").asString()).isEqualTo(reason);
        assertThat(status.path("revokedAt").asString()).isNotBlank();
        assertThat(status.path("canReverify").asBoolean()).isTrue();
        var inbox = call("GET", "/me/notifications?size=100", null, userToken).data;
        List<JsonNode> notifications = new ArrayList<>();
        inbox.path("items").forEach(notifications::add);
        assertThat(notifications).filteredOn(item -> "CAMPUS_REVOKED".equals(item.path("kind").asString()))
                .singleElement().satisfies(item -> assertThat(item.path("target").path("type").asString())
                        .isEqualTo("CERTIFICATION"));
        assertThat(inbox.toString()).doesNotContain(reason);
        var publication = Map.of("expectedRevision", 0, "requestId", key());
        assertThat(call("POST", "/me/articles/" + article + "/publish", publication, userToken).code)
                .isEqualTo("ACCESS_RESTRICTED");
        var commentInput = new LinkedHashMap<String, Object>();
        commentInput.put("requestId", key());
        commentInput.put("body", "仍有登录会话但认证已撤销");
        commentInput.put("imageIds", List.of());
        commentInput.put("replyToCommentId", null);
        assertThat(call("POST", "/articles/1001/comments", commentInput, userToken).code)
                .isEqualTo("ACCESS_RESTRICTED");
        assertThat(call("GET", "/articles/1001", null, userToken).status).isEqualTo(200);

        var issue = call("POST", ownRoute + "/reverification-codes", null, userToken);
        assertThat(issue.status).isEqualTo(202);
        assertThat(call("POST", ownRoute + "/reverification", Map.of(
                "challengeId", issue.data.path("challengeId").asString(),
                "code", MAILER.code(email, "CAMPUS_REVERIFY")), userToken).status).isEqualTo(200);
        assertThat(call("GET", ownRoute, null, userToken).status).isEqualTo(401);
        String fresh = context.getBean(AuthService.class).login(email, password).accessToken();
        assertThat(call("GET", ownRoute, null, fresh).data.path("state").asString()).isEqualTo("VALID");
        assertThat(call("GET", ownRoute, null, fresh).data.path("revocationReason").isNull()).isTrue();
        var latest = call("GET", adminRoute, null, token).data;
        assertThat(latest.path("revision").asLong()).isEqualTo(3);
        assertThat(latest.path("lastRevocation").path("reason").asString()).isEqualTo(reason);
        assertThat(call("POST", revoke, command, token).data.path("revision").asLong()).isEqualTo(2);
        assertThat(call("GET", adminRoute, null, token).data.path("state").asString()).isEqualTo("VALID");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM community_restriction WHERE user_id=? AND enabled=TRUE",
                Long.class, user)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT state FROM article WHERE id=?", String.class, article))
                .isEqualTo("ADMIN_HIDDEN");
    }
    @Test @Order(9) void revokedUsersCanPrivatelyAppealOneRevisionAndReadTheReview() throws Exception {
        var auth = context.getBean(AuthService.class);
        String session = auth.login(email, password).accessToken();
        var prior = call("GET", "/me/certification", null, session).data;
        long validRevision = prior.path("revision").asLong();
        var premature = Map.of("certificationRevision", validRevision, "requestId", key(), "reason", "请复核");
        assertThat(call("POST", "/me/appeals", premature, session).code).isEqualTo("CERTIFICATION_NOT_REVOKED");
        String revocationReason = "本次校园邮箱有效性需要复核";
        var revoked = call("POST", "/admin/users/" + user + "/certification/revocation",
                Map.of("expectedRevision", validRevision, "requestId", key(), "reason", revocationReason), token);
        assertThat(revoked.status).isEqualTo(200);
        long revision = revoked.data.path("revision").asLong();
        var certification = call("GET", "/me/certification", null, session).data;
        assertThat(certification.path("canAppeal").asBoolean()).isTrue();
        assertThat(certification.path("revision").asLong()).isEqualTo(revision);
        assertThat(call("GET", "/me/restrictions", null, session).status).isEqualTo(200);
        assertThat(call("GET", "/me/articles", null, session).code).isEqualTo("ACCESS_RESTRICTED");
        assertThat(call("POST", "/me/appeals", premature, session).code).isEqualTo("REVISION_CONFLICT");

        String appealReason = "仍在校，申请复核这次撤销";
        var command = Map.of("certificationRevision", revision, "requestId", key(), "reason", appealReason);
        assertThat(call("POST", "/me/appeals", command, null).status).isEqualTo(401);
        assertThat(call("POST", "/me/appeals", command, token).status).isEqualTo(401);
        assertThat(call("POST", "/me/appeals", Map.of("certificationRevision", revision,
                "restrictionId", 1, "requestId", key(), "reason", appealReason), session).status).isEqualTo(400);
        long appeal;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var replies = pool.invokeAll(List.<Callable<Reply>>of(() -> call("POST", "/me/appeals", command, session),
                    () -> call("POST", "/me/appeals", command, session)), 15, java.util.concurrent.TimeUnit.SECONDS);
            var first = replies.get(0).get();
            var second = replies.get(1).get();
            assertThat(first.status).isEqualTo(200);
            assertThat(second.status).isEqualTo(200);
            appeal = first.data.path("id").asLong();
            assertThat(second.data.path("id").asLong()).isEqualTo(appeal);
        }
        assertThat(call("POST", "/me/appeals", Map.of("certificationRevision", revision, "requestId", key(),
                "reason", appealReason), session).code).isEqualTo("CERTIFICATION_APPEAL_EXISTS");
        assertThat(call("GET", "/me/certification", null, session).data.path("appealId").asLong()).isEqualTo(appeal);
        assertThat(call("GET", "/me/certification", null, session).data.path("canAppeal").asBoolean()).isFalse();
        String own = "/me/appeals/" + appeal;
        var details = call("GET", own, null, session).data;
        assertThat(details.path("subject").asString()).isEqualTo("CAMPUS_CERTIFICATION");
        assertThat(details.path("reason").asString()).isEqualTo(appealReason);
        assertThat(details.path("certificationRevocationReason").asString()).isEqualTo(revocationReason);
        assertThat(details.has("decidedBy")).isFalse();
        assertThat(call("GET", "/admin/appeals", null, session).status).isEqualTo(401);

        String otherEmail = "g04" + key().replace("-", "") + "@stu.hzcu.edu.cn";
        var code = auth.requestCode(otherEmail, AuthModels.Purpose.REGISTER);
        long other = auth.register(otherEmail, password, code.challengeId(), MAILER.code(otherEmail, "REGISTER")).userId();
        try {
            String otherSession = auth.login(otherEmail, password).accessToken();
            assertThat(call("GET", own, null, otherSession).status).isEqualTo(404);
            assertThat(call("GET", "/me/appeals", null, otherSession).data.path("total").asLong()).isZero();
            assertThat(call("POST", "/me/appeals", command, otherSession).code).isEqualTo("CERTIFICATION_NOT_REVOKED");
        } finally {
            db.update("DELETE FROM auth_session WHERE user_id=?", other);
            db.update("DELETE FROM campus_certification WHERE user_id=?", other);
            db.update("DELETE FROM email_challenge WHERE email=?", otherEmail);
            db.update("DELETE FROM auth_email_gate WHERE email=?", otherEmail);
            db.update("DELETE FROM platform_user WHERE id=?", other);
        }

        String reply = "复核完成，请通过原校园邮箱确认身份";
        String adminAction = "/admin/appeals/" + appeal + "/decisions";
        var decision = Map.of("expectedRevision", 1, "requestId", key(), "outcome", "ACCEPTED", "reply", reply);
        assertThat(call("POST", adminAction, decision, session).status).isEqualTo(401);
        String trigger = "g04_appeal_" + key().replace("-", "");
        db.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON personal_notification FOR EACH ROW "
                + "BEGIN IF NEW.kind='APPEAL_DECIDED' AND NEW.source_id=" + appeal
                + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled'; END IF; END");
        try {
            assertThat(call("POST", adminAction, decision, token).status).isEqualTo(503);
            assertThat(call("GET", own, null, session).data.path("state").asString()).isEqualTo("OPEN");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM community_appeal_receipt WHERE appeal_id=?",
                    Long.class, appeal)).isZero();
        } finally {
            db.execute("DROP TRIGGER " + trigger);
        }
        assertThat(call("POST", adminAction, decision, token).data.path("state").asString()).isEqualTo("ACCEPTED");
        assertThat(call("POST", adminAction, decision, token).status).isEqualTo(200);
        assertThat(call("GET", own, null, session).data.path("reply").asString()).isEqualTo(reply);
        List<JsonNode> queue = new ArrayList<>();
        call("GET", "/admin/appeals?size=100", null, token).data.path("items").forEach(queue::add);
        assertThat(queue).filteredOn(item -> item.path("id").asLong() == appeal).singleElement()
                .satisfies(item -> {
                    assertThat(item.path("decidedBy").asLong()).isEqualTo(admin);
                    assertThat(item.path("userId").asLong()).isEqualTo(user);
                    assertThat(item.path("certificationRevision").asLong()).isEqualTo(revision);
                });
        List<JsonNode> inbox = new ArrayList<>();
        call("GET", "/me/notifications?size=100", null, session).data.path("items").forEach(inbox::add);
        assertThat(inbox).filteredOn(item -> "APPEAL_DECIDED".equals(item.path("kind").asString())
                && item.path("target").path("id").asLong() == appeal).singleElement()
                .satisfies(item -> assertThat(item.path("outcome").asString()).isEqualTo("ACCEPTED"));
        assertThat(call("GET", "/me/certification", null, session).data.path("state").asString()).isEqualTo("REVOKED");
        assertThat(call("GET", "/me/articles", null, session).status).isEqualTo(403);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM community_restriction WHERE user_id=? AND enabled=TRUE",
                Long.class, user)).isEqualTo(1);

        CLOCK.advance(java.time.Duration.ofMinutes(2));
        var issue = call("POST", "/me/certification/reverification-codes", null, session);
        assertThat(issue.status).isEqualTo(202);
        assertThat(call("POST", "/me/certification/reverification", Map.of(
                "challengeId", issue.data.path("challengeId").asString(), "code", MAILER.code(email, "CAMPUS_REVERIFY")),
                session).status).isEqualTo(200);
        assertThat(call("GET", own, null, session).status).isEqualTo(401);
        var fresh = auth.login(email, password).accessToken();
        assertThat(call("GET", own, null, fresh).data.path("state").asString()).isEqualTo("ACCEPTED");
    }

    @Test @Order(10) void mergingCategoriesKeepsHistoricalLinksAndFiltersAliasesThroughRepeatedMerges() throws Exception {
        long old = createMajor("曾用专业"), intermediate = createMajor("过渡专业"), canonical = createMajor("现用专业");
        String session = context.getBean(AuthService.class).login(email, password).accessToken();
        assertThat(call("PUT", "/me/profile", Map.of("expectedRevision", 0, "nickname", "测试用户",
                "majorId", old, "currentStage", "ENROLLED", "bio", ""), session).status).isEqualTo(200);
        long current = db.queryForObject("SELECT governance_revision FROM article WHERE id=?", Long.class, article);
        assertThat(call("POST", "/admin/articles/" + article + "/moderation",
                command("RESTORE", (int) current, key()), token).status).isEqualTo(200);
        db.update("INSERT INTO article_version_category(version_id,category_id) VALUES (?,?),(?,?)",
                version, old, version, intermediate);
        assertThat(call("GET", "/articles?majorId=" + canonical, null, null).data.path("total").asLong()).isZero();

        String route = "/admin/categories/" + old + "/merge";
        String requestId = key();
        var first = mergeCommand(intermediate, 1, 1, requestId);
        assertThat(call("POST", route, first, session).status).isEqualTo(401);
        assertThat(call("POST", route, mergeCommand(old, 1, 1, key()), token).status).isEqualTo(400);
        assertThat(call("POST", route, mergeCommand(1, 1, 0, key()), token).status).isEqualTo(400);
        assertThat(call("POST", route, mergeCommand(intermediate, 1, 0, key()), token).code)
                .isEqualTo("REVISION_CONFLICT");
        var receipt = call("POST", route, first, token);
        assertThat(receipt.status).isEqualTo(200);
        assertThat(receipt.data.path("canonicalId").asLong()).isEqualTo(intermediate);
        assertThat(call("POST", route, first, token).data).isEqualTo(receipt.data);
        assertThat(call("POST", route, mergeCommand(canonical, 1, 1, requestId), token).code)
                .isEqualTo("REQUEST_KEY_REUSED");
        assertThat(call("POST", "/admin/categories/" + canonical + "/merge", mergeCommand(old, 1, 2, key()), token).code)
                .isEqualTo("CATEGORY_TARGET_UNAVAILABLE");

        String nextRoute = "/admin/categories/" + intermediate + "/merge";
        var next = mergeCommand(canonical, 1, 1, key());
        String trigger = "g04_merge_" + key().replace("-", "");
        db.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON governance_event FOR EACH ROW "
                + "BEGIN IF NEW.action='MERGE' AND NEW.target_id=" + intermediate
                + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled'; END IF; END");
        try {
            assertThat(call("POST", nextRoute, next, token).status).isEqualTo(503);
            assertThat(db.queryForObject("SELECT canonical_id FROM category WHERE id=?", Long.class, old))
                    .isEqualTo(intermediate);
            assertThat(db.queryForObject("SELECT enabled FROM category WHERE id=?", Boolean.class, intermediate)).isTrue();
        } finally {
            db.execute("DROP TRIGGER " + trigger);
        }
        assertThat(call("POST", nextRoute, next, token).status).isEqualTo(200);
        assertThat(call("POST", route, first, token).data).isEqualTo(receipt.data);
        for (long id : List.of(old, intermediate, canonical)) {
            var page = call("GET", "/articles?majorId=" + id, null, null).data;
            assertThat(page.path("total").asLong()).isEqualTo(1);
            assertThat(page.path("items").get(0).path("id").asLong()).isEqualTo(article);
        }
        assertThat(call("GET", "/articles?sameMajor=true", null, session).data.path("items").get(0)
                .path("id").asLong()).isEqualTo(article);
        assertThat(call("GET", "/articles?jobId=" + old, null, null).data.path("total").asLong()).isZero();
        var catalog = call("GET", "/catalog", null, null).data.path("majors").toString();
        assertThat(catalog).contains("现用专业").doesNotContain("曾用专业", "过渡专业");
        assertThat(db.queryForList("SELECT category_id FROM article_version_category WHERE version_id=? ORDER BY category_id",
                Long.class, version)).containsExactly(old, intermediate);
        assertThat(call("GET", "/articles/" + article, null, null).data.path("categories").toString())
                .contains("曾用专业", "过渡专业");
        assertThat(call("GET", "/admin/governance-events?size=100", null, token).data.path("items").toString())
                .contains("\"action\":\"MERGE\"", "\"canonicalId\":" + canonical);
    }

    private static long createMajor(String name) throws Exception {
        var response = call("POST", "/admin/categories", Map.of("kind", "MAJOR", "name", name, "enabled", true,
                "displayOrder", 1, "expectedRevision", 0, "requestId", key(), "reason", "分类归并测试"), token);
        assertThat(response.status).isEqualTo(200);
        long id = response.data.path("targetId").asLong();
        MERGE_CATEGORIES.add(id);
        return id;
    }

    private static Map<String, Object> mergeCommand(long canonical, long sourceRevision, long targetRevision, String key) {
        return Map.of("canonicalId", canonical, "expectedRevision", sourceRevision,
                "expectedCanonicalRevision", targetRevision, "requestId", key, "reason", "合并同义专业标签");
    }

    @AfterAll static void cleanup(){if(context==null)return;try{db.update("DELETE FROM author_profile WHERE user_id=?",user);db.update("DELETE FROM article_version_category WHERE version_id=?",version);for(long id:MERGE_CATEGORIES)db.update("UPDATE category SET canonical_id=NULL WHERE id=?",id);for(long id:MERGE_CATEGORIES)db.update("DELETE FROM category WHERE id=?",id);db.update("DELETE FROM campus_certification_revocation WHERE user_id=?",user);db.update("DELETE FROM community_appeal_receipt WHERE admin_id=?",admin);db.update("DELETE FROM community_appeal WHERE user_id=?",user);db.update("DELETE FROM community_restriction_event WHERE admin_id=?",admin);db.update("DELETE FROM community_restriction WHERE user_id=?",user);for(String table:List.of("community_admin_receipt","community_case_history","community_case_comment","community_case_media"))db.update("DELETE FROM "+table+" WHERE case_id IN (SELECT id FROM community_case WHERE user_id=?)",user);db.update("DELETE FROM community_case WHERE user_id=?",user);db.update("DELETE FROM business_audit WHERE actor_id=? AND actor_domain='USER'",user);db.update("DELETE FROM business_audit WHERE actor_id=? AND actor_domain='COMMUNITY_ADMIN'",admin);db.update("DELETE FROM comment_create_receipt WHERE user_id=?",user);db.update("DELETE FROM governance_event WHERE admin_id=?",admin);db.update("DELETE FROM governance_article_hold WHERE article_id=?",article);db.update("DELETE FROM discussion_comment WHERE article_id=?",article);db.update("DELETE FROM article_draft WHERE article_id=?",article);db.update("UPDATE article SET state='DELETED',public_version_id=NULL WHERE id=?",article);db.update("DELETE FROM article_version WHERE id=?",version);db.update("DELETE FROM article WHERE id=?",article);if(category!=null)db.update("DELETE FROM category WHERE id=?",category);db.update("DELETE FROM community_admin_session WHERE admin_id=?",admin);db.update("DELETE FROM community_admin WHERE id=?",admin);db.update("DELETE FROM community_admin_login_gate WHERE name_digest=?",AuthCrypto.tokenDigest(login));for(String table:List.of("auth_session","campus_certification"))db.update("DELETE FROM "+table+" WHERE user_id=?",user);for(String table:List.of("email_challenge","auth_email_gate"))db.update("DELETE FROM "+table+" WHERE email=?",email);db.update("DELETE FROM platform_user WHERE id=?",user);}finally{context.close();}}
}
