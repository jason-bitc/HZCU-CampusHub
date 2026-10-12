package cn.edu.hzcu.campushub.auth;

import static cn.edu.hzcu.campushub.auth.AuthModels.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String USER_SELECT="SELECT u.id,u.email,u.password_hash,u.account_state,u.role,c.state cert_state,c.verified_at FROM platform_user u JOIN campus_certification c ON c.user_id=u.id ";
    public AuthRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    static MapSqlParameterSource p(String name,Object value){return new MapSqlParameterSource(name,value);}
    static Timestamp time(Instant value){return Timestamp.from(value);}
    public Optional<User> user(String email,boolean lock){return jdbc.query(USER_SELECT+"WHERE u.email=:email"+(lock?" FOR UPDATE":""),p("email",email),(r,n)->user(r)).stream().findFirst();}
    public Optional<User> user(long id,boolean lock){return jdbc.query(USER_SELECT+"WHERE u.id=:id"+(lock?" FOR UPDATE":""),p("id",id),(r,n)->user(r)).stream().findFirst();}
    private static User user(ResultSet r)throws SQLException {return new User(r.getLong("id"),r.getString("email"),r.getString("password_hash"),r.getString("account_state"),r.getString("role"),r.getString("cert_state"),r.getTimestamp("verified_at").toInstant());}
    public long createUser(String email,String hash,Instant now){
        var key=new GeneratedKeyHolder(); var params=p("email",email).addValue("hash",hash).addValue("now",time(now));
        jdbc.update("INSERT INTO platform_user(email,password_hash,created_at,password_changed_at) VALUES(:email,:hash,:now,:now)",params,key,new String[]{"id"});
        long id=Objects.requireNonNull(key.getKey()).longValue();
        jdbc.update("INSERT INTO campus_certification(user_id,verified_at) VALUES(:id,:now)",p("id",id).addValue("now",time(now))); return id;
    }
    public Instant lockGate(String email,String purpose){
        var params=p("email",email).addValue("purpose",purpose);
        jdbc.update("INSERT INTO auth_email_gate(email,purpose) VALUES(:email,:purpose) ON DUPLICATE KEY UPDATE email=auth_email_gate.email",params);
        return jdbc.query("SELECT last_attempt_at FROM auth_email_gate WHERE email=:email AND purpose=:purpose FOR UPDATE",params,
                (r,n)->r.getTimestamp(1)==null?null:r.getTimestamp(1).toInstant()).getFirst();
    }
    public long recentAttempts(String email,String purpose,Instant cutoff){return jdbc.queryForObject("SELECT COUNT(*) FROM email_challenge WHERE email=:email AND purpose=:purpose AND created_at>:cutoff",p("email",email).addValue("purpose",purpose).addValue("cutoff",time(cutoff)),Long.class);}
    public void addChallenge(String id,String email,String purpose,String digest,Instant now,Instant expiry,Long resetUser){
        var params=p("id",id).addValue("email",email).addValue("purpose",purpose).addValue("digest",digest).addValue("now",time(now)).addValue("expiry",time(expiry)).addValue("resetUser",resetUser);
        jdbc.update("INSERT INTO email_challenge(id,email,purpose,code_digest,status,created_at,expires_at,reset_user_id) VALUES(:id,:email,:purpose,:digest,'PENDING',:now,:expiry,:resetUser)",params);
        jdbc.update("UPDATE auth_email_gate SET last_attempt_at=:now WHERE email=:email AND purpose=:purpose",params);
    }
    public void sent(String id,String email,String purpose){
        var params=p("id",id).addValue("email",email).addValue("purpose",purpose);
        jdbc.update("UPDATE email_challenge SET status='INVALIDATED' WHERE email=:email AND purpose=:purpose AND status='SENT' AND id<>:id",params);
        status(id,"SENT");
    }
    public void status(String id,String status){jdbc.update("UPDATE email_challenge SET status=:status WHERE id=:id",p("id",id).addValue("status",status));}
    public Optional<Challenge> challenge(String id){return jdbc.query("SELECT * FROM email_challenge WHERE id=:id FOR UPDATE",p("id",id),(r,n)->new Challenge(r.getString("id"),r.getString("email"),r.getString("purpose"),r.getString("code_digest"),r.getString("status"),r.getInt("failed_attempts"),r.getTimestamp("expires_at").toInstant(),r.getObject("reset_user_id",Long.class))).stream().findFirst();}
    public void wrong(Challenge c){jdbc.update("UPDATE email_challenge SET failed_attempts=:attempts,status=:status WHERE id=:id",p("id",c.id()).addValue("attempts",c.attempts()+1).addValue("status",c.attempts()+1>=5?"LOCKED":"SENT"));}
    public void consume(String id,Instant now){jdbc.update("UPDATE email_challenge SET status='CONSUMED',consumed_at=:now WHERE id=:id",p("id",id).addValue("now",time(now)));}
    public void newSession(long user,String digest,Instant now,Instant expiry){jdbc.update("INSERT INTO auth_session(user_id,token_digest,created_at,expires_at) VALUES(:user,:digest,:now,:expiry)",p("user",user).addValue("digest",digest).addValue("now",time(now)).addValue("expiry",time(expiry)));}
    public Optional<Identity> identity(String digest,Instant now){return jdbc.query("SELECT s.id session_id,u.id,u.email,u.password_hash,u.account_state,u.role,c.state cert_state,c.verified_at FROM auth_session s JOIN platform_user u ON u.id=s.user_id JOIN campus_certification c ON c.user_id=u.id WHERE s.token_digest=:digest AND s.revoked_at IS NULL AND s.expires_at>:now",p("digest",digest).addValue("now",time(now)),(r,n)->new Identity(r.getLong("session_id"),user(r).me())).stream().findFirst();}
    public void logout(long session,Instant now){jdbc.update("UPDATE auth_session SET revoked_at=:now WHERE id=:id AND revoked_at IS NULL",p("id",session).addValue("now",time(now)));}
    public void reset(long user,String hash,Instant now){
        var params=p("id",user).addValue("hash",hash).addValue("now",time(now));
        jdbc.update("UPDATE platform_user SET password_hash=:hash,password_changed_at=:now WHERE id=:id",params);
        jdbc.update("UPDATE auth_session SET revoked_at=:now WHERE user_id=:id AND revoked_at IS NULL",params);
    }
}
