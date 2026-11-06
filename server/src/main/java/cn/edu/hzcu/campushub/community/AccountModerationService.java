package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.auth.AuthCrypto;
import cn.edu.hzcu.campushub.common.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountModerationService {
    private final JdbcTemplate db;private final AdminAuthService auth;private final Clock clock;private final TransactionTemplate tx;
    public AccountModerationService(JdbcTemplate db,AdminAuthService auth,Clock clock,PlatformTransactionManager manager){this.db=db;this.auth=auth;this.clock=clock;tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);}
    private static ApiException conflict(String code){return new ApiException(HttpStatus.CONFLICT,code,"状态已经变化，请刷新后确认");}
    private void user(long id){if(db.queryForList("SELECT id FROM platform_user WHERE id=? FOR UPDATE",id).isEmpty())throw ApiException.notFound();}
    private Map<String,Object> restriction(long id){return db.queryForList("SELECT * FROM community_restriction WHERE id=?",id).stream().findFirst().orElseThrow(ApiException::notFound);}
    private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    public Map<String,Object> users(long admin,int page,int size){auth.active(admin,false);return Map.of("items",db.queryForList("SELECT u.id,COALESCE(p.nickname,'城院同学') nickname,u.account_state accountState FROM platform_user u LEFT JOIN author_profile p ON p.user_id=u.id ORDER BY u.id DESC LIMIT ? OFFSET ?",size,((long)page-1)*size),"total",db.queryForObject("SELECT COUNT(*) FROM platform_user",Long.class),"page",page,"size",size);}
    public List<Map<String,Object>> restrictions(long user){return db.queryForList("SELECT id,user_id userId,scope,enabled,revision,reason,updated_at updatedAt FROM community_restriction WHERE user_id=? ORDER BY id",user);}
    public Map<String,Object> set(long admin,long user,String scope,boolean enabled,long expected,String key,String reason){
        if(!Set.of("PUBLISH","COMMENT").contains(scope))throw ApiException.invalid();String digest=AuthCrypto.tokenDigest(user+"\n"+scope+"\n"+enabled+"\n"+expected+"\n"+reason);
        return RequestTrace.write(tx,s->{user(user);auth.active(admin,true);
            var old=db.queryForList("SELECT digest,restriction_id restrictionId,revision,enabled FROM community_restriction_event WHERE admin_id=? AND request_id=?",admin,key);
            if(!old.isEmpty()){var row=new LinkedHashMap<>(old.getFirst());if(!digest.equals(row.remove("digest")))throw conflict("REQUEST_KEY_REUSED");return row;}
            var found=db.queryForList("SELECT * FROM community_restriction WHERE user_id=? AND scope=?",user,scope);long revision=found.isEmpty()?0:number(found.getFirst(),"revision");boolean before=!found.isEmpty()&&Boolean.TRUE.equals(found.getFirst().get("enabled"));if(revision!=expected)throw conflict("REVISION_CONFLICT");
            db.update("INSERT INTO community_restriction(user_id,scope,enabled,revision,reason,admin_id,updated_at) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE enabled=?,revision=?,reason=?,admin_id=?,updated_at=?",user,scope,enabled,revision+1,reason,admin,Timestamp.from(clock.instant()),enabled,revision+1,reason,admin,Timestamp.from(clock.instant()));
            long id=db.queryForObject("SELECT id FROM community_restriction WHERE user_id=? AND scope=?",Long.class,user,scope);history(admin,key,digest,id,before,enabled,revision+1,reason);return Map.of("restrictionId",id,"revision",revision+1,"enabled",enabled);
        });
    }
    private void history(long admin,String key,String digest,long id,boolean before,boolean after,long revision,String reason){db.update("INSERT INTO community_restriction_event(admin_id,request_id,digest,restriction_id,before_enabled,enabled,revision,reason,occurred_at) VALUES(?,?,?,?,?,?,?,?,?)",admin,key,digest,id,before,after,revision,reason,Timestamp.from(clock.instant()));}
    public Map<String,Object> appeal(long user,long restriction,String key,String reason){String digest=AuthCrypto.tokenDigest(restriction+"\n"+reason);
        return RequestTrace.write(tx,s->{user(user);var old=db.queryForList("SELECT id,revision,state,digest FROM community_appeal WHERE user_id=? AND request_id=?",user,key);if(!old.isEmpty()){var row=new LinkedHashMap<>(old.getFirst());if(!digest.equals(row.remove("digest")))throw conflict("REQUEST_KEY_REUSED");return row;}
            var row=restriction(restriction);if(number(row,"user_id")!=user)throw ApiException.notFound();if(!Boolean.TRUE.equals(row.get("enabled")))throw conflict("RESTRICTION_INACTIVE");
            if(db.queryForObject("SELECT COUNT(*) FROM community_appeal WHERE restriction_id=? AND restriction_revision=? AND state='OPEN'",Long.class,restriction,number(row,"revision"))>0)throw conflict("APPEAL_ALREADY_OPEN");
            db.update("INSERT INTO community_appeal(user_id,request_id,digest,restriction_id,restriction_revision,reason,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",user,key,digest,restriction,number(row,"revision"),reason,Timestamp.from(clock.instant()),Timestamp.from(clock.instant()));
            return db.queryForMap("SELECT id,revision,state FROM community_appeal WHERE user_id=? AND request_id=?",user,key);
        });
    }
    public Map<String,Object> appeals(Long owner,int page,int size){String where=owner==null?"":" WHERE user_id=?";var args=new ArrayList<Object>();if(owner!=null)args.add(owner);long total=db.queryForObject("SELECT COUNT(*) FROM community_appeal"+where,Long.class,args.toArray());args.add(size);args.add(((long)page-1)*size);
        return Map.of("items",db.queryForList("SELECT id,restriction_id restrictionId,restriction_revision restrictionRevision,reason,state,revision,reply,created_at createdAt,updated_at updatedAt FROM community_appeal"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",args.toArray()),"total",total,"page",page,"size",size);
    }
    public Map<String,Object> decide(long admin,long id,long expected,String outcome,String key,String reply){if(!Set.of("ACCEPTED","REJECTED").contains(outcome))throw ApiException.invalid();String digest=AuthCrypto.tokenDigest(id+"\n"+expected+"\n"+outcome+"\n"+reply);
        return RequestTrace.write(tx,s->{
            var initial=db.queryForList("SELECT * FROM community_appeal WHERE id=?",id).stream().findFirst().orElseThrow(ApiException::notFound);user(number(initial,"user_id"));auth.active(admin,true);
            var old=db.queryForList("SELECT digest,appeal_id id,revision,state FROM community_appeal_receipt WHERE admin_id=? AND request_id=?",admin,key);if(!old.isEmpty()){var row=new LinkedHashMap<>(old.getFirst());if(!digest.equals(row.remove("digest")))throw conflict("REQUEST_KEY_REUSED");return row;}
            var row=db.queryForMap("SELECT * FROM community_appeal WHERE id=? FOR UPDATE",id);if(number(row,"revision")!=expected)throw conflict("REVISION_CONFLICT");if(!row.get("state").equals("OPEN"))throw conflict("APPEAL_CLOSED");
            if(outcome.equals("ACCEPTED")){long restriction=number(row,"restriction_id");var r=restriction(restriction);if(number(r,"revision")!=number(row,"restriction_revision")||!Boolean.TRUE.equals(r.get("enabled")))throw conflict("REVISION_CONFLICT");long revision=number(r,"revision")+1;db.update("UPDATE community_restriction SET enabled=FALSE,revision=?,reason=?,admin_id=?,updated_at=? WHERE id=?",revision,reply,admin,Timestamp.from(clock.instant()),restriction);history(admin,key,AuthCrypto.tokenDigest("APPEAL\n"+digest),restriction,true,false,revision,reply);}
            db.update("UPDATE community_appeal SET state=?,revision=revision+1,reply=?,decided_by=?,updated_at=? WHERE id=?",outcome,reply,admin,Timestamp.from(clock.instant()),id);
            db.update("INSERT INTO community_appeal_receipt(admin_id,request_id,digest,appeal_id,revision,state) VALUES(?,?,?,?,?,?)",admin,key,digest,id,expected+1,outcome);return Map.of("id",id,"revision",expected+1,"state",outcome);
        });
    }
}
