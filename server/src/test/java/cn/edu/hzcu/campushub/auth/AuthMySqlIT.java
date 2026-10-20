package cn.edu.hzcu.campushub.auth;

import static cn.edu.hzcu.campushub.auth.AuthTestSupport.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthMySqlIT {
    private static final ControlledMailer MAIL=new ControlledMailer();
    private static final MutableClock CLOCK=new MutableClock();
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final HttpClient HTTP=HttpClient.newHttpClient();
    private static final List<String> EMAILS=new CopyOnWriteArrayList<>();
    private static ConfigurableApplicationContext context;
    private static String base;
    private static JdbcTemplate db;
    private static List<Map<String,Object>> articles;
    @BeforeAll static void start(){
        boot();db=context.getBean(JdbcTemplate.class);
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("campushub_test");
        articles=db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes FROM article ORDER BY id");
    }
    static void boot(){context=application(MAIL,CLOCK).run("--server.port=0","--server.address=127.0.0.1","--spring.profiles.active=demo");base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");}
    @AfterAll static void stop(){
        if(context!=null){
            try{
                assertThat(db.queryForList("SELECT id,state,public_version_id,effective_coins,effective_likes FROM article ORDER BY id")).isEqualTo(articles);
                if(!EMAILS.isEmpty()){
                    // Exact randomly generated mailboxes created by this test, in campushub_test only.
                    var jdbc=context.getBean(NamedParameterJdbcTemplate.class);var p=new MapSqlParameterSource("emails",EMAILS);
                    jdbc.update("DELETE FROM email_challenge WHERE email IN (:emails)",p);
                    jdbc.update("DELETE FROM auth_email_gate WHERE email IN (:emails)",p);
                    jdbc.update("DELETE s FROM auth_session s JOIN platform_user u ON u.id=s.user_id WHERE u.email IN (:emails)",p);
                    jdbc.update("DELETE c FROM campus_certification c JOIN platform_user u ON u.id=c.user_id WHERE u.email IN (:emails)",p);
                    jdbc.update("DELETE FROM platform_user WHERE email IN (:emails)",p);
                }
            }finally{context.close();}
        }
    }
    static String email(){String e="it"+UUID.randomUUID().toString().replace("-","")+"@stu.hzcu.edu.cn";EMAILS.add(e);return e;}
    static String password(){return UUID.randomUUID()+"!";}
    record Reply(int status,JsonNode data,String code){}
    static Reply call(String method,String path,Object body,String token)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20));
        if(body!=null)builder.header("Content-Type","application/json");if(token!=null)builder.header("Authorization","Bearer "+token);
        var request=builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body instanceof String s?s:JSON.writeValueAsString(body))).build();
        var response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());var json=JSON.readTree(response.body());
        // Booleans prevent assertion output from printing a returned bearer token or any submitted secrets.
        assertThat(json.path("traceId").asString().matches("[0-9a-f-]{36}")).isTrue();
        assertThat(response.headers().firstValue("X-Trace-Id").orElse("").equals(json.path("traceId").asString())).isTrue();
        if(path.contains("/auth/")||path.equals("/api/v1/me"))assertThat(response.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store");
        if(response.statusCode()>=400){assertThat(json.has("data")).isFalse();assertThat(json.has("message")).isTrue();}
        return new Reply(response.statusCode(),json.path("data"),json.path("code").asString());
    }
    static Reply code(String email,String purpose)throws Exception{return call("POST","/api/v1/auth/"+(purpose.equals("REGISTER")?"registration-codes":"password-reset-codes"),Map.of("email",email),null);}
    static Reply register(String email,String password,String challenge,String code)throws Exception{return call("POST","/api/v1/auth/register",Map.of("email",email,"password",password,"challengeId",challenge,"code",code),null);}
    static String registered(String email,String password)throws Exception{var c=code(email,"REGISTER");assertThat(c.status).isEqualTo(202);assertThat(register(email,password,c.data.path("challengeId").asString(),MAIL.code(email,"REGISTER")).status).isEqualTo(201);return email;}
    static String login(String email,String password)throws Exception{var r=call("POST","/api/v1/auth/login",Map.of("email",email,"password",password),null);assertThat(r.status).isEqualTo(200);return r.data.path("accessToken").asString();}

    @Test @Order(1) void registrationPasswordStorageSessionsAndLogout()throws Exception{
        String email=email(),password=password();var c=code(email,"REGISTER");assertThat(c.status).isEqualTo(202);
        assertThat(c.data.has("code")).isFalse();String id=c.data.path("challengeId").asString();
        var r=register(" "+email.toUpperCase(Locale.ROOT)+" ",password,id,MAIL.code(email,"REGISTER"));assertThat(r.status).isEqualTo(201);
        assertThat(r.data.path("role").asString()).isEqualTo("USER");assertThat(r.data.path("campusCertification").asString()).isEqualTo("VALID");assertThat(r.data.has("accessToken")).isFalse();
        String hash=db.queryForObject("SELECT password_hash FROM platform_user WHERE email=?",String.class,email);
        assertThat(hash.startsWith("{bcrypt}$2a$12$")).isTrue();assertThat(hash.contains(password)).isFalse();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM email_challenge WHERE id=? AND code_digest=?",Long.class,id,MAIL.code(email,"REGISTER"))).isZero();
        String first=login(email,password),second=login(email,password);assertThat(first.equals(second)).isFalse();assertThat(first.matches("[A-Za-z0-9_-]{43}")).isTrue();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM auth_session WHERE token_digest=?",Long.class,first)).isZero();
        assertThat(call("GET","/api/v1/me",null,first).status).isEqualTo(200);
        assertThat(call("POST","/api/v1/auth/logout",null,first).status).isEqualTo(200);
        assertThat(call("GET","/api/v1/me",null,first).status).isEqualTo(401);assertThat(call("GET","/api/v1/me",null,second).status).isEqualTo(200);
        assertThat(register(email,password,id,MAIL.code(email,"REGISTER")).status).isEqualTo(400);
    }
    @Test @Order(2) void invalidDomainPasswordPayloadAndNoPrivilegeEscalation()throws Exception{
        for(String invalid:List.of("a@gmail.com","a@stu.hzcu.edu.cn.evil","a@hzcu.edu.cn"))assertThat(code(invalid,"REGISTER").status).isEqualTo(400);
        String email=email(),password=password();var c=code(email,"REGISTER");String id=c.data.path("challengeId").asString();
        assertThat(register(email,"汉".repeat(25),id,MAIL.code(email,"REGISTER")).status).isEqualTo(400);
        var extra=new HashMap<String,String>();extra.put("email",email);extra.put("password",password);extra.put("challengeId",id);extra.put("code",MAIL.code(email,"REGISTER"));extra.put("role","ADMIN");
        assertThat(call("POST","/api/v1/auth/register",extra,null).status).isEqualTo(400);
        assertThat(register(email,password,id,MAIL.code(email,"REGISTER")).status).isEqualTo(201);
        assertThat(db.queryForObject("SELECT role FROM platform_user WHERE email=?",String.class,email)).isEqualTo("USER");
        assertThat(call("GET","/api/v1/me",null,null).status).isEqualTo(401);assertThat(call("GET","/api/v1/me",null,"bad").status).isEqualTo(401);
        assertThat(call("POST","/api/v1/auth/login","{invalid",null).status).isEqualTo(400);
        assertThat(call("POST","/api/v1/auth/login","x".repeat(16385),null).status).isEqualTo(413);
    }
    @Test @Order(3) void wrongAttemptsExpiryAndPurposeCannotBeBypassed()throws Exception{
        String email=email(),password=password();var c=code(email,"REGISTER");String id=c.data.path("challengeId").asString(),correct=MAIL.code(email,"REGISTER");
        String wrong=correct.equals("000000")?"000001":"000000";
        for(int n=0;n<5;n++)assertThat(register(email,password,id,wrong).status).isEqualTo(400);
        assertThat(db.queryForObject("SELECT failed_attempts FROM email_challenge WHERE id=?",Integer.class,id)).isEqualTo(5);
        assertThat(register(email,password,id,correct).status).isEqualTo(400);
        String exp=email();var expired=code(exp,"REGISTER");CLOCK.advance(Duration.ofMinutes(5));
        assertThat(register(exp,password,expired.data.path("challengeId").asString(),MAIL.code(exp,"REGISTER")).status).isEqualTo(400);
        String other=email();var onlyRegister=code(other,"REGISTER");
        assertThat(call("POST","/api/v1/auth/password-resets",Map.of("email",other,"password",password,"challengeId",onlyRegister.data.path("challengeId").asString(),"code",MAIL.code(other,"REGISTER")),null).status).isEqualTo(400);
        String different=email();assertThat(register(different,password,onlyRegister.data.path("challengeId").asString(),MAIL.code(other,"REGISTER")).status).isEqualTo(400);
        assertThat(register(other,password,onlyRegister.data.path("challengeId").asString(),MAIL.code(other,"REGISTER")).status).isEqualTo(201);
    }
    @Test @Order(4) void resendLimitsAndSuccessfulReplacementOnly()throws Exception{
        String failedEmail=email();MAIL.fail=true;try{assertThat(code(failedEmail,"REGISTER").status).isEqualTo(503);}finally{MAIL.fail=false;}
        String failedId=db.queryForObject("SELECT id FROM email_challenge WHERE email=?",String.class,failedEmail);
        assertThat(register(failedEmail,password(),failedId,MAIL.attempted(failedEmail,"REGISTER")).status).isEqualTo(400);
        String email=email(),password=password();var original=code(email,"REGISTER");String old=MAIL.code(email,"REGISTER");
        assertThat(code(email,"REGISTER").status).isEqualTo(429);CLOCK.advance(Duration.ofSeconds(60));
        MAIL.fail=true;try{assertThat(code(email,"REGISTER").status).isEqualTo(503);}finally{MAIL.fail=false;}
        assertThat(db.queryForObject("SELECT status FROM email_challenge WHERE id=?",String.class,original.data.path("challengeId").asString())).isEqualTo("SENT");
        CLOCK.advance(Duration.ofSeconds(60));var fresh=code(email,"REGISTER");
        assertThat(register(email,password,original.data.path("challengeId").asString(),old).status).isEqualTo(400);
        assertThat(register(email,password,fresh.data.path("challengeId").asString(),MAIL.code(email,"REGISTER")).status).isEqualTo(201);
        String rate=email();for(int n=0;n<5;n++){assertThat(code(rate,"REGISTER").status).isEqualTo(202);CLOCK.advance(Duration.ofSeconds(60));}
        assertThat(code(rate,"REGISTER").status).isEqualTo(429);CLOCK.advance(Duration.ofHours(1));assertThat(code(rate,"REGISTER").status).isEqualTo(202);
        assertThat(call("GET","/api/v1/articles",null,null).status).isEqualTo(200);
    }
    @Test @Order(5) void concurrentRequestsAndRegistrationCreateOneAccount()throws Exception{
        String email=email(),password=password();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var sends=executor.invokeAll(List.<Callable<Integer>>of(()->code(email,"REGISTER").status,()->code(email,"REGISTER").status));
            var sendStatuses=new ArrayList<Integer>();for(var f:sends)sendStatuses.add(f.get());assertThat(sendStatuses).containsExactlyInAnyOrder(202,429);
            String id=db.queryForObject("SELECT id FROM email_challenge WHERE email=? AND status='SENT'",String.class,email),digits=MAIL.code(email,"REGISTER");
            var registrations=executor.invokeAll(List.<Callable<Integer>>of(()->register(email,password,id,digits).status,()->register(email,password,id,digits).status));
            var statuses=new ArrayList<Integer>();for(var f:registrations)statuses.add(f.get());assertThat(statuses).containsExactlyInAnyOrder(201,400);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM platform_user WHERE email=?",Long.class,email)).isEqualTo(1);
        }
        CLOCK.advance(Duration.ofSeconds(60));var duplicate=code(email,"REGISTER");
        assertThat(register(email,password,duplicate.data.path("challengeId").asString(),MAIL.code(email,"REGISTER")).status).isEqualTo(409);
    }
    @Test @Order(6) void loginFailureIsUniformAndStatesCheckedOnNewRequests()throws Exception{
        String email=email(),password=password();registered(email,password);String token=login(email,password);
        var wrong=call("POST","/api/v1/auth/login",Map.of("email",email,"password",password()),null);
        var missing=call("POST","/api/v1/auth/login",Map.of("email",email(),"password",password),null);
        assertThat(wrong.status).isEqualTo(401);assertThat(missing.status).isEqualTo(401);assertThat(wrong.code).isEqualTo(missing.code);
        db.update("UPDATE platform_user SET account_state='RESTRICTED' WHERE email=?",email);
        assertThat(call("GET","/api/v1/me",null,token).status).isEqualTo(403);
        assertThat(call("POST","/api/v1/auth/login",Map.of("email",email,"password",password),null).status).isEqualTo(401);
        db.update("UPDATE platform_user SET account_state='ACTIVE' WHERE email=?",email);
        db.update("UPDATE campus_certification c JOIN platform_user u ON u.id=c.user_id SET c.state='REVOKED' WHERE u.email=?",email);
        assertThat(call("GET","/api/v1/me",null,token).status).isEqualTo(403);assertThat(call("POST","/api/v1/auth/logout",null,token).status).isEqualTo(200);
        assertThat(call("GET","/api/v1/articles",null,null).status).isEqualTo(200);
    }
    @Test @Order(7) void resetIsPurposeBoundSingleUseAndRevokesEverySession()throws Exception{
        String email=email(),old=password(),updated=password();registered(email,old);String one=login(email,old),two=login(email,old);
        var code=code(email,"PASSWORD_RESET");String id=code.data.path("challengeId").asString(),digits=MAIL.code(email,"PASSWORD_RESET");
        assertThat(register(email,updated,id,digits).status).isEqualTo(400);
        var body=Map.of("email",email,"password",updated,"challengeId",id,"code",digits);
        assertThat(call("POST","/api/v1/auth/password-resets",body,null).status).isEqualTo(200);
        assertThat(call("GET","/api/v1/me",null,one).status).isEqualTo(401);assertThat(call("GET","/api/v1/me",null,two).status).isEqualTo(401);
        assertThat(call("POST","/api/v1/auth/login",Map.of("email",email,"password",old),null).status).isEqualTo(401);assertThat(login(email,updated).length()==43).isTrue();
        assertThat(call("POST","/api/v1/auth/password-resets",body,null).status).isEqualTo(400);
        String unknown=email();var absent=code(unknown,"PASSWORD_RESET");assertThat(absent.status).isEqualTo(202);assertThat(absent.data.path("notice").asString()).isEqualTo(code.data.path("notice").asString());
        assertThat(MAIL.code(unknown,"PASSWORD_RESET")==null).isTrue();
        assertThat(call("POST","/api/v1/auth/password-resets",Map.of("email",unknown,"password",updated,"challengeId",absent.data.path("challengeId").asString(),"code","000000"),null).status).isEqualTo(400);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM platform_user WHERE email=?",Long.class,unknown)).isZero();
    }
    @Test @Order(8) void sessionAbsoluteExpiryPersistenceAndOldArticleMigrationsPreserved()throws Exception{
        String email=email(),password=password();registered(email,password);String token=login(email,password);
        context.close();boot();db=context.getBean(JdbcTemplate.class);
        assertThat(call("GET","/api/v1/me",null,token).status).isEqualTo(200);
        CLOCK.advance(Duration.ofDays(7));assertThat(call("GET","/api/v1/me",null,token).status).isEqualTo(401);
        for(var entry:Map.of("db/migration/V1__article_reading_schema.sql","1D2FA83D40D1EC19B282EF175581F882BA0398175C79F717B9FF875A8D26D8B1", "db/demo/V2__explicit_demo_articles.sql","8C37951D759435A95B52327CD851C4D2D991C2E83F8A42318EBF13ED7A3EC014").entrySet()){
            try(var stream=AuthMySqlIT.class.getClassLoader().getResourceAsStream(entry.getKey())){var hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));assertThat(hash.toUpperCase(Locale.ROOT)).isEqualTo(entry.getValue());}
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM article WHERE state='PUBLISHED'",Long.class)).isEqualTo(6);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE",Long.class)).isEqualTo(5);
    }
}
