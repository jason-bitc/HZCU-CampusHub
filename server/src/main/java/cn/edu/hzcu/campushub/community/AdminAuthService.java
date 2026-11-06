package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.auth.*;
import cn.edu.hzcu.campushub.common.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminAuthService {
    public record Identity(long adminId,long sessionId){}
    public record Login(String tokenType,String accessToken,Instant expiresAt){@Override public String toString(){return "AdminLogin[redacted]";}}
    private record Account(long id,String hash,boolean enabled){}
    private record Gate(int failures,Instant started){}
    private record Outcome(Login login,ApiException error){}
    private final JdbcTemplate db;private final PasswordEncoder encoder;private final AuthCrypto crypto;private final Clock clock;private final TransactionTemplate write;private final String dummy;
    public AdminAuthService(JdbcTemplate db,PasswordEncoder encoder,AuthCrypto crypto,Clock clock,PlatformTransactionManager manager){this.db=db;this.encoder=encoder;this.crypto=crypto;this.clock=clock;write=new TransactionTemplate(manager);write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);dummy=encoder.encode(crypto.token());}
    public static ApiException unauthorized(){return new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","请使用有效社区管理会话");}
    public void active(long id,boolean lock){RequestTrace.stage(RequestTrace.Stage.AUTHORIZATION);var found=db.query("SELECT enabled FROM community_admin WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->r.getBoolean(1),id);if(found.isEmpty()||!found.getFirst())throw unauthorized();RequestTrace.actor(id);}
    public Login login(String name,String password){
        String canonical=CommunityInput.login(name),raw=AuthInput.password(password),gate=AuthCrypto.tokenDigest(canonical);
        Outcome outcome=RequestTrace.write(write,tx->{
            Instant now=clock.instant();db.update("INSERT INTO community_admin_login_gate(name_digest,window_started) VALUES(?,?) ON DUPLICATE KEY UPDATE name_digest=community_admin_login_gate.name_digest",gate,Timestamp.from(now));
            var row=db.queryForObject("SELECT failures,window_started FROM community_admin_login_gate WHERE name_digest=? FOR UPDATE",(r,n)->new Gate(r.getInt(1),r.getTimestamp(2).toInstant()),gate);int failures=row.failures();Instant start=row.started();
            if(!now.isBefore(start.plusSeconds(900))){failures=0;db.update("UPDATE community_admin_login_gate SET failures=0,window_started=? WHERE name_digest=?",Timestamp.from(now),gate);}
            if(failures>=5)return new Outcome(null,new ApiException(HttpStatus.TOO_MANY_REQUESTS,"ADMIN_RATE_LIMITED","管理登录尝试过于频繁，请稍后重试"));
            RequestTrace.stage(RequestTrace.Stage.AUTHENTICATION);Account account=db.query("SELECT id,password_hash,enabled FROM community_admin WHERE login_name=? FOR UPDATE",(r,n)->new Account(r.getLong(1),r.getString(2),r.getBoolean(3)),canonical).stream().findFirst().orElse(null);
            boolean matches=encoder.matches(raw,account==null?dummy:account.hash());if(account==null||!matches||!account.enabled()){
                db.update("UPDATE community_admin_login_gate SET failures=failures+1 WHERE name_digest=?",gate);return new Outcome(null,new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_LOGIN_FAILED","管理账号或密码错误，或当前不可登录"));
            }
            RequestTrace.actor(account.id());RequestTrace.stage(RequestTrace.Stage.PERSIST);String token="adm_"+crypto.token();Instant expires=now.plusSeconds(8*3600);
            db.update("INSERT INTO community_admin_session(admin_id,token_digest,created_at,expires_at) VALUES(?,?,?,?)",account.id(),AuthCrypto.tokenDigest(token),Timestamp.from(now),Timestamp.from(expires));db.update("UPDATE community_admin_login_gate SET failures=0,window_started=? WHERE name_digest=?",Timestamp.from(now),gate);return new Outcome(new Login("Bearer",token,expires),null);
        });if(outcome.error()!=null)throw outcome.error();return outcome.login();
    }
    public Identity authenticate(String token){
        RequestTrace.stage(RequestTrace.Stage.AUTHENTICATION);if(token==null||!token.matches("adm_[A-Za-z0-9_-]{43}"))throw unauthorized();
        var identity=db.query("SELECT s.admin_id,s.id FROM community_admin_session s JOIN community_admin a ON a.id=s.admin_id WHERE s.token_digest=? AND s.revoked_at IS NULL AND s.expires_at>? AND a.enabled=TRUE",(r,n)->new Identity(r.getLong(1),r.getLong(2)),AuthCrypto.tokenDigest(token),Timestamp.from(clock.instant())).stream().findFirst().orElseThrow(AdminAuthService::unauthorized);RequestTrace.actor(identity.adminId());RequestTrace.stage(RequestTrace.Stage.AUTHORIZATION);return identity;
    }
    public void logout(Identity me){RequestTrace.write(write,tx->{active(me.adminId(),true);RequestTrace.stage(RequestTrace.Stage.PERSIST);db.update("UPDATE community_admin_session SET revoked_at=? WHERE id=? AND admin_id=? AND revoked_at IS NULL",Timestamp.from(clock.instant()),me.sessionId(),me.adminId());return true;});}
}
