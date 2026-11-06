package cn.edu.hzcu.campushub.auth;

import cn.edu.hzcu.campushub.common.ApiException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AuthInputTest {
    @Test void canonicalMailboxDoesNotAssumeStudentIdLength(){assertThat(AuthInput.email("  Alumni+abc@STU.HZCU.EDU.CN  ")).isEqualTo("alumni+abc@stu.hzcu.edu.cn");assertThat(AuthInput.email("1@stu.hzcu.edu.cn")).isEqualTo("1@stu.hzcu.edu.cn");}
    @ParameterizedTest @ValueSource(strings={"x@gmail.com","x@stu.hzcu.edu.cn.evil.com","x@evil-stu.hzcu.edu.cn","x@hzcu.edu.cn","x..y@stu.hzcu.edu.cn","x@stu.hzcu.edu.cn\r\nBcc:x@y.com","中文@stu.hzcu.edu.cn"})
    void onlyExactSchoolDomain(String value){assertThatThrownBy(()->AuthInput.email(value)).isInstanceOf(ApiException.class);}
    @Test void passwordCharacterAndByteLimitsPreventBcryptTruncation(){
        assertThat(AuthInput.password("x".repeat(64))).hasSize(64); assertThat(AuthInput.password("汉".repeat(24))).hasSize(24);
        for(String value:new String[]{"x".repeat(7),"x".repeat(65),"汉".repeat(25),"😀".repeat(19),"\ud800".repeat(8)})assertThatThrownBy(()->AuthInput.password(value)).isInstanceOf(ApiException.class);
    }
    @Test void randomTokensDigestsAndPasswordsDoNotExposeRawValues(){
        var crypto=new AuthCrypto(AuthTestSupport.TEST_KEY);String token=crypto.token();
        assertThat(token.matches("[A-Za-z0-9_-]{43}")).isTrue();assertThat(AuthCrypto.tokenDigest(token).equals(token)).isFalse();
        String id=java.util.UUID.randomUUID().toString(),email="unit@stu.hzcu.edu.cn",code=crypto.code();
        assertThat(code.matches("[0-9]{6}")).isTrue();String hash=crypto.codeDigest(id,email,"REGISTER",code);
        assertThat(AuthCrypto.equal(hash,crypto.codeDigest(id,email,"PASSWORD_RESET",code))).isFalse();
        var encoder=new AuthConfiguration().passwordEncoder();String password=crypto.token(),encoded=encoder.encode(password);
        assertThat(encoded.startsWith("{bcrypt}$2a$12$")).isTrue();assertThat(encoder.matches(password,encoded)).isTrue();
        assertThat(encoded.contains(password)).isFalse();assertThat(encoder.matches(crypto.token(),encoded)).isFalse();
    }
    @Test void productionSmtpRequiresTlsAndNeverEchoesSecret(){
        var settings=new SmtpConfiguration.Settings();settings.setHost("smtp.qiye.163.com");settings.setPort(465);settings.setTimeoutMs(10000);
        settings.setFrom("unit@stu.hzcu.edu.cn");settings.setUsername("unit@stu.hzcu.edu.cn");settings.setPassword("unit-only-sentinel");settings.setTlsMode("NONE");
        assertThatThrownBy(settings::validate).isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("unit-only-sentinel");
        settings.setTlsMode("SSL");assertThatCode(settings::validate).doesNotThrowAnyException();
    }
}
