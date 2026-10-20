package cn.edu.hzcu.campushub.authoring;

import cn.edu.hzcu.campushub.auth.*;
import java.io.*;
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
class AuthoringMySqlIT {
    @TempDir static Path root;
    private static final AuthTestSupport.ControlledMailer MAIL=new AuthTestSupport.ControlledMailer();
    private static final AuthTestSupport.MutableClock CLOCK=new AuthTestSupport.MutableClock();
    private static final HttpClient HTTP=HttpClient.newHttpClient();
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static ConfigurableApplicationContext context;
    private static JdbcTemplate db;
    private static String base,one,two,emailOne,emailTwo;
    private static long userOne,userTwo;
    private static List<Map<String,Object>> baseline;
    private static long edited;
    private static String publicImage;
    private static byte[] png;
    @BeforeAll static void start()throws Exception{
        boot();db=context.getBean(JdbcTemplate.class);assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("campushub_test");
        baseline=db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes FROM article ORDER BY id");
        emailOne="writing"+UUID.randomUUID().toString().replace("-","")+"@stu.hzcu.edu.cn";
        emailTwo="writing"+UUID.randomUUID().toString().replace("-","")+"@stu.hzcu.edu.cn";
        var auth=context.getBean(AuthService.class);String password=UUID.randomUUID().toString();
        var c=auth.requestCode(emailOne,AuthModels.Purpose.REGISTER);userOne=auth.register(emailOne,password,c.challengeId(),MAIL.code(emailOne,"REGISTER")).userId();one=auth.login(emailOne,password).accessToken();
        c=auth.requestCode(emailTwo,AuthModels.Purpose.REGISTER);userTwo=auth.register(emailTwo,password,c.challengeId(),MAIL.code(emailTwo,"REGISTER")).userId();two=auth.login(emailTwo,password).accessToken();
        png=MediaStoreTest.png(16,16);
    }
    static void boot(){var app=AuthTestSupport.application(MAIL,CLOCK);app.addInitializers(ctx->ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("writing-test-storage",Map.of("campushub.media.root",root.toString()))));context=app.run("--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=demo");base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");}
    @AfterAll static void stop(){
        if(context==null)return;
        try{
            var jdbc=context.getBean(NamedParameterJdbcTemplate.class);var p=new MapSqlParameterSource("users",List.of(userOne,userTwo)).addValue("emails",List.of(emailOne,emailTwo));
            List<Long> ids=db.queryForList("SELECT id FROM article WHERE owner_user_id IN (?,?)",Long.class,userOne,userTwo);
            if(!ids.isEmpty()){
                p.addValue("articles",ids);
                for(String table:List.of("article_publish_request","article_version_image","article_draft_image","article_draft_category","article_draft_source_link","author_create_request","media_image","article_draft"))jdbc.update("DELETE FROM "+table+" WHERE article_id IN (:articles)",p);
                jdbc.update("UPDATE article SET state='DELETED',public_version_id=NULL WHERE id IN (:articles)",p);
                jdbc.update("DELETE FROM article_version_category WHERE version_id IN(SELECT id FROM article_version WHERE article_id IN (:articles))",p);
                jdbc.update("DELETE FROM article_source_link WHERE version_id IN(SELECT id FROM article_version WHERE article_id IN (:articles))",p);
                jdbc.update("DELETE FROM article_version WHERE article_id IN (:articles)",p);
                jdbc.update("DELETE FROM article WHERE id IN (:articles)",p);
            }
            jdbc.update("DELETE FROM business_audit WHERE actor_id IN (:users)",p);
            jdbc.update("DELETE FROM author_profile WHERE user_id IN (:users)",p);
            jdbc.update("DELETE FROM email_challenge WHERE email IN (:emails)",p);jdbc.update("DELETE FROM auth_email_gate WHERE email IN (:emails)",p);
            jdbc.update("DELETE FROM auth_session WHERE user_id IN (:users)",p);jdbc.update("DELETE FROM campus_certification WHERE user_id IN (:users)",p);jdbc.update("DELETE FROM platform_user WHERE id IN (:users)",p);
            assertThat(db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes FROM article ORDER BY id")).isEqualTo(baseline);
        }finally{context.close();}
    }
    record Reply(int status,JsonNode data,String code,String raw){}
    static Reply response(HttpResponse<byte[]> r){
        JsonNode json=JSON.readTree(r.body());assertThat(json.path("traceId").asString().matches("[0-9a-f-]{36}")).isTrue();
        assertThat(r.headers().firstValue("X-Trace-Id").orElse("").equals(json.path("traceId").asString())).isTrue();
        return new Reply(r.statusCode(),json.path("data"),json.path("code").asString(),new String(r.body(),StandardCharsets.UTF_8));
    }
    static Reply call(String method,String path,Object body,String token)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30));if(token!=null)b.header("Authorization","Bearer "+token);
        if(body!=null)b.header("Content-Type","application/json");
        return response(HTTP.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body instanceof String s?s:JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofByteArray()));
    }
    static String key(){return UUID.randomUUID().toString();}
    static long create(String token)throws Exception{var r=call("POST","/api/v1/me/articles",Map.of("requestId",key()),token);assertThat(r.status).isEqualTo(201);return r.data.path("articleId").asLong();}
    static Map<String,Object> draft(long rev,String title,String body){var d=new LinkedHashMap<String,Object>();d.put("expectedRevision",rev);d.put("title",title);d.put("summary","");d.put("body",body);d.put("jobIds",List.of(1));d.put("majorIds",List.of(11));d.put("companyIds",List.of());d.put("topicIds",List.of(31));d.put("experienceStage","演示实习准备");d.put("sourceType","DEMO");d.put("sourceNote","G04后端接口验证示例，不是真实经历");d.put("sourceLinks",List.of(Map.of("label","Java资料","url","https://dev.java/learn/")));return d;}
    static Reply save(long id,long rev,String title,String body,String token)throws Exception{return call("PUT","/api/v1/me/articles/"+id+"/draft",draft(rev,title,body),token);}
    static Reply publish(long id,long rev,String request,String token)throws Exception{return call("POST","/api/v1/me/articles/"+id+"/publish",Map.of("expectedRevision",rev,"requestId",request),token);}
    static Reply upload(long article,String request,String filename,String mime,byte[] bytes,String token)throws Exception{
        String boundary="test-"+key();var out=new ByteArrayOutputStream();out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n"+request+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var b=HttpRequest.newBuilder(URI.create(base+"/api/v1/me/articles/"+article+"/images")).timeout(Duration.ofSeconds(30)).expectContinue(true).header("Content-Type","multipart/form-data; boundary="+boundary);if(token!=null)b.header("Authorization","Bearer "+token);
        return response(HTTP.send(b.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofByteArray()));
    }
    static HttpResponse<byte[]> image(String id,String token)throws Exception{var b=HttpRequest.newBuilder(URI.create(base+"/api/v1/media/images/"+id)).timeout(Duration.ofSeconds(10));if(token!=null)b.header("Authorization","Bearer "+token);var response=HTTP.send(b.GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");return response;}
    static long rev(long id,String token)throws Exception{return call("GET","/api/v1/me/articles/"+id+"/draft",null,token).data.path("revision").asLong();}

    @Test @Order(1) void profilesArePrivateAndVersionedWithoutModifyingIdentity()throws Exception{
        assertThat(call("GET","/api/v1/me/profile",null,null).status).isEqualTo(401);
        var initial=call("GET","/api/v1/me/profile",null,one);assertThat(initial.data.path("nickname").asString()).isEqualTo("城院同学");
        var body=Map.of("expectedRevision",0,"nickname","G04测试昵称","majorId",12,"currentStage","ENROLLED","bio","测试简介");
        var updated=call("PUT","/api/v1/me/profile",body,one);assertThat(updated.status).isEqualTo(200);assertThat(updated.data.path("revision").asLong()).isEqualTo(1);
        assertThat(call("PUT","/api/v1/me/profile",body,one).status).isEqualTo(409);assertThat(call("GET","/api/v1/me/profile",null,two).data.path("nickname").asString()).isEqualTo("城院同学");
        var fake=new HashMap<String,Object>(body);fake.put("userId",userTwo);assertThat(call("PUT","/api/v1/me/profile",fake,one).status).isEqualTo(400);
        assertThat(db.queryForObject("SELECT email FROM platform_user WHERE id=?",String.class,userOne).equals(emailOne)).isTrue();
    }
    @Test @Order(2) void independentIncompleteDraftsCreationRetriesAndConcurrentSaves()throws Exception{
        String request=key();var created=call("POST","/api/v1/me/articles",Map.of("requestId",request),one);long id=created.data.path("articleId").asLong();
        assertThat(call("POST","/api/v1/me/articles",Map.of("requestId",request),one).data.path("articleId").asLong()).isEqualTo(id);
        long other=create(one);assertThat(other).isNotEqualTo(id);assertThat(publish(other,0,key(),one).status).isEqualTo(422);
        var incomplete=draft(0,"","");incomplete.put("jobIds",List.of());incomplete.put("majorIds",List.of());incomplete.put("sourceType",null);incomplete.put("experienceStage","");assertThat(call("PUT","/api/v1/me/articles/"+other+"/draft",incomplete,one).status).isEqualTo(200);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var results=pool.invokeAll(List.<Callable<Integer>>of(()->save(id,0,"并发A","正文A",one).status,()->save(id,0,"并发B","正文B",one).status));var statuses=new ArrayList<Integer>();for(var r:results)statuses.add(r.get());assertThat(statuses).containsExactlyInAnyOrder(200,409);
        }
        assertThat(rev(id,one)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM article_version WHERE article_id=?",Long.class,id)).isZero();
        assertThat(call("GET","/api/v1/me/articles?page=1&size=1",null,one).data.path("items").size()).isEqualTo(1);
        assertThat(call("GET","/api/v1/me/articles?size=101",null,one).status).isEqualTo(400);
    }
    @Test @Order(3) void privateUploadsRetryKeysAndForeignAssociations()throws Exception{
        long id=create(one);String request=key();var uploaded=upload(id,request,"fixture.png","image/png",png,one);assertThat(uploaded.status).isEqualTo(200);String image=uploaded.data.path("imageId").asString();
        assertThat(upload(id,request,"fixture.png","image/png",png,one).data.path("imageId").asString()).isEqualTo(image);
        assertThat(upload(id,request,"other.png","image/png",MediaStoreTest.png(8,8),one).status).isEqualTo(409);
        assertThat(image(image,null).statusCode()).isEqualTo(404);assertThat(image(image,two).statusCode()).isEqualTo(404);assertThat(image(image,one).body()).isEqualTo(png);
        assertThat(save(create(two),0,"外来图","![图](/api/v1/media/images/"+image+")",two).status).isEqualTo(404);
        assertThat(save(create(one),0,"其他文章图","![图](/api/v1/media/images/"+image+")",one).status).isEqualTo(404);
        for(String action:List.of("draft","publish","unpublish","restore")){
            var r=action.equals("draft")?call("GET","/api/v1/me/articles/"+id+"/draft",null,two):call("POST","/api/v1/me/articles/"+id+"/"+action,action.equals("publish")?Map.of("expectedRevision",0,"requestId",key()):Map.of("expectedRevision",0),two);assertThat(r.status).isEqualTo(404);
        }
        assertThat(upload(id,key(),"fixture.png","image/png",png,two).status).isEqualTo(404);assertThat(call("DELETE","/api/v1/me/articles/"+id+"?expectedRevision=0",null,two).status).isEqualTo(404);
    }
    @Test @Order(4) void publicationIsImmutableAtomicAndConcurrentRetriesOnlyHaveOneEffect()throws Exception{
        edited=create(one);publicImage=upload(edited,key(),"fixture.png","image/png",png,one).data.path("imageId").asString();String body="# DEMO\n\n![图][p]\n\n[p]: /api/v1/media/images/"+publicImage+"\n\n原始Markdown **保存**。";
        assertThat(save(edited,0,"【DEMO】初版",body,one).status).isEqualTo(200);String request=key();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var results=pool.invokeAll(List.<Callable<Integer>>of(()->publish(edited,1,request,one).status,()->publish(edited,1,request,one).status));for(var r:results)assertThat(r.get()).isEqualTo(200);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM article_version WHERE article_id=?",Long.class,edited)).isEqualTo(1);
        var detail=call("GET","/api/v1/articles/"+edited,null,null);assertThat(detail.data.path("body").asString()).isEqualTo(body);assertThat(detail.data.path("counts").path("origin").asString()).isEqualTo("NONE");assertThat(detail.data.path("counts").path("effectiveCoins").asLong()).isZero();assertThat(detail.raw).doesNotContain(emailOne,"owner_user_id","nickname");
        var binary=image(publicImage,null);assertThat(binary.statusCode()).isEqualTo(200);assertThat(binary.body()).isEqualTo(png);assertThat(binary.headers().firstValue("Cache-Control").orElse("")).contains("no-store");assertThat(binary.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff");
        assertThat(publish(edited,1,key(),one).status).isEqualTo(409);assertThat(publish(edited,2,request,one).status).isEqualTo(409);
        assertThat(call("GET","/api/v1/articles?jobId=1&majorId=11&sort=latest",null,null).raw).contains("【DEMO】初版");
        assertThat(save(edited,2,"PRIVATE_DRAFT","新正文，没有旧图",one).status).isEqualTo(200);
        assertThat(call("GET","/api/v1/articles/"+edited,null,null).data.path("body").asString()).isEqualTo(body);assertThat(image(publicImage,null).statusCode()).isEqualTo(200);
        CLOCK.advance(Duration.ofMinutes(1));var published=publish(edited,3,key(),one);assertThat(published.status).isEqualTo(200);
        var changed=call("GET","/api/v1/articles/"+edited,null,null);assertThat(changed.data.path("title").asString()).isEqualTo("PRIVATE_DRAFT");assertThat(changed.data.path("publishedAt").asString()).isEqualTo(detail.data.path("publishedAt").asString());assertThat(changed.data.path("updatedAt").asString()).isNotEqualTo(detail.data.path("updatedAt").asString());
        assertThat(image(publicImage,null).statusCode()).isEqualTo(404);assertThat(image(publicImage,one).statusCode()).isEqualTo(200);
    }
    @Test @Order(5) void offlineHiddenDeletedOldLinksAndAuthorStateBoundaries()throws Exception{
        long id=create(one);String img=upload(id,key(),"fixture.png","image/png",png,one).data.path("imageId").asString();save(id,0,"图文状态","![图](/api/v1/media/images/"+img+")",one);publish(id,1,key(),one);
        var original=call("GET","/api/v1/articles/"+id,null,null).data.path("publishedAt").asString();
        assertThat(call("POST","/api/v1/me/articles/"+id+"/unpublish",Map.of("expectedRevision",2),one).status).isEqualTo(200);assertThat(image(img,null).statusCode()).isEqualTo(404);assertThat(call("GET","/api/v1/articles/"+id,null,null).status).isEqualTo(404);
        assertThat(call("POST","/api/v1/me/articles/"+id+"/unpublish",Map.of("expectedRevision",2),one).data.path("revision").asLong()).isEqualTo(3);
        CLOCK.advance(Duration.ofHours(1));assertThat(call("POST","/api/v1/me/articles/"+id+"/restore",Map.of("expectedRevision",3),one).status).isEqualTo(200);assertThat(call("GET","/api/v1/articles/"+id,null,null).data.path("publishedAt").asString()).isEqualTo(original);
        db.update("UPDATE article SET state='ADMIN_HIDDEN' WHERE id=?",id);assertThat(image(img,null).statusCode()).isEqualTo(404);assertThat(call("GET","/api/v1/articles/"+id,null,null).status).isEqualTo(404);
        assertThat(publish(id,4,key(),one).status).isEqualTo(409);assertThat(call("POST","/api/v1/me/articles/"+id+"/restore",Map.of("expectedRevision",4),one).status).isEqualTo(409);assertThat(save(id,4,"偷恢复","正文",one).status).isEqualTo(409);
        assertThat(call("DELETE","/api/v1/me/articles/"+id+"?expectedRevision=4",null,one).status).isEqualTo(200);assertThat(call("DELETE","/api/v1/me/articles/"+id+"?expectedRevision=4",null,one).status).isEqualTo(200);assertThat(image(img,one).statusCode()).isEqualTo(404);assertThat(call("GET","/api/v1/me/articles/"+id+"/draft",null,one).status).isEqualTo(404);
    }
    @Test @Order(6) void invalidImagesInputBoundsAndNoPhantomReferences()throws Exception{
        long id=create(one);assertThat(upload(id,key(),"../../fixture.png","image/png",png,one).status).isEqualTo(400);assertThat(upload(id,key(),"fake.jpg","image/jpeg",png,one).status).isEqualTo(400);assertThat(upload(id,key(),"script.svg","image/svg+xml","<svg/>".getBytes(),one).status).isEqualTo(400);assertThat(upload(id,key(),"bad.png","image/png",Arrays.copyOf(png,png.length-10),one).status).isEqualTo(400);
        assertThat(upload(id,key(),"large.png","image/png",new byte[10*1024*1024+1],one).status).isEqualTo(413);
        assertThat(upload(id,"x".repeat(12*1024*1024+1),"fixture.png","image/png",png,one).status).isEqualTo(413);
        assertThat(save(id,0,"大正文","x".repeat(1024*1024+1),one).status).isEqualTo(413);
        assertThat(call("PUT","/api/v1/me/articles/"+id+"/draft","x".repeat(2*1024*1024+1),one).status).isEqualTo(413);
        var bad=draft(0,"分类","正文");bad.put("majorIds",List.of(1));assertThat(call("PUT","/api/v1/me/articles/"+id+"/draft",bad,one).status).isEqualTo(400);
        var forged=draft(0,"伪造作者","正文");forged.put("ownerUserId",userTwo);assertThat(call("PUT","/api/v1/me/articles/"+id+"/draft",forged,one).status).isEqualTo(400);
        String code="```md\n![仅代码](/api/v1/media/images/"+key()+")\n```";assertThat(save(id,0,"代码示例",code,one).status).isEqualTo(200);assertThat(publish(id,1,key(),one).status).isEqualTo(200);assertThat(call("GET","/api/v1/articles/"+id,null,null).data.path("body").asString()).isEqualTo(code);
    }
    @Test @Order(7) void realStorageFailureAndDatabaseRollbackPreserveExistingFiles()throws Exception{
        long id=create(one);Path sentinel=root.resolve("objects/sentinel-existing");Files.writeString(sentinel,"preserve");Path temporary=root.resolve("temporary");Files.delete(temporary);Files.writeString(temporary,"blocking-test-file");
        try{assertThat(upload(id,key(),"fixture.png","image/png",png,one).status).isEqualTo(503);}finally{Files.delete(temporary);Files.createDirectory(temporary);}
        assertThat(Files.readString(sentinel)).isEqualTo("preserve");assertThat(db.queryForObject("SELECT COUNT(*) FROM media_image WHERE article_id=?",Long.class,id)).isZero();
        long filesBefore;try(var stream=Files.list(root.resolve("objects"))){filesBefore=stream.count();}
        db.execute("CREATE TRIGGER g04_writing_media_failure BEFORE INSERT ON media_image FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled test rollback'");
        try{assertThat(upload(id,key(),"fixture.png","image/png",png,one).status).isEqualTo(503);}finally{db.execute("DROP TRIGGER g04_writing_media_failure");}
        try(var stream=Files.list(root.resolve("objects"))){assertThat(stream.count()).isEqualTo(filesBefore);}
        assertThat(Files.readString(sentinel)).isEqualTo("preserve");assertThat(rev(id,one)).isZero();
        assertThat(upload(id,key(),"fixture.png","image/png",png,one).status).isEqualTo(200);
    }
    @Test @Order(8) void publishDatabaseFailureRollsBackVersionPointerAndRetryReceipt()throws Exception{
        long id=create(one);save(id,0,"待发布","正文",one);String request=key();long before=db.queryForObject("SELECT COUNT(*) FROM article_version",Long.class);
        db.execute("CREATE TRIGGER g04_writing_publish_failure BEFORE INSERT ON article_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled publication rollback'");
        try{assertThat(publish(id,1,request,one).status).isEqualTo(503);}finally{db.execute("DROP TRIGGER g04_writing_publish_failure");}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM article_version",Long.class)).isEqualTo(before);assertThat(db.queryForObject("SELECT COUNT(*) FROM article_publish_request WHERE article_id=?",Long.class,id)).isZero();assertThat(rev(id,one)).isEqualTo(1);
        assertThat(call("GET","/api/v1/articles/"+id,null,null).status).isEqualTo(404);assertThat(publish(id,1,request,one).status).isEqualTo(200);
    }
    @Test @Order(9) void concurrentCreateAndUploadShareOneStableReceiptAndFile()throws Exception{
        String creation=key();long id;
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var results=pool.invokeAll(List.<Callable<Reply>>of(()->call("POST","/api/v1/me/articles",Map.of("requestId",creation),one),()->call("POST","/api/v1/me/articles",Map.of("requestId",creation),one)));
            var first=results.getFirst().get();var second=results.getLast().get();assertThat(first.status).isEqualTo(201);assertThat(second.status).isEqualTo(201);id=first.data.path("articleId").asLong();assertThat(second.data.path("articleId").asLong()).isEqualTo(id);
            String request=key();long before;try(var files=Files.list(root.resolve("objects"))){before=files.count();}
            var images=pool.invokeAll(List.<Callable<Reply>>of(()->upload(id,request,"fixture.png","image/png",png,one),()->upload(id,request,"fixture.png","image/png",png,one)));
            var a=images.getFirst().get();var b=images.getLast().get();assertThat(a.status).isEqualTo(200);assertThat(b.status).isEqualTo(200);assertThat(a.data.path("imageId").asString()).isEqualTo(b.data.path("imageId").asString());
            try(var files=Files.list(root.resolve("objects"))){assertThat(files.count()).isEqualTo(before+1);}
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM media_image WHERE article_id=?",Long.class,id)).isEqualTo(1);
    }
    @Test @Order(10) void partialUploadAndBrokenNewImageCannotReplaceReadablePublicVersion()throws Exception{
        long id=create(one);String first=upload(id,key(),"first.png","image/png",png,one).data.path("imageId").asString();String initial="![原图](/api/v1/media/images/"+first+")";save(id,0,"图文存储故障",initial,one);publish(id,1,key(),one);
        byte[] another=MediaStoreTest.png(8,8);String second=upload(id,key(),"second.png","image/png",another,one).data.path("imageId").asString();save(id,2,"新草稿","![新图](/api/v1/media/images/"+second+")",one);
        assertThat(upload(id,key(),"damaged.png","image/png",new byte[]{1,2,3},one).status).isEqualTo(400);assertThat(db.queryForObject("SELECT COUNT(*) FROM media_image WHERE article_id=?",Long.class,id)).isEqualTo(2);
        long pointer=db.queryForObject("SELECT public_version_id FROM article WHERE id=?",Long.class,id);Path stored=root.resolve("objects").resolve(second);byte[] backup=Files.readAllBytes(stored);String request=key();
        Files.write(stored,new byte[]{1});
        try{
            assertThat(publish(id,3,request,one).status).isEqualTo(503);assertThat(image(second,one).statusCode()).isEqualTo(503);
            assertThat(db.queryForObject("SELECT public_version_id FROM article WHERE id=?",Long.class,id)).isEqualTo(pointer);
            assertThat(call("GET","/api/v1/articles/"+id,null,null).data.path("body").asString()).isEqualTo(initial);assertThat(image(first,null).statusCode()).isEqualTo(200);assertThat(rev(id,one)).isEqualTo(3);
        }finally{Files.write(stored,backup);}
        assertThat(publish(id,3,request,one).status).isEqualTo(200);assertThat(image(first,null).statusCode()).isEqualTo(404);assertThat(image(second,null).body()).isEqualTo(another);
    }
    @Test @Order(11) void authenticationChangesApplyToNewWritingRequests()throws Exception{
        long id=create(one);db.update("UPDATE campus_certification SET state='REVOKED' WHERE user_id=?",userOne);
        assertThat(save(id,0,"失效","正文",one).status).isEqualTo(403);assertThat(call("POST","/api/v1/me/articles",Map.of("requestId",key()),one).status).isEqualTo(403);
        db.update("UPDATE campus_certification SET state='VALID' WHERE user_id=?",userOne);db.update("UPDATE platform_user SET account_state='RESTRICTED' WHERE id=?",userOne);assertThat(upload(id,key(),"fixture.png","image/png",png,one).status).isEqualTo(403);db.update("UPDATE platform_user SET account_state='ACTIVE' WHERE id=?",userOne);
        assertThat(call("POST","/api/v1/auth/logout",null,two).status).isEqualTo(200);assertThat(call("GET","/api/v1/me/articles",null,two).status).isEqualTo(401);
        assertThat(call("GET","/api/v1/articles",null,null).status).isEqualTo(200);
    }
    @Test @Order(12) void rebootKeepsIndependentDraftsProfilesAndPublicVersions()throws Exception{
        long id=create(one);String body="# 重启保留\n\n复杂Markdown `a < b` 与中文。";save(id,0,"恢复草稿",body,one);var before=call("GET","/api/v1/me/articles/"+id+"/draft",null,one).data;
        context.close();boot();db=context.getBean(JdbcTemplate.class);
        assertThat(call("GET","/api/v1/me/articles/"+id+"/draft",null,one).data).isEqualTo(before);assertThat(call("GET","/api/v1/me/profile",null,one).data.path("nickname").asString()).isEqualTo("G04测试昵称");assertThat(call("GET","/api/v1/articles/"+edited,null,null).status).isEqualTo(200);
        CLOCK.advance(Duration.ofDays(7));assertThat(call("PUT","/api/v1/me/articles/"+id+"/draft",draft(1,"过期","正文"),one).status).isEqualTo(401);
        assertThat(db.queryForList("SELECT version FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank",String.class)).containsExactly("1","2","3","4","5","6");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM article WHERE owner_user_id IS NULL",Long.class)).isEqualTo(10);
    }
}
