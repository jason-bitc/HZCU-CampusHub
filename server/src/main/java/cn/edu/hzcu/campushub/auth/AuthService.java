package cn.edu.hzcu.campushub.auth;

import static cn.edu.hzcu.campushub.auth.AuthModels.*;
import cn.edu.hzcu.campushub.common.ApiException;
import cn.edu.hzcu.campushub.common.RequestTrace;
import static cn.edu.hzcu.campushub.common.RequestTrace.stage;
import static cn.edu.hzcu.campushub.common.RequestTrace.Stage.*;

import java.time.*;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {
    private static final Logger LOG=LoggerFactory.getLogger(AuthService.class);
    private final AuthRepository repository;
    private final AuthCrypto crypto;
    private final PasswordEncoder passwords;
    private final CodeMailer mailer;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final String dummyHash;
    public AuthService(AuthRepository repository,AuthCrypto crypto,PasswordEncoder passwords,CodeMailer mailer,Clock clock,PlatformTransactionManager manager){
        this.repository=repository;this.crypto=crypto;this.passwords=passwords;this.mailer=mailer;this.clock=clock;
        transaction=new TransactionTemplate(manager);transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        dummyHash=passwords.encode(crypto.token());
    }
    private record Outcome<T>(T value,ApiException error){}
    private static <T> Outcome<T> ok(T value){return new Outcome<>(value,null);}
    private static <T> Outcome<T> fail(ApiException error){return new Outcome<>(null,error);}
    private static <T> T unwrap(Outcome<T> outcome){if(outcome.error()!=null)throw outcome.error();return outcome.value();}
    private static ApiException error(HttpStatus status,String code,String message){return new ApiException(status,code,message);}
    private static ApiException invalidCode(){return error(HttpStatus.BAD_REQUEST,"CODE_INVALID","验证码无效或已不可用，请重新申请");}
    public static ApiException unauthorized(){return error(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","请使用有效登录会话");}
    private static ApiException loginFailed(){return error(HttpStatus.UNAUTHORIZED,"LOGIN_FAILED","邮箱或密码错误，或账号当前不可登录");}

    public CodeRequest requestCode(String email,Purpose purpose){
        email=AuthInput.email(email); final String canonical=email;
        return unwrap(RequestTrace.write(transaction,tx->{
            stage(PERSIST);Instant now=clock.instant(); String kind=purpose.name(); Instant last=repository.lockGate(canonical,kind);
            if ((last!=null&&now.isBefore(last.plusSeconds(60))) || repository.recentAttempts(canonical,kind,now.minusSeconds(3600))>=5)
                return fail(error(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","请求过于频繁，请稍后再试"));
            String id=UUID.randomUUID().toString(),code=crypto.code(); Instant expiry=now.plusSeconds(300);
            User resetUser=purpose==Purpose.PASSWORD_RESET?repository.user(canonical,false).orElse(null):null;
            repository.addChallenge(id,canonical,kind,crypto.codeDigest(id,canonical,kind,code),now,expiry,resetUser==null?null:resetUser.id());
            if(purpose==Purpose.PASSWORD_RESET&&resetUser==null){repository.status(id,"NOT_APPLICABLE");}
            else try{stage(MAIL_SEND);mailer.send(canonical,kind,code);stage(PERSIST);repository.sent(id,canonical,kind);}
            catch(SmtpConfiguration.MailDeliveryFailure e){
                RequestTrace.cause(e);repository.status(id,"FAILED"); LOG.warn("verification mail failed category={}","MAIL_UNAVAILABLE");
                return fail(error(HttpStatus.SERVICE_UNAVAILABLE,"MAIL_UNAVAILABLE","邮件服务暂时不可用，请稍后重试"));
            }
            return ok(new CodeRequest(id,expiry,now.plusSeconds(60),purpose==Purpose.PASSWORD_RESET
                    ?"请求已受理，若账号可重置，将收到验证码邮件":"验证码邮件已由SMTP服务器接受，请查看本人邮箱"));
        }));
    }

    private Outcome<Challenge> verify(String id,String email,Purpose purpose,String code,Instant now){
        stage(CODE_VERIFICATION);Challenge c=repository.challenge(id).orElse(null);
        if(c==null||!c.email().equals(email)||!c.purpose().equals(purpose.name())||!c.status().equals("SENT"))return fail(invalidCode());
        if(!now.isBefore(c.expiresAt())){repository.status(id,"EXPIRED");return fail(invalidCode());}
        if(!AuthCrypto.equal(c.digest(),crypto.codeDigest(id,email,purpose.name(),code))){repository.wrong(c);return fail(invalidCode());}
        return ok(c);
    }
    public Me register(String email,String password,String challengeId,String code){
        final String canonical=AuthInput.email(email),raw=AuthInput.password(password),id=AuthInput.challenge(challengeId),digits=AuthInput.code(code);
        return unwrap(RequestTrace.write(transaction,tx->{
            Instant now=clock.instant(); var verified=verify(id,canonical,Purpose.REGISTER,digits,now);
            if(verified.error()!=null)return fail(verified.error());
            if(repository.user(canonical,false).isPresent())return fail(error(HttpStatus.CONFLICT,"ACCOUNT_EXISTS","该邮箱账号已经注册，请登录或找回密码"));
            try{
                stage(PERSIST);long user=repository.createUser(canonical,passwords.encode(raw),now);repository.consume(id,now);RequestTrace.actor(user);
                return ok(repository.user(user,false).orElseThrow().me());
            }catch(DuplicateKeyException duplicate){return fail(error(HttpStatus.CONFLICT,"ACCOUNT_EXISTS","该邮箱账号已经注册，请登录或找回密码"));}
        }));
    }
    public Login login(String email,String password){
        String canonical=AuthInput.email(email),raw=AuthInput.password(password);
        stage(AUTHENTICATION);User original=repository.user(canonical,false).orElse(null);
        boolean matched=passwords.matches(raw,original==null?dummyHash:original.hash());
        if(original==null||!matched)throw loginFailed();
        return unwrap(RequestTrace.write(transaction,tx->{
            User current=repository.user(original.id(),true).orElse(null);
            stage(AUTHORIZATION);
            if(current==null||!current.allowed()||!AuthCrypto.equal(current.hash(),original.hash()))return fail(loginFailed());
            RequestTrace.actor(current.id());stage(PERSIST);Instant now=clock.instant(),expiry=now.plus(Duration.ofDays(7));String token=crypto.token();
            repository.newSession(current.id(),AuthCrypto.tokenDigest(token),now,expiry);return ok(new Login("Bearer",token,expiry));
        }));
    }
    public Identity authenticate(String token){
        stage(AUTHENTICATION);
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw unauthorized();
        var identity=repository.identity(AuthCrypto.tokenDigest(token),clock.instant()).orElseThrow(AuthService::unauthorized);RequestTrace.actor(identity.me().userId());return identity;
    }
    public Me me(Identity identity){
        stage(AUTHORIZATION);
        Me me=identity.me();
        if(!"ACTIVE".equals(me.accountState())||!"VALID".equals(me.campusCertification())||!"USER".equals(me.role()))
            throw error(HttpStatus.FORBIDDEN,"ACCESS_RESTRICTED","账号或校园认证当前不可用于此操作");
        return me;
    }
    public void logout(Identity identity){stage(PERSIST);repository.logout(identity.sessionId(),clock.instant());}
    public void resetPassword(String email,String password,String challengeId,String code){
        String canonical=AuthInput.email(email),raw=AuthInput.password(password),id=AuthInput.challenge(challengeId),digits=AuthInput.code(code);
        unwrap(RequestTrace.write(transaction,tx->{
            Instant now=clock.instant();var verified=verify(id,canonical,Purpose.PASSWORD_RESET,digits,now);
            if(verified.error()!=null)return fail(verified.error());
            Long userId=verified.value().resetUserId();
            User user=userId==null?null:repository.user(userId,true).orElse(null);
            if(user==null||!user.email().equals(canonical))return fail(invalidCode());
            RequestTrace.actor(user.id());stage(PERSIST);repository.reset(user.id(),passwords.encode(raw),now);repository.consume(id,now);return ok(Boolean.TRUE);
        }));
    }
}
