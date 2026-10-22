package cn.edu.hzcu.campushub.coin;

import cn.edu.hzcu.campushub.auth.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
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

/** Real HTTP/MySQL; controlled mail, clock and owned random identities only. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CoinsMySqlIT {
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
    static String base,mode,password;
    static long a,b,draft;
    static List<Map<String,Object>> originalArticles,originalVersions,originalCoins;
    static Map<String,Long> baseline;
    record Reply(int status,JsonNode data,String code,String trace,String raw){}
    static String key(){return UUID.randomUUID().toString();}
    static String secret(){String s="b8-private-"+key();secrets.add(s);return s;}
    static Path runtime(){return root.resolve("logs/campushub-runtime.log");}
    static Path diagnostic(){return root.resolve("logs/campushub-diagnostic.log");}
    static void boot(String selected){
        mode=selected;var app=AuthTestSupport.application(MAIL,CLOCK);
        app.addInitializers(ctx->ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("coin-test",Map.of("campushub.run-mode",selected,"campushub.logs.enabled",true,"campushub.logs.root",root.resolve("logs").toString(),"campushub.logs.max-bytes",10*1024*1024,"campushub.media.root",root.resolve("media").toString(),"server.tomcat.threads.max",8,"server.tomcat.threads.min-spare",1))));
        context=app.run("--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=demo");base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");db=context.getBean(JdbcTemplate.class);
    }
    @BeforeAll static void start()throws Exception{
        boot("debug");assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("campushub_test");
        originalArticles=db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes,count_origin FROM article ORDER BY id");originalVersions=db.queryForList("SELECT id,title,summary,body,source_type,source_note FROM article_version ORDER BY id");originalCoins=db.queryForList("SELECT id,effective_coins,effective_likes,count_origin FROM article ORDER BY id");baseline=new LinkedHashMap<>();
        for(String table:List.of("coin_wallet","coin_event","coin_reading_session","business_audit","article_like","article_favorite","discussion_comment","media_image"))baseline.put(table,count(table));
        password=secret();for(int i=0;i<5;i++){
            String email="b8"+key().replace("-","")+"@stu.hzcu.edu.cn";emails.add(email);secrets.add(email);var auth=context.getBean(AuthService.class);var challenge=auth.requestCode(email,AuthModels.Purpose.REGISTER);String code=MAIL.code(email,"REGISTER");secrets.add(code);users.add(auth.register(email,password,challenge.challengeId(),code).userId());tokens.add(login(i));
        }
        a=article(0);b=article(0);draft=call("POST","/api/v1/me/articles",Map.of("requestId",key()),0).data.path("articleId").asLong();
    }
    static String login(int who){String token=context.getBean(AuthService.class).login(emails.get(who),password).accessToken();secrets.add(token);return token;}
    static void nextDay(int... who){CLOCK.advance(Duration.ofDays(1));for(int i:who)tokens.set(i,login(i));}
    static long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    static long balance(int who)throws Exception{return get("/me/coins",who).data.path("balance").asLong();}
    static String requestLine(String trace)throws Exception{return Files.readString(runtime()).lines().filter(s->s.contains("traceId="+trace+" event=REQUEST")).findFirst().orElseThrow();}
    static Reply call(String method,String path,Object body,int who)throws Exception{return raw(method,path,body,who<0?null:tokens.get(who),"application/json");}
    static Reply raw(String method,String path,Object body,String token,String type)throws Exception{
        if(!path.startsWith("/api/"))path="/api/v1"+path;
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(25)).header("X-Trace-Id","untrusted-private-input");if(token!=null)request.header("Authorization","Bearer "+token);if(body!=null)request.header("Content-Type",type);
        var response=HTTP.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body instanceof String s?s:JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        var json=JSON.readTree(response.body());String trace=json.path("traceId").asString();assertThat(response.headers().firstValue("X-Trace-Id")).contains(trace);assertThat(trace).matches("[0-9a-f-]{36}");
        String route=path.split("\\?")[0];String action=cn.edu.hzcu.campushub.common.RequestTrace.action(route,method);
        if(cn.edu.hzcu.campushub.common.RequestTrace.module(action).equals("COIN")){
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            for(int retry=0;retry<100&&!Files.readString(runtime()).contains("traceId="+trace+" event=REQUEST");retry++)Thread.sleep(10);
            assertThat(requestLine(trace)).contains("action="+action,"module=COIN");coverage.add(Map.of("mode",mode,"method",method,"route",route.replaceAll("/[0-9]+(?=/|$)","/{id}"),"action",action,"status",response.statusCode(),"code",json.path("code").asString(),"traceId",trace));
        }
        if(response.statusCode()>=400){assertThat(json.has("data")).isFalse();for(String secret:secrets)assertThat(response.body()).doesNotContain(secret);}
        return new Reply(response.statusCode(),json.path("data"),json.path("code").asString(),trace,response.body());
    }
    static Reply get(String path,int who)throws Exception{return call("GET",path,null,who);}
    static Reply reward(int who,String request)throws Exception{return call("POST","/me/rewards/login",Map.of("requestId",request),who);}
    static Reply read(long article,int who)throws Exception{return call("POST","/articles/"+article+"/reading-session",null,who);}
    static Reply claim(String session,int who,String request)throws Exception{return call("POST","/me/rewards/reading",Map.of("sessionId",session,"requestId",request),who);}
    static Reply spend(long article,int amount,int who,String request)throws Exception{return call("POST","/articles/"+article+"/coins",Map.of("amount",amount,"requestId",request),who);}
    static Map<String,Object> content(long revision){return Map.ofEntries(Map.entry("expectedRevision",revision),Map.entry("title",secret()),Map.entry("summary",secret()),Map.entry("body",secret()),Map.entry("jobIds",List.of(1)),Map.entry("majorIds",List.of(11)),Map.entry("companyIds",List.of(21)),Map.entry("topicIds",List.of(31)),Map.entry("experienceStage","DEMO"),Map.entry("sourceType","DEMO"),Map.entry("sourceNote",secret()),Map.entry("sourceLinks",List.of()));}
    static long article(int who)throws Exception{
        long id=call("POST","/me/articles",Map.of("requestId",key()),who).data.path("articleId").asLong();assertThat(call("PUT","/me/articles/"+id+"/draft",content(0),who).status).isEqualTo(200);assertThat(call("POST","/me/articles/"+id+"/publish",Map.of("expectedRevision",1,"requestId",key()),who).status).isEqualTo(200);return id;
    }
    static long fact(long article){return db.queryForObject("SELECT COALESCE(SUM(-delta),0) FROM coin_event WHERE article_id=? AND action='ARTICLE_COIN' AND effective=TRUE",Long.class,article);}
    interface Checked {Reply run()throws Exception;}
    static List<Reply> race(int count,Checked task)throws Exception{try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var gate=new CountDownLatch(1);var futures=new ArrayList<Future<Reply>>();for(int i=0;i<count;i++)futures.add(pool.submit(()->{gate.await();return task.run();}));gate.countDown();var replies=new ArrayList<Reply>();for(var f:futures)replies.add(f.get(30,TimeUnit.SECONDS));return replies;}}
    static Reply fault(String table,String operation,String condition,Checked request)throws Exception{
        String marker=secret();db.execute("CREATE TRIGGER b08_coin_failure BEFORE "+operation+" ON "+table+" FOR EACH ROW BEGIN IF "+condition+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='"+marker+"'; END IF; END");try{return request.run();}finally{db.execute("DROP TRIGGER b08_coin_failure");}
    }
    static void reconcile(int who)throws Exception{
        long user=users.get(who);long sum=db.queryForObject("SELECT COALESCE(SUM(delta),0) FROM coin_event WHERE user_id=?",Long.class,user);assertThat(balance(who)).isEqualTo(sum);
        long events=db.queryForObject("SELECT COUNT(*) FROM coin_event WHERE user_id=?",Long.class,user);assertThat(db.queryForObject("SELECT COALESCE(MAX(wallet_revision),0) FROM coin_event WHERE user_id=?",Long.class,user)).isEqualTo(events);
    }
    @Test @Order(1) void rulesAndReadOnlyQueriesDoNotCreateWalletOrMintCoins()throws Exception{
        long events=count("coin_event"),wallets=count("coin_wallet");var rewards=get("/me/rewards",1).data;var rules=rewards.path("rules");assertThat(rules.path("loginReward").asInt()).isEqualTo(2);assertThat(rules.path("readingReward").asInt()).isEqualTo(1);assertThat(rules.path("readingSeconds").asInt()).isEqualTo(180);assertThat(rules.path("dailyMaximum").asInt()).isEqualTo(3);assertThat(rules.path("articleLimit").asInt()).isEqualTo(2);assertThat(rules.path("timezone").asString()).isEqualTo("Asia/Shanghai");assertThat(rules.path("readerCanWithdraw").asBoolean()).isFalse();
        assertThat(balance(1)).isZero();assertThat(get("/me/coin-ledger",1).data.path("items").size()).isZero();assertThat(count("coin_event")).isEqualTo(events);assertThat(count("coin_wallet")).isEqualTo(wallets);
    }
    @Test @Order(2) void authenticationPrecedesBodyTargetAndAllInputsAreStrict()throws Exception{
        for(String path:List.of("/me/coins","/me/coin-ledger","/me/rewards","/me/rewards/login","/me/rewards/reading","/articles/not-id/coins","/articles/not-id/coin-state","/articles/not-id/reading-session"))assertThat(call(path.endsWith("coins")&&!path.equals("/me/coins")||path.contains("/rewards/")||path.endsWith("reading-session")?"POST":"GET",path,"malformed",-1).status).isEqualTo(401);
        for(String suffix:List.of("userId=1","date=2030-01-01","page=0","size=101","page=1&page=2","snapshot=-1","sort=latest"))assertThat(get("/me/coin-ledger?"+suffix,1).status).isEqualTo(400);
        for(String body:List.of("{\"requestId\":\""+key()+"\",\"userId\":1}","{\"requestId\":\""+key()+"\",\"requestId\":\""+key()+"\"}","{}","[]"))assertThat(call("POST","/me/rewards/login",body,1).status).isEqualTo(400);
        assertThat(raw("POST","/me/rewards/login","{}",tokens.get(1),"text/plain").status).isEqualTo(415);assertThat(call("POST","/me/rewards/login","x".repeat(4097),1).status).isEqualTo(413);
        assertThat(call("GET","/me/coins","{}",1).status).isEqualTo(400);assertThat(call("POST","/articles/"+a+"/reading-session","{}",1).status).isEqualTo(400);
        for(String amount:List.of("0","-1","1.5","1.0","\"1\"","9223372036854775808"))assertThat(call("POST","/articles/"+a+"/coins","{\"requestId\":\""+key()+"\",\"amount\":"+amount+"}",1).status).isEqualTo(400);
        for(String table:List.of("platform_user","campus_certification")){
            String column=table.equals("platform_user")?"account_state":"state",id=table.equals("platform_user")?"id":"user_id",bad=table.equals("platform_user")?"RESTRICTED":"REVOKED",good=table.equals("platform_user")?"ACTIVE":"VALID";
            db.update("UPDATE "+table+" SET "+column+"=? WHERE "+id+"=?",bad,users.get(1));try{assertThat(get("/me/coins",1).status).isEqualTo(403);assertThat(spend(a,1,1,key()).status).isEqualTo(403);}finally{db.update("UPDATE "+table+" SET "+column+"=? WHERE "+id+"=?",good,users.get(1));}
        }
        String old=login(4);context.getBean(AuthService.class).logout(context.getBean(AuthService.class).authenticate(old));assertThat(raw("GET","/me/coins",null,old,"application/json").status).isEqualTo(401);
    }
    @Test @Order(3) void dailyLoginConcurrentIdempotencyMidnightAndOldReceiptReplay()throws Exception{
        String request=key();long before=balance(1);var replies=race(12,()->reward(1,request));assertThat(replies).allSatisfy(r->assertThat(r.status).isEqualTo(200));assertThat(replies.stream().map(r->r.data.path("eventId").asString()).distinct().count()).isEqualTo(1);assertThat(balance(1)).isEqualTo(before+2);assertThat(reward(1,key()).code).isEqualTo("REWARD_ALREADY_CLAIMED");
        JsonNode original=replies.getFirst().data;Instant midnight=CLOCK.instant().atZone(CoinRules.ZONE).toLocalDate().plusDays(1).atStartOfDay(CoinRules.ZONE).toInstant();CLOCK.advance(Duration.between(CLOCK.instant(),midnight.minusSeconds(1)));assertThat(reward(1,key()).code).isEqualTo("REWARD_ALREADY_CLAIMED");CLOCK.advance(Duration.ofSeconds(1));
        assertThat(reward(1,request).data).isEqualTo(original);assertThat(reward(1,key()).code).isEqualTo("LOGIN_REQUIRED_TODAY");tokens.set(1,login(1));assertThat(reward(1,key()).status).isEqualTo(200);assertThat(balance(1)).isEqualTo(before+4);reconcile(1);
    }
    @Test @Order(4) void readingUsesServer179And180SecondsAndNeverRefreshesStart()throws Exception{
        String session=read(a,1).data.path("sessionId").asString();JsonNode original=read(a,1).data;CLOCK.advance(Duration.ofSeconds(179));assertThat(read(a,1).data).isEqualTo(original);String request=key();long before=balance(1),events=count("coin_event");assertThat(claim(session,1,request).code).isEqualTo("READING_NOT_READY");assertThat(count("coin_event")).isEqualTo(events);assertThat(claim(session,2,key()).status).isEqualTo(404);
        CLOCK.advance(Duration.ofSeconds(1));assertThat(get("/me/rewards",1).data.path("reading").path("eligible").asBoolean()).isTrue();var receipt=claim(session,1,request);assertThat(receipt.status).isEqualTo(200);assertThat(claim(session,1,request).data).isEqualTo(receipt.data);assertThat(balance(1)).isEqualTo(before+1);
        String other=read(1001,1).data.path("sessionId").asString();CLOCK.advance(Duration.ofSeconds(180));assertThat(claim(other,1,key()).code).isEqualTo("REWARD_ALREADY_CLAIMED");assertThat(claim(other,1,request).code).isEqualTo("REQUEST_KEY_REUSED");assertThat(get("/me/rewards",1).data.path("remainingDailyReward").asLong()).isZero();
        nextDay(1);assertThat(claim(other,1,key()).code).isEqualTo("READING_DAY_EXPIRED");assertThat(read(1001,1).data.path("sessionId").asString()).isNotEqualTo(other);assertThat(claim(session,1,request).data).isEqualTo(receipt.data);
        assertThat(read(a,0).status).isEqualTo(200);reconcile(1);
    }
    @Test @Order(5) void differentArticlesAndDevicesShareExactlyOneReadingReward()throws Exception{
        nextDay(1);String one=read(a,1).data.path("sessionId").asString(),two=read(1001,1).data.path("sessionId").asString();CLOCK.advance(Duration.ofSeconds(180));long before=balance(1);var i=new java.util.concurrent.atomic.AtomicInteger();var replies=race(12,()->claim(i.getAndIncrement()%2==0?one:two,1,key()));assertThat(replies.stream().filter(r->r.status==200).count()).isEqualTo(1);assertThat(replies.stream().filter(r->r.status!=200)).allSatisfy(r->assertThat(r.code).isEqualTo("REWARD_ALREADY_CLAIMED"));assertThat(balance(1)).isEqualTo(before+1);reconcile(1);
    }
    @Test @Order(6) void targetPrivacySelfDemoAndUnavailableArticlesCannotReceiveCoins()throws Exception{
        tokens.set(0,login(0));assertThat(spend(a,1,0,key()).code).isEqualTo("SELF_ARTICLE");assertThat(spend(1001,1,1,key()).code).isEqualTo("NO_REAL_AUTHOR");assertThat(get("/articles/1001/coin-state",1).data.path("reason").asString()).isEqualTo("NO_REAL_AUTHOR");assertThat(spend(a,1,4,key()).code).isEqualTo("INSUFFICIENT_COINS");assertThat(spend(draft,1,1,key()).status).isEqualTo(404);
        long target=article(0);for(String state:List.of("AUTHOR_OFFLINE","ADMIN_HIDDEN","DELETED")){db.update("UPDATE article SET state=? WHERE id=?",state,target);assertThat(spend(target,1,1,key()).status).isEqualTo(404);assertThat(read(target,1).status).isEqualTo(404);assertThat(get("/articles/"+target+"/coin-state",1).status).isEqualTo(404);}db.update("UPDATE article SET state='PUBLISHED' WHERE id=?",target);
        assertThat(get("/articles/"+a+"/coin-state",1).raw).doesNotContain("authorId","userId","email","voters");assertThat(fact(1001)).isZero();
        long version=db.queryForObject("SELECT public_version_id FROM article WHERE id=?",Long.class,target);var published=db.queryForObject("SELECT published_at FROM article_version WHERE id=?",java.sql.Timestamp.class,version);db.update("UPDATE article_version SET published_at=NULL WHERE id=?",version);
        try{assertThat(spend(target,1,1,key()).status).isEqualTo(404);assertThat(read(target,1).status).isEqualTo(404);}finally{db.update("UPDATE article_version SET published_at=? WHERE id=?",published,version);}
    }
    @Test @Order(7) void onePlusOneAndDirectTwoRespectLifetimeLimitAndReplayFingerprint()throws Exception{
        nextDay(1);assertThat(reward(1,key()).status).isEqualTo(200);String first=key();var receipt=spend(a,1,1,first);assertThat(receipt.status).isEqualTo(200);assertThat(spend(a,1,1,first).data).isEqualTo(receipt.data);assertThat(spend(a,2,1,first).code).isEqualTo("REQUEST_KEY_REUSED");assertThat(spend(b,1,1,first).code).isEqualTo("REQUEST_KEY_REUSED");assertThat(spend(a,1,1,key()).status).isEqualTo(200);assertThat(spend(a,1,1,key()).code).isEqualTo("ARTICLE_COIN_LIMIT");
        tokens.set(0,login(0));assertThat(call("PUT","/me/articles/"+a+"/draft",content(2),0).status).isEqualTo(200);assertThat(call("POST","/me/articles/"+a+"/publish",Map.of("expectedRevision",3,"requestId",key()),0).status).isEqualTo(200);nextDay(1);assertThat(spend(a,1,1,key()).code).isEqualTo("ARTICLE_COIN_LIMIT");assertThat(spend(a,1,1,first).data).isEqualTo(receipt.data);assertThat(spend(b,2,1,key()).status).isEqualTo(200);assertThat(spend(b,1,1,key()).code).isEqualTo("ARTICLE_COIN_LIMIT");reconcile(1);
    }
    @Test @Order(8) void simultaneousDifferentArticleSpendsCannotOverdrawOneWallet()throws Exception{
        nextDay(3);assertThat(reward(3,key()).status).isEqualTo(200);tokens.set(0,login(0));long one=article(0),two=article(0);var i=new java.util.concurrent.atomic.AtomicInteger();var replies=race(12,()->spend(i.getAndIncrement()%2==0?one:two,2,3,key()));assertThat(replies.stream().filter(r->r.status==200).count()).isEqualTo(1);assertThat(replies).allSatisfy(r->assertThat(r.status).isIn(200,409));assertThat(balance(3)).isZero();assertThat(fact(one)+fact(two)).isEqualTo(2);reconcile(3);
    }
    @Test @Order(9) void sameKeyAndDifferentKeysSerializePerArticleLimits()throws Exception{
        nextDay(2);reward(2,key());long one=article(0);String request=key();var replies=race(12,()->spend(one,2,2,request));assertThat(replies).allSatisfy(r->assertThat(r.status).isEqualTo(200));assertThat(replies.stream().map(r->r.data.path("eventId").asString()).distinct().count()).isEqualTo(1);assertThat(fact(one)).isEqualTo(2);
        nextDay(2);reward(2,key());long two=article(0);replies=race(12,()->spend(two,1,2,key()));assertThat(replies.stream().filter(r->r.status==200).count()).isEqualTo(2);assertThat(fact(two)).isEqualTo(2);assertThat(balance(2)).isZero();reconcile(2);
    }
    @Test @Order(10) void multipleDonorsAndRewardSpendInterleavingKeepExactContribution()throws Exception{
        nextDay(1,2,3);tokens.set(0,login(0));long target=article(0);reward(1,key());reward(2,key());
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var one=pool.submit(()->spend(target,2,1,key()));var two=pool.submit(()->spend(target,2,2,key()));assertThat(one.get().status).isEqualTo(200);assertThat(two.get().status).isEqualTo(200);}assertThat(fact(target)).isEqualTo(4);
        long other=article(0);try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var grant=pool.submit(()->reward(3,key()));var debit=pool.submit(()->spend(other,1,3,key()));assertThat(grant.get().status).isEqualTo(200);var result=debit.get();assertThat(result.status).isIn(200,409);assertThat(balance(3)).isEqualTo(result.status==200?1:2);}for(int i:new int[]{1,2,3})reconcile(i);
    }
    @Test @Order(11) void privateLedgerSnapshotIsStableAndContributionIsNotSpendable()throws Exception{
        tokens.set(0,login(0));long contribution=get("/me/coins",0).data.path("receivedContribution").asLong(),old=balance(0);assertThat(contribution).isPositive();assertThat(reward(0,key()).status).isEqualTo(200);assertThat(balance(0)).isEqualTo(old+2);
        tokens.set(1,login(1));long target=article(1);var first=get("/me/coin-ledger?size=1",0).data;long snapshot=first.path("snapshot").asLong(),total=first.path("total").asLong();String firstId=first.path("items").get(0).path("eventId").asString();String request=key();var receipt=spend(target,1,0,request);assertThat(receipt.status).isEqualTo(200);assertThat(get("/me/coins",0).data.path("receivedContribution").asLong()).isEqualTo(contribution);assertThat(balance(0)).isEqualTo(old+1);
        var again=get("/me/coin-ledger?size=1&snapshot="+snapshot,0).data;assertThat(again.path("total").asLong()).isEqualTo(total);assertThat(again.path("items").get(0).path("eventId").asString()).isEqualTo(firstId);
        var ids=new HashSet<String>();for(int page=1;page<=total;page++){var result=get("/me/coin-ledger?size=1&page="+page+"&snapshot="+snapshot,0).data;assertThat(ids.add(result.path("items").get(0).path("eventId").asString())).isTrue();}assertThat(ids).hasSize((int)total);
        db.update("UPDATE article SET state='AUTHOR_OFFLINE' WHERE id=?",target);assertThat(spend(target,1,0,request).data).isEqualTo(receipt.data);assertThat(get("/me/coin-ledger",0).raw).doesNotContain("title","body","email","authorId","contactValue");assertThat(spend(target,1,0,key()).status).isEqualTo(404);db.update("UPDATE article SET state='PUBLISHED' WHERE id=?",target);reconcile(0);
    }
    @Test @Order(12) void publicCountsAndAllSummaryEntrypointsUseOnlyEffectiveFacts()throws Exception{
        tokens.set(0,login(0));tokens.set(1,login(1));assertThat(call("PUT","/articles/"+a+"/favorite",null,1).status).isEqualTo(200);
        for(String route:List.of("/articles","/articles?sort=latest","/articles?jobId=1&majorId=11&companyId=21&topicId=31","/authors/"+users.getFirst()+"/articles","/me/favorites")){
            var result=get(route,route.startsWith("/articles")?-1:1);assertThat(result.status).isEqualTo(200);for(var item:result.data.path("items")){var article=item.has("article")?item.path("article"):item;if(!article.has("counts"))continue;long id=article.path("id").asLong();assertThat(article.path("counts").path("effectiveCoins").asLong()).isEqualTo(fact(id));assertThat(article.path("counts").path("origin").asString()).isEqualTo("LIVE");}
        }
        long previous=Long.MAX_VALUE;var examples=new ArrayList<Map<String,Object>>();for(var item:get("/articles?size=100&sort=recognized",-1).data.path("items")){long coins=item.path("counts").path("effectiveCoins").asLong();assertThat(coins).isLessThanOrEqualTo(previous);previous=coins;examples.add(Map.of("articleId",item.path("id").asLong(),"effectiveCoins",coins,"factCoins",fact(item.path("id").asLong()),"origin",item.path("counts").path("origin").asString()));}
        assertThat(get("/articles/1001",-1).data.path("counts").path("effectiveCoins").asLong()).isZero();assertThat(db.queryForList("SELECT id,effective_coins,effective_likes,count_origin FROM article WHERE owner_user_id IS NULL ORDER BY id")).isEqualTo(originalCoins);Files.writeString(Path.of("target/batch8-live-count-examples.json"),JSON.writeValueAsString(examples));
        assertThat(call("PUT","/articles/"+b+"/like",null,1).status).isEqualTo(200);var tied=new ArrayList<Long>();for(var item:get("/articles?size=100",-1).data.path("items")){long id=item.path("id").asLong();if(id==a||id==b)tied.add(id);}assertThat(tied).containsExactly(b,a);
    }
    @Test @Order(13) void walletEventReadingAndAuditFailuresRollBackAndSameKeyCanRetry()throws Exception{
        for(String table:List.of("coin_wallet","coin_event","business_audit")){
            nextDay(1);String request=key();long before=balance(1),events=count("coin_event"),audits=count("business_audit");String condition=table.equals("business_audit")?"NEW.actor_id="+users.get(1)+" AND NEW.action='LOGIN_REWARD'":"NEW.user_id="+users.get(1);
            var failed=fault(table,table.equals("coin_wallet")?"UPDATE":"INSERT",condition,()->reward(1,request));assertThat(failed.status).isEqualTo(503);assertThat(balance(1)).isEqualTo(before);assertThat(count("coin_event")).isEqualTo(events);assertThat(count("business_audit")).isEqualTo(audits);assertThat(Files.readString(runtime()).lines().filter(s->s.contains(failed.trace)&&s.contains("event=BUSINESS_CHANGE")).count()).isZero();assertThat(reward(1,request).status).isEqualTo(200);
        }
        tokens.set(0,login(0));long target=article(0);long before=balance(1),events=count("coin_event"),audits=count("business_audit");String request=key();assertThat(fault("business_audit","INSERT","NEW.actor_id="+users.get(1)+" AND NEW.action='ARTICLE_COIN'",()->spend(target,1,1,request)).status).isEqualTo(503);assertThat(balance(1)).isEqualTo(before);assertThat(fact(target)).isZero();assertThat(count("coin_event")).isEqualTo(events);assertThat(count("business_audit")).isEqualTo(audits);assertThat(spend(target,1,1,request).status).isEqualTo(200);
        long sessions=count("coin_reading_session");assertThat(fault("business_audit","INSERT","NEW.actor_id="+users.get(1)+" AND NEW.action='READING_START'",()->read(target,1)).status).isEqualTo(503);assertThat(count("coin_reading_session")).isEqualTo(sessions);
        String session=read(target,1).data.path("sessionId").asString();CLOCK.advance(Duration.ofSeconds(180));before=balance(1);String readKey=key();assertThat(fault("coin_event","INSERT","NEW.user_id="+users.get(1)+" AND NEW.action='READING_REWARD'",()->claim(session,1,readKey)).status).isEqualTo(503);assertThat(balance(1)).isEqualTo(before);assertThat(claim(session,1,readKey).status).isEqualTo(200);reconcile(1);
    }
    @Test @Order(14) void articleLockSerializesWithdrawalWithSpendingAndRestorationKeepsFacts()throws Exception{
        nextDay(1);reward(1,key());tokens.set(0,login(0));long target=article(0);long before=balance(1);var source=context.getBean(DataSource.class);
        try(var connection=source.getConnection();var pool=Executors.newVirtualThreadPerTaskExecutor()){
            connection.setAutoCommit(false);try(var statement=connection.prepareStatement("UPDATE article SET state='AUTHOR_OFFLINE' WHERE id=?")){statement.setLong(1,target);statement.executeUpdate();}var future=pool.submit(()->spend(target,1,1,key()));Thread.sleep(120);assertThat(future.isDone()).isFalse();connection.commit();assertThat(future.get().status).isEqualTo(404);
        }
        assertThat(balance(1)).isEqualTo(before);assertThat(fact(target)).isZero();db.update("UPDATE article SET state='PUBLISHED' WHERE id=?",target);String request=key();var receipt=spend(target,1,1,request);assertThat(receipt.status).isEqualTo(200);assertThat(call("POST","/me/articles/"+target+"/unpublish",Map.of("expectedRevision",2),0).status).isEqualTo(200);assertThat(fact(target)).isEqualTo(1);assertThat(spend(target,1,1,request).data).isEqualTo(receipt.data);assertThat(get("/articles/"+target,-1).status).isEqualTo(404);assertThat(call("POST","/me/articles/"+target+"/restore",Map.of("expectedRevision",3),0).status).isEqualTo(200);assertThat(get("/articles/"+target,-1).data.path("counts").path("effectiveCoins").asLong()).isEqualTo(1);
    }
    @Test @Order(15) void allEightActionsAndFailureContractsMatchInDebugAndRelease()throws Exception{
        var contracts=new LinkedHashMap<String,List<String>>();for(String selected:List.of("debug","release")){
            if(!mode.equals(selected)){context.close();boot(selected);}nextDay(1);tokens.set(0,login(0));long target=article(0);long diagBefore=Files.size(diagnostic()),debugBefore=Files.readString(runtime()).lines().filter(s->s.contains("event=DEBUG_STAGE")).count();var results=new ArrayList<Reply>();
            results.add(get("/me/coins",1));results.add(get("/me/coin-ledger",1));results.add(get("/me/rewards",1));results.add(reward(1,key()));Reply started=read(target,1);results.add(started);CLOCK.advance(Duration.ofSeconds(180));results.add(claim(started.data.path("sessionId").asString(),1,key()));results.add(get("/articles/"+target+"/coin-state",1));results.add(spend(target,1,1,key()));
            Reply failed=fault("business_audit","INSERT","NEW.actor_id="+users.get(1)+" AND NEW.action='ARTICLE_COIN'",()->spend(target,1,1,key()));assertThat(failed.status).isEqualTo(503);results.add(failed);assertThat(fact(target)).isEqualTo(1);contracts.put(selected,results.stream().map(r->r.status+":"+r.code).toList());
            if(selected.equals("debug")){assertThat(Files.readString(diagnostic()).lines().filter(s->s.contains(failed.trace)).toList()).anySatisfy(s->assertThat(s).contains("module=COIN","action=ARTICLE_COIN","BusinessAudit.java:","CoinService.java:","stage=AUDIT"));assertThat(Files.readString(runtime())).contains("stage=COMMIT","stage=ROLLBACK");}
            else{assertThat(Files.size(diagnostic())).isEqualTo(diagBefore);assertThat(Files.readString(runtime()).lines().filter(s->s.contains("event=DEBUG_STAGE")).count()).isEqualTo(debugBefore);}
        }assertThat(contracts.get("release")).isEqualTo(contracts.get("debug"));Files.writeString(Path.of("target/batch8-mode-contracts.json"),JSON.writeValueAsString(contracts));
    }
    @Test @Order(16) void restartRetainsWalletSessionsLedgerReceiptsAndPrivateDataStaysOutOfLogs()throws Exception{
        String request=key();long target=article(0);var receipt=spend(target,1,1,request);assertThat(receipt.status).isEqualTo(200);String reading=read(target,1).data.path("sessionId").asString();var wallet=db.queryForList("SELECT * FROM coin_wallet ORDER BY user_id");var events=db.queryForList("SELECT * FROM coin_event ORDER BY sequence_id");var sessions=db.queryForList("SELECT * FROM coin_reading_session ORDER BY id");context.close();boot("release");
        assertThat(db.queryForList("SELECT * FROM coin_wallet ORDER BY user_id")).isEqualTo(wallet);assertThat(db.queryForList("SELECT * FROM coin_event ORDER BY sequence_id")).isEqualTo(events);assertThat(db.queryForList("SELECT * FROM coin_reading_session ORDER BY id")).isEqualTo(sessions);assertThat(read(target,1).data.path("sessionId").asString()).isEqualTo(reading);assertThat(spend(target,1,1,request).data).isEqualTo(receipt.data);
        CLOCK.advance(Duration.ofDays(1));assertThat(spend(target,1,1,request).data).isEqualTo(receipt.data);for(int i:new int[]{0,1,2,3,4}){tokens.set(i,login(i));reconcile(i);}CLOCK.advance(Duration.ofDays(7));assertThat(get("/me/coins",1).status).isEqualTo(401);assertThat(spend(a,1,1,key()).status).isEqualTo(401);
        String runtime=Files.readString(runtime()),diagnostic=Files.readString(diagnostic());for(String secret:secrets){assertThat(runtime).doesNotContain(secret);assertThat(diagnostic).doesNotContain(secret);}Files.writeString(Path.of("target/batch8-runtime-sample.txt"),runtime);Files.writeString(Path.of("target/batch8-diagnostic-sample.txt"),diagnostic);Files.writeString(Path.of("target/batch8-route-coverage.json"),JSON.writeValueAsString(coverage));
        assertThat(db.queryForList("SELECT version FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank",String.class)).containsExactly("1","2","3","4","5","6","7","8");
    }
    @Test @Order(17) void retryWaitingBehindWithdrawalStillReturnsConcurrentCommittedReceipt()throws Exception{
        nextDay(1);reward(1,key());tokens.set(0,login(0));long target=article(0);String request=key();var source=context.getBean(DataSource.class);var withdrawn=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var wallet=source.getConnection();var pool=Executors.newVirtualThreadPerTaskExecutor()){
            wallet.setAutoCommit(false);try(var statement=wallet.prepareStatement("SELECT balance FROM coin_wallet WHERE user_id=? FOR UPDATE")){statement.setLong(1,users.get(1));statement.executeQuery().close();}
            var first=pool.submit(()->spend(target,1,1,request));Thread.sleep(120);assertThat(first.isDone()).isFalse();
            var withdraw=pool.submit(()->{try(var connection=source.getConnection()){connection.setAutoCommit(false);try(var statement=connection.prepareStatement("UPDATE article SET state='AUTHOR_OFFLINE' WHERE id=?")){statement.setLong(1,target);statement.executeUpdate();}withdrawn.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Test barrier expired");connection.commit();}return true;});
            Thread.sleep(120);assertThat(withdrawn.getCount()).isEqualTo(1);var retry=pool.submit(()->spend(target,1,1,request));Thread.sleep(120);assertThat(retry.isDone()).isFalse();wallet.commit();assertThat(withdrawn.await(10,TimeUnit.SECONDS)).isTrue();release.countDown();assertThat(withdraw.get()).isTrue();var original=first.get();assertThat(original.status).isEqualTo(200);assertThat(retry.get().data).isEqualTo(original.data);assertThat(fact(target)).isEqualTo(1);
        }finally{release.countDown();}reconcile(1);
    }
    @Test @Order(18) void changedConfigurationNeverRewritesReceiptOrReopensClaimedDay()throws Exception{
        String request=key();nextDay(1);var receipt=reward(1,request);assertThat(receipt.status).isEqualTo(200);var changed=new CoinService(context.getBean(CoinRepository.class),new CoinRules(3,2,200,3),context.getBean(cn.edu.hzcu.campushub.audit.BusinessAudit.class),CLOCK,context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        var replay=changed.login(users.get(1),request);assertThat(replay.delta()).isEqualTo(2);assertThat(replay.rules().loginReward()).isEqualTo(2);assertThat(replay.eventId()).isEqualTo(receipt.data.path("eventId").asString());assertThatThrownBy(()->changed.login(users.get(1),key())).isInstanceOfSatisfying(cn.edu.hzcu.campushub.common.ApiException.class,e->assertThat(e.code()).isEqualTo("REWARD_ALREADY_CLAIMED"));reconcile(1);
        var reconciled=new ArrayList<Map<String,Object>>();for(long user:users){
            long delta=db.queryForObject("SELECT COALESCE(SUM(delta),0) FROM coin_event WHERE user_id=?",Long.class,user),balance=db.queryForObject("SELECT COALESCE((SELECT balance FROM coin_wallet WHERE user_id=?),0)",Long.class,user),events=db.queryForObject("SELECT COUNT(*) FROM coin_event WHERE user_id=?",Long.class,user),audits=db.queryForObject("SELECT COUNT(*) FROM business_audit WHERE actor_id=? AND action IN ('LOGIN_REWARD','READING_REWARD','ARTICLE_COIN')",Long.class,user);
            assertThat(balance).isEqualTo(delta);assertThat(audits).isEqualTo(events);reconciled.add(Map.of("testUserId",user,"balance",balance,"ledgerSum",delta,"economicEvents",events,"successAudits",audits));
        }Files.writeString(Path.of("target/batch8-reconciliation.json"),JSON.writeValueAsString(reconciled));
        String run=Files.readString(runtime()),diag=Files.readString(diagnostic()),audit=JSON.writeValueAsString(context.getBean(NamedParameterJdbcTemplate.class).queryForList("SELECT * FROM business_audit WHERE actor_id IN (:users) AND action IN ('LOGIN_REWARD','READING_REWARD','ARTICLE_COIN','READING_START') ORDER BY occurred_at,event_id",new MapSqlParameterSource("users",users)));for(String s:secrets){assertThat(run).doesNotContain(s);assertThat(diag).doesNotContain(s);assertThat(audit).doesNotContain(s);}Files.writeString(Path.of("target/batch8-runtime-sample.txt"),run);Files.writeString(Path.of("target/batch8-diagnostic-sample.txt"),diag);Files.writeString(Path.of("target/batch8-audit-sample.json"),audit);
        Files.writeString(Path.of("target/batch8-route-coverage.json"),JSON.writeValueAsString(coverage));
    }
    @AfterAll static void cleanup()throws Exception{
        if(context==null)return;try{
            var named=context.getBean(NamedParameterJdbcTemplate.class);var p=new MapSqlParameterSource("users",users).addValue("emails",emails);if(!users.isEmpty()){
                for(String table:List.of("coin_event","coin_reading_session","coin_wallet","article_like","article_favorite"))named.update("DELETE FROM "+table+" WHERE user_id IN (:users)",p);named.update("DELETE FROM business_audit WHERE actor_id IN (:users)",p);
                var articles=named.queryForList("SELECT id FROM article WHERE owner_user_id IN (:users)",p,Long.class);if(!articles.isEmpty()){
                    p.addValue("articles",articles);for(String table:List.of("article_publish_request","article_version_image","article_draft_image","article_draft_category","article_draft_source_link","author_create_request","media_image","article_draft"))named.update("DELETE FROM "+table+" WHERE article_id IN (:articles)",p);
                    named.update("UPDATE article SET state='DELETED',public_version_id=NULL WHERE id IN (:articles)",p);named.update("DELETE FROM article_version_category WHERE version_id IN (SELECT id FROM article_version WHERE article_id IN (:articles))",p);named.update("DELETE FROM article_source_link WHERE version_id IN (SELECT id FROM article_version WHERE article_id IN (:articles))",p);named.update("DELETE FROM article_version WHERE article_id IN (:articles)",p);named.update("DELETE FROM article WHERE id IN (:articles)",p);
                }
                for(String table:List.of("author_contact_setting","author_profile","auth_session","campus_certification"))named.update("DELETE FROM "+table+" WHERE user_id IN (:users)",p);
            }if(!emails.isEmpty()){named.update("DELETE FROM email_challenge WHERE email IN (:emails)",p);named.update("DELETE FROM auth_email_gate WHERE email IN (:emails)",p);}if(!users.isEmpty())named.update("DELETE FROM platform_user WHERE id IN (:users)",p);
            if(originalArticles!=null){assertThat(db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes,count_origin FROM article ORDER BY id")).isEqualTo(originalArticles);assertThat(db.queryForList("SELECT id,title,summary,body,source_type,source_note FROM article_version ORDER BY id")).isEqualTo(originalVersions);for(var entry:baseline.entrySet())assertThat(count(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());Files.writeString(Path.of("target/batch8-historical-data-protection.json"),JSON.writeValueAsString(Map.of("articlesMatched",true,"versionsMatched",true,"tableCountsMatched",true,"historicalCounters",originalCoins)));}
        }finally{context.close();}
    }
}
