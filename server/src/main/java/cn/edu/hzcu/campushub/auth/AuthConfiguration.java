package cn.edu.hzcu.campushub.auth;

import java.time.Clock;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;

@Configuration
public class AuthConfiguration {
    @Bean @ConditionalOnMissingBean(Clock.class) Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new DelegatingPasswordEncoder("bcrypt",Map.of("bcrypt",new BCryptPasswordEncoder(12))); }
}
