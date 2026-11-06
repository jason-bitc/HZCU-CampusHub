package cn.edu.hzcu.campushub.auth;

import cn.edu.hzcu.campushub.CampusHubApplication;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;

public final class AuthTestSupport {
    private AuthTestSupport(){}
    public static final String TEST_KEY=Base64.getEncoder().encodeToString("controlled-test-key-not-used-in-development".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    public static final class MutableClock extends Clock {
        private final AtomicReference<Instant> time=new AtomicReference<>(Instant.parse("2030-01-01T00:00:00Z"));
        public Instant instant(){return time.get();} public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;}
        public void advance(Duration duration){time.updateAndGet(i->i.plus(duration));}
    }
    public static final class ControlledMailer implements CodeMailer {
        private final Map<String,String> codes=new ConcurrentHashMap<>();
        private final Map<String,String> attempted=new ConcurrentHashMap<>();
        public volatile boolean fail;
        public void send(String email,String purpose,String code){attempted.put(email+"/"+purpose,code);if(fail)throw new SmtpConfiguration.MailDeliveryFailure("CONTROLLED_TEST_FAILURE");codes.put(email+"/"+purpose,code);}
        public String attempted(String email,String purpose){return attempted.get(email+"/"+purpose);}
        public String code(String email,String purpose){return codes.get(email+"/"+purpose);}
        @Override public String toString(){return "ControlledMailer[redacted]";}
    }
    public static SpringApplication application(ControlledMailer mailer,MutableClock clock){
        String username=require("CAMPUSHUB_TEST_DB_USERNAME"),password=require("CAMPUSHUB_TEST_DB_PASSWORD");
        var app=new SpringApplication(CampusHubApplication.class);app.setDefaultProperties(Map.of("spring.main.banner-mode","off"));
        app.addInitializers(ctx->{
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("controlled-auth-test",Map.of(
                    "campushub.db.name","campushub_test","campushub.db.username",username,"campushub.db.password",password,"campushub.auth.pepper",TEST_KEY,
                    "campushub.media.root",java.nio.file.Path.of("target","auth-test-media").toAbsolutePath().toString(),"campushub.logs.enabled","false","campushub.runtime-environment","test")));
            ctx.getBeanFactory().registerSingleton("controlledCodeMailer",mailer);
            ctx.getBeanFactory().registerSingleton("controlledClock",clock);
        });return app;
    }
    private static String require(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalStateException("Set "+name);return value;}
}
