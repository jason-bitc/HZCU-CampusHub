package cn.edu.hzcu.campushub.comment;

import cn.edu.hzcu.campushub.auth.*;
import cn.edu.hzcu.campushub.audit.SafeFileLogs;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
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

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommentsMySqlIT {
    @TempDir static Path root;
    static final AuthTestSupport.ControlledMailer MAIL=new AuthTestSupport.ControlledMailer();
    static final AuthTestSupport.MutableClock CLOCK=new AuthTestSupport.MutableClock();
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final HttpClient HTTP=HttpClient.newHttpClient();
    static ConfigurableApplicationContext context;
    static JdbcTemplate db;
    static String base,ownerToken,readerToken,thirdToken,password;
    static long owner,reader,third,articleA,articleB,lastingComment;
    static String lastingImage,thirdEmail;
    static boolean diagnostics;
    static byte[] png;
    static final List<Long> users=new ArrayList<>();
    static final List<String> emails=new ArrayList<>(),secrets=new ArrayList<>();
    static List<Map<String,Object>> baseline;
    static long commentBaseline,imageBaseline,auditBaseline;
    record Reply(int status,JsonNode data,String trace,String raw){}
    static void boot(){
        var app=AuthTestSupport.application(MAIL,CLOCK);app.addInitializers(ctx->ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("comments-test",Map.of("campushub.media.root",root.resolve("media").toString(),"campushub.logs.enabled","true","campushub.logs.root",root.resolve("logs").toString(),"campushub.logs.diagnostics-enabled",diagnostics))));
        context=app.run("--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=demo");base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");db=context.getBean(JdbcTemplate.class);
    }
    @BeforeAll static void start()throws Exception{
        boot();assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("campushub_test");baseline=db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes,count_origin FROM article ORDER BY id");commentBaseline=count("discussion_comment");imageBaseline=count("comment_image");auditBaseline=count("business_audit");
        password=key();secrets.add(password);String email=email();owner=register(email);ownerToken=login(email);email=email();reader=register(email);readerToken=login(email);thirdEmail=email();third=register(thirdEmail);thirdToken=login(thirdEmail);png=png(8);
        articleA=article();articleB=article();
    }
    static String key(){return UUID.randomUUID().toString();}
    static String email(){String email="b5"+key().replace("-","")+"@stu.hzcu.edu.cn";emails.add(email);secrets.add(email);return email;}
    static long register(String email){var auth=context.getBean(AuthService.class);var challenge=auth.requestCode(email,AuthModels.Purpose.REGISTER);String code=MAIL.code(email,"REGISTER");secrets.add(code);long id=auth.register(email,password,challenge.challengeId(),code).userId();users.add(id);return id;}
    static String login(String email){String token=context.getBean(AuthService.class).login(email,password).accessToken();secrets.add(token);return token;}
    static byte[] png(int size)throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
    static long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    static Reply response(HttpResponse<byte[]> response,boolean restricted){
        var json=JSON.readTree(response.body());String trace=json.path("traceId").asString();assertThat(trace).matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");assertThat(response.headers().firstValue("X-Trace-Id")).contains(trace);
        if(restricted)assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        String raw=new String(response.body(),StandardCharsets.UTF_8);if(response.statusCode()>=400){assertThat(json.has("data")).isFalse();for(String secret:secrets)assertThat(raw.contains(secret)).isFalse();}
        return new Reply(response.statusCode(),json.path("data"),trace,raw);
    }
    static Reply call(String method,String path,Object body,String token)throws Exception{
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).expectContinue(body!=null).header("X-Trace-Id","untrusted-input");if(token!=null)request.header("Authorization","Bearer "+token);if(body!=null)request.header("Content-Type","application/json");
        var r=HTTP.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body instanceof String s?s:JSON.writeValueAsString(body),StandardCharsets.UTF_8)).build(),HttpResponse.BodyHandlers.ofByteArray());
        boolean restricted=path.contains("comment")||path.startsWith("/api/v1/me/")||path.startsWith("/api/v1/authors/");return response(r,restricted);
    }
    static Reply get(String path,String token)throws Exception{return call("GET",path,null,token);}
    static Map<String,Object> input(String request,String body,List<String> images,Long reply){var n=new LinkedHashMap<String,Object>();n.put("requestId",request);n.put("body",body);n.put("imageIds",images);n.put("replyToCommentId",reply);return n;}
    static String text(){String text="comment-payload-"+key();secrets.add(text);return text;}
    static Reply create(long article,String request,String body,List<String> images,Long reply,String token)throws Exception{return call("POST","/api/v1/articles/"+article+"/comments",input(request,body,images,reply),token);}
    static long post(long article,String token,Long reply)throws Exception{var r=create(article,key(),text(),List.of(),reply,token);assertThat(r.status).isEqualTo(201);return r.data.path("commentId").asLong();}
    static Reply roots(long article,String token)throws Exception{return get("/api/v1/articles/"+article+"/comments?size=100",token);}
    static Reply replies(long rootId,String token)throws Exception{return get("/api/v1/comments/"+rootId+"/replies?size=100",token);}
    static JsonNode item(Reply page,long id){for(var node:page.data.path("items"))if(node.path("commentId").asLong()==id)return node;throw new AssertionError("Expected own fixture comment missing");}
    static Reply delete(long id,String token)throws Exception{return call("DELETE","/api/v1/comments/"+id,null,token);}
    static Reply upload(long article,String request,String filename,String mime,byte[] bytes,String token)throws Exception{
        String boundary="boundary-"+key();var out=new ByteArrayOutputStream();out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n"+request+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var r=HttpRequest.newBuilder(URI.create(base+"/api/v1/articles/"+article+"/comment-images")).timeout(Duration.ofSeconds(30)).expectContinue(true).header("Content-Type","multipart/form-data; boundary="+boundary);if(token!=null)r.header("Authorization","Bearer "+token);
        return response(HTTP.send(r.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofByteArray()),true);
    }
    static String upload(long article,String token)throws Exception{var r=upload(article,key(),"fixture.png","image/png",png,token);assertThat(r.status).isEqualTo(200);return r.data.path("imageId").asString();}
    static HttpResponse<byte[]> image(String id,String token)throws Exception{var r=HttpRequest.newBuilder(URI.create(base+"/api/v1/media/comment-images/"+id)).timeout(Duration.ofSeconds(10));if(token!=null)r.header("Authorization","Bearer "+token);var response=HTTP.send(r.GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");return response;}
    static long article()throws Exception{
        var created=call("POST","/api/v1/me/articles",Map.of("requestId",key()),ownerToken);long id=created.data.path("articleId").asLong();
        var d=new LinkedHashMap<String,Object>();d.put("expectedRevision",0);d.put("title","【DEMO】评论后端测试");d.put("summary","");d.put("body","后端验证示例");d.put("jobIds",List.of(1));d.put("majorIds",List.of(11));d.put("companyIds",List.of());d.put("topicIds",List.of());d.put("experienceStage","DEMO阶段");d.put("sourceType","DEMO");d.put("sourceNote","非真实经历");d.put("sourceLinks",List.of());
        assertThat(call("PUT","/api/v1/me/articles/"+id+"/draft",d,ownerToken).status).isEqualTo(200);assertThat(call("POST","/api/v1/me/articles/"+id+"/publish",Map.of("expectedRevision",1,"requestId",key()),ownerToken).status).isEqualTo(200);return id;
    }
    static long objectFiles()throws Exception{try(var files=Files.list(root.resolve("media/objects"))){return files.count();}}
    interface Checked {void run()throws Exception;}
    static void auditFailure(Checked test)throws Exception{String marker="sql-message-secret-"+key();secrets.add(marker);db.execute("CREATE TRIGGER g05_comment_audit_failure BEFORE INSERT ON business_audit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='"+marker+"'");try{test.run();}finally{db.execute("DROP TRIGGER g05_comment_audit_failure");}}
    @AfterAll static void cleanup(){
        if(context==null)return;
        try{
            var named=context.getBean(NamedParameterJdbcTemplate.class);var p=new MapSqlParameterSource("users",users).addValue("emails",emails);
            if(!users.isEmpty()){
                named.update("DELETE FROM business_audit WHERE actor_id IN (:users)",p);named.update("DELETE FROM comment_create_receipt WHERE user_id IN (:users)",p);named.update("DELETE FROM comment_image WHERE user_id IN (:users)",p);
                for(long id:named.queryForList("SELECT id FROM discussion_comment WHERE user_id IN (:users) ORDER BY id DESC",p,Long.class))db.update("DELETE FROM discussion_comment WHERE id=?",id);
                var articles=named.queryForList("SELECT id FROM article WHERE owner_user_id IN (:users)",p,Long.class);
                if(!articles.isEmpty()){
                    p.addValue("articles",articles);for(String table:List.of("article_publish_request","article_version_image","article_draft_image","article_draft_category","article_draft_source_link","author_create_request","media_image","article_draft"))named.update("DELETE FROM "+table+" WHERE article_id IN (:articles)",p);
                    named.update("UPDATE article SET public_version_id=NULL,state='DELETED' WHERE id IN (:articles)",p);named.update("DELETE FROM article_version_category WHERE version_id IN (SELECT id FROM article_version WHERE article_id IN (:articles))",p);named.update("DELETE FROM article_source_link WHERE version_id IN (SELECT id FROM article_version WHERE article_id IN (:articles))",p);named.update("DELETE FROM article_version WHERE article_id IN (:articles)",p);named.update("DELETE FROM article WHERE id IN (:articles)",p);
                }
                for(String table:List.of("author_contact_setting","author_profile","auth_session","campus_certification"))named.update("DELETE FROM "+table+" WHERE user_id IN (:users)",p);named.update("DELETE FROM email_challenge WHERE email IN (:emails)",p);named.update("DELETE FROM auth_email_gate WHERE email IN (:emails)",p);named.update("DELETE FROM platform_user WHERE id IN (:users)",p);
            }
            assertThat(db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes,count_origin FROM article ORDER BY id")).isEqualTo(baseline);assertThat(count("discussion_comment")).isEqualTo(commentBaseline);assertThat(count("comment_image")).isEqualTo(imageBaseline);assertThat(count("business_audit")).isEqualTo(auditBaseline);
        }finally{context.close();}
    }
    @Test @Order(1) void nonMeRoutesAuthenticateBeforeTargetsAndFailuresNeverCache()throws Exception{
        for(String path:List.of("/api/v1/articles/1001/comments","/api/v1/articles/not-an-id/comments","/api/v1/comments/99999/replies","/api/v1/media/comment-images/"+key())){assertThat(get(path,null).status).isEqualTo(401);assertThat(get(path,"invalid").status).isEqualTo(401);}
        assertThat(delete(999999,null).status).isEqualTo(401);assertThat(upload(1001,key(),"fixture.png","image/png",png,null).status).isEqualTo(401);assertThat(get("/api/v1/articles/not-an-id/comments",readerToken).status).isEqualTo(400);
        db.update("UPDATE campus_certification SET state='REVOKED' WHERE user_id=?",reader);try{assertThat(roots(1001,readerToken).status).isEqualTo(403);assertThat(image(key(),readerToken).statusCode()).isEqualTo(403);assertThat(create(1001,key(),text(),List.of(),null,readerToken).status).isEqualTo(403);}finally{db.update("UPDATE campus_certification SET state='VALID' WHERE user_id=?",reader);}
        db.update("UPDATE platform_user SET account_state='RESTRICTED' WHERE id=?",reader);try{assertThat(roots(999999,readerToken).status).isEqualTo(403);}finally{db.update("UPDATE platform_user SET account_state='ACTIVE' WHERE id=?",reader);}
        call("POST","/api/v1/auth/logout",null,thirdToken);assertThat(roots(1001,thirdToken).status).isEqualTo(401);thirdToken=login(thirdEmail);assertThat(get("/api/v1/articles/1001",null).status).isEqualTo(200);
    }
    @Test @Order(2) void textImageAndMixedCommentsWorkWithoutAnAuthorHome()throws Exception{
        long textComment=post(articleA,readerToken,null);var visible=item(roots(articleA,thirdToken),textComment);assertThat(visible.path("bodyFormat").asString()).isEqualTo("PLAIN_TEXT");assertThat(visible.path("commenter").path("nickname").asString()).isEqualTo("城院同学");assertThat(visible.path("commenter").has("authorId")).isFalse();assertThat(get("/api/v1/authors/"+reader,ownerToken).status).isEqualTo(404);
        long authorComment=post(articleA,ownerToken,null);assertThat(item(roots(articleA,readerToken),authorComment).path("commenter").path("authorId").asLong()).isEqualTo(owner);
        lastingImage=upload(articleA,readerToken);assertThat(image(lastingImage,readerToken).body()).isEqualTo(png);assertThat(image(lastingImage,thirdToken).statusCode()).isEqualTo(404);assertThat(image(lastingImage,null).statusCode()).isEqualTo(401);assertThat(get("/api/v1/media/images/"+lastingImage,null).status).isEqualTo(404);
        var imageOnly=create(articleA,key(),"",List.of(lastingImage),null,readerToken);assertThat(imageOnly.status).isEqualTo(201);lastingComment=imageOnly.data.path("commentId").asLong();assertThat(image(lastingImage,thirdToken).body()).isEqualTo(png);assertThat(image(lastingImage,thirdToken).headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        String another=upload(articleA,thirdToken);assertThat(create(articleA,key(),text(),List.of(another),null,thirdToken).status).isEqualTo(201);assertThat(post(1001,readerToken,null)).isPositive();assertThat(get("/api/v1/articles/1001/author",readerToken).status).isEqualTo(404);
        assertThat(roots(articleA,thirdToken).raw).doesNotContain("email","password","role","contact","verifiedAt");
    }
    @Test @Order(3) void flatRepliesKeepRelationsAndDeterministicPages()throws Exception{
        long parent=post(articleA,readerToken,null),child=post(articleA,ownerToken,parent),grandchild=post(articleA,thirdToken,child);
        var page=replies(parent,readerToken);assertThat(page.data.path("total").asLong()).isEqualTo(2);assertThat(item(page,grandchild).path("rootCommentId").asLong()).isEqualTo(parent);assertThat(item(page,grandchild).path("replyToCommentId").asLong()).isEqualTo(child);assertThat(item(page,grandchild).path("replyTo").path("available").asBoolean()).isTrue();
        assertThat(get("/api/v1/comments/"+parent+"/replies?size=1&page=1",readerToken).data.path("items").get(0).path("commentId").asLong()).isEqualTo(child);assertThat(get("/api/v1/comments/"+parent+"/replies?size=1&page=2",readerToken).data.path("items").get(0).path("commentId").asLong()).isEqualTo(grandchild);
        assertThat(replies(child,readerToken).status).isEqualTo(404);assertThat(create(articleB,key(),text(),List.of(),child,readerToken).status).isEqualTo(404);assertThat(create(articleA,key(),text(),List.of(),999999L,readerToken).status).isEqualTo(404);
        var forged=input(key(),text(),List.of(),parent);forged.put("rootCommentId",child);assertThat(call("POST","/api/v1/articles/"+articleA+"/comments",forged,readerToken).status).isEqualTo(400);
        long newest=post(articleA,readerToken,null);assertThat(get("/api/v1/articles/"+articleA+"/comments?size=1",readerToken).data.path("items").get(0).path("commentId").asLong()).isEqualTo(newest);
    }
    @Test @Order(4) void creationReceiptsCompareNormalizedContentAndSerializeImageClaims()throws Exception{
        String request=key(),body=text();long before=count("discussion_comment"),auditBefore=count("business_audit");var first=create(articleA,request,body+"\r\nline",List.of(),null,readerToken);var repeated=create(articleA,request,body+"\nline",List.of(),null,readerToken);assertThat(repeated.data).isEqualTo(first.data);assertThat(count("discussion_comment")).isEqualTo(before+1);assertThat(count("business_audit")).isEqualTo(auditBefore+1);
        assertThat(create(articleA,request,"different",List.of(),null,readerToken).status).isEqualTo(409);assertThat(create(articleA,request,body+"\nline",List.of(key()),null,readerToken).status).isEqualTo(409);assertThat(create(articleB,request,body+"\nline",List.of(),null,readerToken).status).isEqualTo(409);assertThat(create(articleA,request,body+"\nline",List.of(),first.data.path("commentId").asLong(),readerToken).status).isEqualTo(409);
        String image=upload(articleA,readerToken),concurrent=key();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var results=pool.invokeAll(List.<Callable<Reply>>of(()->create(articleA,concurrent,body,List.of(image),null,readerToken),()->create(articleA,concurrent,body,List.of(image),null,readerToken)));assertThat(results.get(0).get().status).isEqualTo(201);assertThat(results.get(1).get().data).isEqualTo(results.get(0).get().data);
            String claimed=upload(articleA,readerToken);var claims=pool.invokeAll(List.<Callable<Integer>>of(()->create(articleA,key(),body,List.of(claimed),null,readerToken).status,()->create(articleA,key(),body,List.of(claimed),null,readerToken).status));assertThat(List.of(claims.get(0).get(),claims.get(1).get())).containsExactlyInAnyOrder(201,409);
        }
        assertThat(create(articleA,request,body+"\nline",List.of(),null,thirdToken).status).isEqualTo(201);
    }
    @Test @Order(5) void uploadReceiptsAndNamespacesEnforceOwnershipAndPermanentBinding()throws Exception{
        String request=key(),id;long before=objectFiles();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var results=pool.invokeAll(List.<Callable<Reply>>of(()->upload(articleA,request,"fixture.png","image/png",png,readerToken),()->upload(articleA,request,"fixture.png","image/png",png,readerToken)));var a=results.get(0).get();var b=results.get(1).get();assertThat(a.status).isEqualTo(200);assertThat(b.data).isEqualTo(a.data);id=a.data.path("imageId").asString();
        }
        assertThat(objectFiles()).isEqualTo(before+1);assertThat(upload(articleA,request,"fixture.png","image/png",png(4),readerToken).status).isEqualTo(409);assertThat(upload(articleB,request,"fixture.png","image/png",png,readerToken).status).isEqualTo(409);
        assertThat(create(articleA,key(),"",List.of(id),null,thirdToken).status).isEqualTo(404);String elsewhere=upload(articleB,readerToken);assertThat(create(articleA,key(),"",List.of(elsewhere),null,readerToken).status).isEqualTo(404);
        String boundary="author-image-"+key();var out=new ByteArrayOutputStream();out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n"+key()+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"author.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(png);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var authorUpload=HTTP.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/me/articles/"+articleA+"/images")).header("Authorization","Bearer "+ownerToken).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofByteArray());var authored=response(authorUpload,true);assertThat(authored.status).isEqualTo(200);assertThat(create(articleA,key(),"",List.of(authored.data.path("imageId").asString()),null,ownerToken).status).isEqualTo(404);
        var attached=create(articleA,key(),"",List.of(id),null,readerToken);assertThat(attached.status).isEqualTo(201);assertThat(create(articleA,key(),"",List.of(id),null,readerToken).status).isEqualTo(409);assertThat(create(articleA,key(),"",List.of(id,id),null,readerToken).status).isEqualTo(400);
    }
    @Test @Order(6) void deletingParentsLeavesPlaceholdersRepliesAndImagePermissionsIntact()throws Exception{
        String rootImage=upload(articleA,readerToken);long parent=create(articleA,key(),text(),List.of(rootImage),null,readerToken).data.path("commentId").asLong();String childImage=upload(articleA,ownerToken);long child=create(articleA,key(),text(),List.of(childImage),parent,ownerToken).data.path("commentId").asLong();long grandchild=post(articleA,thirdToken,child);
        assertThat(delete(parent,ownerToken).status).isEqualTo(404);var deleted=delete(parent,readerToken);assertThat(deleted.status).isEqualTo(200);assertThat(deleted.data.size()).isEqualTo(2);long audits=count("business_audit");assertThat(delete(parent,readerToken).data).isEqualTo(deleted.data);assertThat(count("business_audit")).isEqualTo(audits);
        var placeholder=item(roots(articleA,thirdToken),parent);assertThat(placeholder.path("available").asBoolean()).isFalse();assertThat(placeholder.has("body")).isFalse();assertThat(placeholder.has("images")).isFalse();assertThat(placeholder.has("commenter")).isFalse();assertThat(placeholder.has("state")).isFalse();assertThat(placeholder.path("replyCount").asLong()).isEqualTo(2);
        assertThat(image(rootImage,readerToken).statusCode()).isEqualTo(404);assertThat(image(childImage,thirdToken).statusCode()).isEqualTo(200);assertThat(create(articleA,key(),text(),List.of(),parent,thirdToken).status).isEqualTo(404);assertThat(create(articleA,key(),text(),List.of(),child,thirdToken).status).isEqualTo(201);
        delete(child,ownerToken);var thread=replies(parent,readerToken);assertThat(thread.data.path("total").asLong()).isEqualTo(3);assertThat(item(thread,grandchild).path("available").asBoolean()).isTrue();assertThat(item(thread,grandchild).path("replyTo").path("available").asBoolean()).isFalse();assertThat(image(childImage,ownerToken).statusCode()).isEqualTo(404);
        long hidden=post(articleA,readerToken,null),reply=post(articleA,thirdToken,hidden);db.update("UPDATE discussion_comment SET state='ADMIN_HIDDEN' WHERE id=?",hidden);assertThat(item(roots(articleA,thirdToken),hidden).has("body")).isFalse();assertThat(item(replies(hidden,readerToken),reply).path("available").asBoolean()).isTrue();assertThat(create(articleA,key(),text(),List.of(),hidden,thirdToken).status).isEqualTo(404);assertThat(delete(hidden,readerToken).status).isEqualTo(200);
    }
    @Test @Order(7) void articleWithdrawalRevokesOldEntrancesButOwnKnownDeletionStillWorks()throws Exception{
        String image=upload(articleA,readerToken);long id=create(articleA,key(),text(),List.of(image),null,readerToken).data.path("commentId").asLong(),reply=post(articleA,thirdToken,id);
        assertThat(call("POST","/api/v1/me/articles/"+articleA+"/unpublish",Map.of("expectedRevision",2),ownerToken).status).isEqualTo(200);assertThat(roots(articleA,readerToken).status).isEqualTo(404);assertThat(replies(id,readerToken).status).isEqualTo(404);assertThat(image(image,readerToken).statusCode()).isEqualTo(404);assertThat(create(articleA,key(),text(),List.of(),null,readerToken).status).isEqualTo(404);assertThat(upload(articleA,key(),"fixture.png","image/png",png,readerToken).status).isEqualTo(404);assertThat(delete(id,readerToken).status).isEqualTo(200);
        assertThat(call("POST","/api/v1/me/articles/"+articleA+"/restore",Map.of("expectedRevision",3),ownerToken).status).isEqualTo(200);assertThat(item(replies(id,readerToken),reply).path("available").asBoolean()).isTrue();assertThat(image(image,readerToken).statusCode()).isEqualTo(404);
        db.update("UPDATE article SET state='ADMIN_HIDDEN' WHERE id=?",articleA);try{assertThat(roots(articleA,readerToken).status).isEqualTo(404);assertThat(image(lastingImage,readerToken).statusCode()).isEqualTo(404);assertThat(delete(reply,thirdToken).status).isEqualTo(200);}finally{db.update("UPDATE article SET state='PUBLISHED' WHERE id=?",articleA);}
        long deletedArticle=article();String deletedImage=upload(deletedArticle,readerToken);long deletedComment=create(deletedArticle,key(),text(),List.of(deletedImage),null,readerToken).data.path("commentId").asLong();call("DELETE","/api/v1/me/articles/"+deletedArticle+"?expectedRevision=2",null,ownerToken);assertThat(roots(deletedArticle,readerToken).status).isEqualTo(404);assertThat(image(deletedImage,readerToken).statusCode()).isEqualTo(404);assertThat(delete(deletedComment,readerToken).status).isEqualTo(200);
    }
    @Test @Order(8) void restrictedCommenterLosesProfileButVisibleHistoricalContentRemains()throws Exception{
        long id=post(articleA,readerToken,null);String body=item(roots(articleA,thirdToken),id).path("body").asString();
        db.update("UPDATE campus_certification SET state='REVOKED' WHERE user_id=?",reader);try{var item=item(roots(articleA,thirdToken),id);assertThat(item.path("body").asString()).isEqualTo(body);assertThat(item.path("commenter").isNull()).isTrue();assertThat(image(lastingImage,thirdToken).statusCode()).isEqualTo(200);assertThat(delete(id,readerToken).status).isEqualTo(403);}finally{db.update("UPDATE campus_certification SET state='VALID' WHERE user_id=?",reader);}
        db.update("UPDATE platform_user SET account_state='RESTRICTED' WHERE id=?",owner);try{long own=db.queryForObject("SELECT id FROM discussion_comment WHERE user_id=? AND state='VISIBLE' AND root_comment_id IS NULL ORDER BY id LIMIT 1",Long.class,owner);assertThat(item(roots(articleA,thirdToken),own).path("commenter").isNull()).isTrue();}finally{db.update("UPDATE platform_user SET account_state='ACTIVE' WHERE id=?",owner);}
    }
    @Test @Order(9) void auditFailureRollsBackCommentReceiptBindingAndDeletion()throws Exception{
        String image=upload(articleA,readerToken),request=key(),body=text();long before=count("discussion_comment"),receipts=count("comment_create_receipt"),audits=count("business_audit");
        auditFailure(()->assertThat(create(articleA,request,body,List.of(image),null,readerToken).status).isEqualTo(503));assertThat(count("discussion_comment")).isEqualTo(before);assertThat(count("comment_create_receipt")).isEqualTo(receipts);assertThat(count("business_audit")).isEqualTo(audits);assertThat(db.queryForObject("SELECT comment_id FROM comment_image WHERE id=?",Long.class,image)).isNull();assertThat(image(image,thirdToken).statusCode()).isEqualTo(404);
        var created=create(articleA,request,body,List.of(image),null,readerToken);assertThat(created.status).isEqualTo(201);long id=created.data.path("commentId").asLong();assertThat(db.queryForObject("SELECT COUNT(*) FROM business_audit WHERE trace_id=? AND action='COMMENT_CREATE'",Long.class,created.trace)).isEqualTo(1);
        auditFailure(()->assertThat(delete(id,readerToken).status).isEqualTo(503));assertThat(item(roots(articleA,thirdToken),id).path("available").asBoolean()).isTrue();assertThat(image(image,thirdToken).statusCode()).isEqualTo(200);assertThat(delete(id,readerToken).status).isEqualTo(200);assertThat(image(image,readerToken).statusCode()).isEqualTo(404);
        var oldReceipt=create(articleA,request,body,List.of(image),null,readerToken);assertThat(oldReceipt.data).isEqualTo(created.data);assertThat(oldReceipt.data.has("body")).isFalse();assertThat(item(roots(articleA,thirdToken),id).path("available").asBoolean()).isFalse();
    }
    @Test @Order(10) void fileAndMetadataFailuresPreserveExistingObjectsAndAllowRetry()throws Exception{
        long before=objectFiles(),metadata=count("comment_image");db.execute("CREATE TRIGGER g05_comment_image_failure BEFORE INSERT ON comment_image FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled image metadata failure'");
        try{assertThat(upload(articleA,key(),"fixture.png","image/png",png,readerToken).status).isEqualTo(503);}finally{db.execute("DROP TRIGGER g05_comment_image_failure");}assertThat(objectFiles()).isEqualTo(before);assertThat(count("comment_image")).isEqualTo(metadata);
        Path temporary=root.resolve("media/temporary");Files.delete(temporary);Files.writeString(temporary,"preserved-test-blocker");try{assertThat(upload(articleA,key(),"fixture.png","image/png",png,readerToken).status).isEqualTo(503);assertThat(Files.readString(temporary)).isEqualTo("preserved-test-blocker");}finally{Files.delete(temporary);Files.createDirectory(temporary);}assertThat(objectFiles()).isEqualTo(before);
        String id=upload(articleA,readerToken),request=key(),body=text();Path file=root.resolve("media/objects").resolve(id);byte[] original=Files.readAllBytes(file);Files.write(file,new byte[]{1});long rows=count("discussion_comment");try{assertThat(create(articleA,request,body,List.of(id),null,readerToken).status).isEqualTo(503);assertThat(image(id,readerToken).statusCode()).isEqualTo(503);}finally{Files.write(file,original);}assertThat(count("discussion_comment")).isEqualTo(rows);assertThat(create(articleA,request,body,List.of(id),null,readerToken).status).isEqualTo(201);assertThat(image(lastingImage,thirdToken).body()).isEqualTo(png);
    }
    @Test @Order(11) void concurrentWithdrawalAndDeletionCannotAcceptLateWrites()throws Exception{
        var source=context.getBean(DataSource.class);String request=key(),body=text();long before=count("discussion_comment");
        try(var pool=Executors.newVirtualThreadPerTaskExecutor();var connection=source.getConnection()){
            connection.setAutoCommit(false);try(var statement=connection.prepareStatement("UPDATE article SET state='AUTHOR_OFFLINE' WHERE id=?")){statement.setLong(1,articleA);statement.executeUpdate();}
            var future=pool.submit(()->create(articleA,request,body,List.of(),null,readerToken));Thread.sleep(100);assertThat(future.isDone()).isFalse();connection.commit();assertThat(future.get().status).isEqualTo(404);
        }finally{db.update("UPDATE article SET state='PUBLISHED' WHERE id=?",articleA);}assertThat(count("discussion_comment")).isEqualTo(before);
        long parent=post(articleA,readerToken,null),child=post(articleA,thirdToken,parent);try(var pool=Executors.newVirtualThreadPerTaskExecutor();var connection=source.getConnection()){
            connection.setAutoCommit(false);try(var statement=connection.prepareStatement("UPDATE discussion_comment SET state='DELETED',revision=revision+1 WHERE id=?")){statement.setLong(1,parent);statement.executeUpdate();}
            try(var statement=connection.prepareStatement("SELECT id FROM article WHERE id=? FOR UPDATE")){statement.setLong(1,articleA);statement.executeQuery().close();}
            var future=pool.submit(()->create(articleA,key(),body,List.of(),parent,readerToken));Thread.sleep(100);connection.commit();assertThat(future.get().status).isEqualTo(404);
        }
        assertThat(item(replies(parent,readerToken),child).path("available").asBoolean()).isTrue();long own=post(articleA,readerToken,null),audits=count("business_audit");try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var deleted=pool.invokeAll(List.<Callable<Integer>>of(()->delete(own,readerToken).status,()->delete(own,readerToken).status));for(var result:deleted)assertThat(result.get()).isEqualTo(200);}assertThat(count("business_audit")).isEqualTo(audits+1);
    }
    @Test @Order(12) void requestBoundsLiteralTextAndManyImagesAreRealHttpContracts()throws Exception{
        var tooLarge=call("POST","/api/v1/articles/"+articleA+"/comments","x".repeat(131073),readerToken);assertThat(tooLarge.status).isEqualTo(413);assertThat(Files.readString(root.resolve("logs/campushub-runtime.log")).lines().filter(line->line.contains(tooLarge.trace)).toList()).anySatisfy(line->assertThat(line).contains("code=REQUEST_TOO_LARGE","stage=VALIDATION"));assertThat(call("POST","/api/v1/articles/"+articleA+"/comments","x".repeat(131073),null).status).isEqualTo(401);
        assertThat(create(articleA,key(),"x".repeat(10001),List.of(),null,readerToken).status).isEqualTo(413);assertThat(create(articleA,key()," \n\t",List.of(),null,readerToken).status).isEqualTo(422);assertThat(create(articleA,key(),"x\u0000",List.of(),null,readerToken).status).isEqualTo(400);
        String literal="<script>literal-only</script>\r\n<svg onload=literal>";secrets.add(literal.replace("\r\n","\n"));var plain=create(articleA,key(),literal,List.of(),null,readerToken);assertThat(item(roots(articleA,readerToken),plain.data.path("commentId").asLong()).path("body").asString()).isEqualTo(literal.replace("\r\n","\n"));
        for(String suffix:List.of("?size=101","?page=1&page=2","?sort=latest"))assertThat(get("/api/v1/articles/"+articleA+"/comments"+suffix,readerToken).status).isEqualTo(400);
        assertThat(upload(articleA,key(),"fake.jpg","image/jpeg",png,readerToken).status).isEqualTo(400);assertThat(upload(articleA,key(),"../../file.png","image/png",png,readerToken).status).isEqualTo(400);assertThat(upload(articleA,key(),"svg.svg","image/svg+xml","<svg/>".getBytes(),readerToken).status).isEqualTo(400);assertThat(upload(articleA,key(),"bad.png","image/png",Arrays.copyOf(png,png.length-10),readerToken).status).isEqualTo(400);
        assertThat(upload(articleA,key(),"large.png","image/png",new byte[10*1024*1024+1],readerToken).status).isEqualTo(413);assertThat(upload(articleA,"x".repeat(12*1024*1024+1),"fixture.png","image/png",png,readerToken).status).isEqualTo(413);
        var images=new ArrayList<String>();for(int i=0;i<105;i++)images.add(upload(articleA,readerToken));var many=create(articleA,key(),"",images,null,readerToken);assertThat(many.status).isEqualTo(201);assertThat(item(roots(articleA,thirdToken),many.data.path("commentId").asLong()).path("images").size()).isEqualTo(105);
    }
    @Test @Order(13) void defaultOffDiagnosticsCanLinkRealHttpFailureToSafeProjectLocation()throws Exception{
        Path log=root.resolve("logs/campushub-runtime.log"),diagnostic=root.resolve("logs/campushub-diagnostic.log");assertThat(Files.exists(diagnostic)).isFalse();String original=Files.readString(log);assertThat(original).contains("diagnostics=false","event=STARTUP");
        context.close();diagnostics=true;boot();String request=key(),body=text();final Reply[] failed={null};
        auditFailure(()->{failed[0]=create(articleA,request,body,List.of(),null,readerToken);assertThat(failed[0].status).isEqualTo(503);});String trace=failed[0].trace;String runtime=Files.readString(log),details=Files.readString(diagnostic);
        assertThat(runtime.lines().filter(s->s.contains(trace)).toList()).anySatisfy(line->assertThat(line).contains("action=COMMENT_CREATE","code=SERVICE_UNAVAILABLE","stage=AUDIT","module=COMMENT","commentId="));assertThat(details).contains(trace,"BusinessAudit.java:","code=SERVICE_UNAVAILABLE","stage=AUDIT","state:45000/vendor:1644");
        var logs=context.getBean(SafeFileLogs.class);Path backup=root.resolve("logs/own-diagnostic-backup");Files.move(diagnostic,backup);Files.createDirectory(diagnostic);try{auditFailure(()->assertThat(create(articleA,key(),text(),List.of(),null,readerToken).status).isEqualTo(503));assertThat(logs.healthy()).isFalse();assertThat(logs.failedWrites()).isGreaterThan(0);}finally{Files.delete(diagnostic);Files.move(backup,diagnostic);}auditFailure(()->assertThat(create(articleA,key(),text(),List.of(),null,readerToken).status).isEqualTo(503));assertThat(logs.healthy()).isTrue();
        context.close();diagnostics=false;boot();String before=Files.readString(diagnostic);auditFailure(()->assertThat(create(articleA,key(),text(),List.of(),null,readerToken).status).isEqualTo(503));assertThat(Files.readString(diagnostic)).isEqualTo(before);
        runtime=Files.readString(log);for(String action:List.of("COMMENT_LIST","COMMENT_CREATE","COMMENT_DELETE","COMMENT_REPLIES","COMMENT_IMAGE_UPLOAD","COMMENT_IMAGE_READ"))assertThat(runtime).contains("action="+action);for(String secret:secrets){assertThat(runtime.contains(secret)).isFalse();assertThat(before.contains(secret)).isFalse();}
    }
    @Test @Order(14) void restartRetainsRepliesImagesTerminalsAuditAndNoSensitiveExports()throws Exception{
        var rows=db.queryForList("SELECT id,state,revision,body,root_comment_id,reply_to_comment_id FROM discussion_comment WHERE user_id IN (?,?,?) ORDER BY id",owner,reader,third);long audits=count("business_audit");String runtimeBefore=Files.readString(root.resolve("logs/campushub-runtime.log"));context.close();boot();assertThat(db.queryForList("SELECT id,state,revision,body,root_comment_id,reply_to_comment_id FROM discussion_comment WHERE user_id IN (?,?,?) ORDER BY id",owner,reader,third)).isEqualTo(rows);assertThat(count("business_audit")).isEqualTo(audits);assertThat(item(roots(articleA,thirdToken),lastingComment).path("available").asBoolean()).isTrue();assertThat(image(lastingImage,thirdToken).body()).isEqualTo(png);assertThat(Files.readString(root.resolve("logs/campushub-runtime.log"))).startsWith(runtimeBefore);
        CLOCK.advance(Duration.ofDays(7));assertThat(roots(articleA,readerToken).status).isEqualTo(401);assertThat(image(lastingImage,readerToken).statusCode()).isEqualTo(401);assertThat(get("/api/v1/articles/1001",null).status).isEqualTo(200);assertThat(db.queryForList("SELECT version FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank",String.class)).containsExactly("1","2","3","4","5","6");
        String runtime=Files.readString(root.resolve("logs/campushub-runtime.log")),diagnostic=Files.readString(root.resolve("logs/campushub-diagnostic.log"));String audit=JSON.writeValueAsString(db.queryForList("SELECT * FROM business_audit WHERE actor_id IN (?,?,?) ORDER BY occurred_at,event_id",owner,reader,third));for(String secret:secrets){assertThat(runtime.contains(secret)).isFalse();assertThat(diagnostic.contains(secret)).isFalse();assertThat(audit.contains(secret)).isFalse();}Files.writeString(Path.of("target","batch5-runtime-sample.txt"),runtime);Files.writeString(Path.of("target","batch5-diagnostic-sample.txt"),diagnostic);Files.writeString(Path.of("target","batch5-audit-sample.json"),audit+"\n");
    }
}
