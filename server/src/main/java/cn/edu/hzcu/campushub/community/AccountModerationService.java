package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.auth.AuthCrypto;
import cn.edu.hzcu.campushub.auth.AuthModels;
import cn.edu.hzcu.campushub.auth.AuthRepository;
import cn.edu.hzcu.campushub.auth.AuthService;
import cn.edu.hzcu.campushub.common.*;
import cn.edu.hzcu.campushub.notification.NotificationService;
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
    public enum AppealSubject { BEHAVIOR_RESTRICTION, CAMPUS_CERTIFICATION }

    private final NotificationService notifications;
    private final AuthRepository sessions;
    public AccountModerationService(JdbcTemplate db, AdminAuthService auth, Clock clock,
            PlatformTransactionManager manager, NotificationService notifications, AuthRepository sessions) {
        this.db = db;
        this.auth = auth;
        this.clock = clock;
        this.notifications = notifications;
        this.sessions = sessions;
        tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
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
            long id=db.queryForObject("SELECT id FROM community_restriction WHERE user_id=? AND scope=?",Long.class,user,scope);
            history(admin,key,digest,id,before,enabled,revision+1,reason);
            if (before != enabled) {
                notifications.record(user, NotificationService.Kind.RESTRICTION_CHANGED, id, revision + 1,
                        enabled ? NotificationService.Outcome.ENABLED : NotificationService.Outcome.DISABLED);
            }
            return Map.of("restrictionId",id,"revision",revision+1,"enabled",enabled);
        });
    }
    private void history(long admin,String key,String digest,long id,boolean before,boolean after,long revision,String reason){db.update("INSERT INTO community_restriction_event(admin_id,request_id,digest,restriction_id,before_enabled,enabled,revision,reason,occurred_at) VALUES(?,?,?,?,?,?,?,?,?)",admin,key,digest,id,before,after,revision,reason,Timestamp.from(clock.instant()));}
    public Map<String, Object> appeal(AuthModels.Identity identity, AppealSubject subject, long reference,
            String key, String reason) {
        long userId = identity.me().userId();
        boolean certification = subject == AppealSubject.CAMPUS_CERTIFICATION;
        // Preserve the fingerprint used by existing behavior appeals.
        String digest = AuthCrypto.tokenDigest(certification
                ? "CAMPUS_CERTIFICATION\n" + reference + "\n" + reason : reference + "\n" + reason);
        return RequestTrace.write(tx, s -> {
            user(userId);
            if (!sessions.lockCurrentSession(userId, identity.sessionId(), clock.instant())) {
                throw AuthService.unauthorized();
            }
            var existing = db.queryForList("""
                    SELECT id,revision,state,digest FROM community_appeal WHERE user_id=? AND request_id=?
                    """, userId, key);
            if (!existing.isEmpty()) return replay(existing.getFirst(), digest);

            Long restrictionId = certification ? null : reference;
            Long restrictionRevision = certification ? null : appealableRestriction(userId, reference);
            if (certification) ensureAppealableCertification(userId, reference);
            var now = Timestamp.from(clock.instant());
            db.update("""
                    INSERT INTO community_appeal(user_id,request_id,digest,restriction_id,restriction_revision,
                      certification_revision,reason,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)
                    """, userId, key, digest, restrictionId, restrictionRevision, certification ? reference : null,
                    reason, now, now);
            return db.queryForMap("SELECT id,revision,state FROM community_appeal WHERE user_id=? AND request_id=?",
                    userId, key);
        });
    }

    private long appealableRestriction(long userId, long id) {
        var row = restriction(id);
        if (number(row, "user_id") != userId) throw ApiException.notFound();
        if (!Boolean.TRUE.equals(row.get("enabled"))) throw conflict("RESTRICTION_INACTIVE");
        long revision = number(row, "revision");
        if (db.queryForObject("""
                SELECT COUNT(*) FROM community_appeal WHERE restriction_id=? AND restriction_revision=? AND state='OPEN'
                """, Long.class, id, revision) > 0) throw conflict("APPEAL_ALREADY_OPEN");
        return revision;
    }

    private void ensureAppealableCertification(long userId, long expected) {
        var found = db.queryForList("SELECT state,revision FROM campus_certification WHERE user_id=?", userId);
        if (found.isEmpty()) throw ApiException.notFound();
        var current = found.getFirst();
        if (!"REVOKED".equals(current.get("state"))) throw conflict("CERTIFICATION_NOT_REVOKED");
        if (number(current, "revision") != expected) throw conflict("REVISION_CONFLICT");
        if (db.queryForObject("""
                SELECT COUNT(*) FROM community_appeal WHERE user_id=? AND certification_revision=?
                """, Long.class, userId, expected) > 0) throw conflict("CERTIFICATION_APPEAL_EXISTS");
    }

    private static Map<String, Object> replay(Map<String, Object> stored, String digest) {
        var response = new LinkedHashMap<>(stored);
        if (!digest.equals(response.remove("digest"))) throw conflict("REQUEST_KEY_REUSED");
        return response;
    }

    private static final String APPEAL_SELECT = """
            SELECT a.id,
                   CASE WHEN a.certification_revision IS NULL THEN 'BEHAVIOR_RESTRICTION'
                        ELSE 'CAMPUS_CERTIFICATION' END subject,
                   a.restriction_id restrictionId,a.restriction_revision restrictionRevision,
                   a.certification_revision certificationRevision,r.reason certificationRevocationReason,
                   r.occurred_at certificationRevokedAt,
                   a.reason,a.state,a.revision,a.reply,a.created_at createdAt,a.updated_at updatedAt
            """;
    private static final String APPEAL_FROM = """
            FROM community_appeal a LEFT JOIN campus_certification_revocation r
              ON r.user_id=a.user_id AND r.revision=a.certification_revision
            """;

    public Map<String, Object> appeals(Long owner, int page, int size) {
        String where = owner == null ? "" : " WHERE a.user_id=?";
        String adminColumns = owner == null ? ",a.user_id userId,a.decided_by decidedBy " : "";
        var args = new ArrayList<Object>();
        if (owner != null) args.add(owner);
        long total = db.queryForObject("SELECT COUNT(*) FROM community_appeal a" + where, Long.class, args.toArray());
        args.add(size);
        args.add(((long) page - 1) * size);
        var items = db.queryForList(APPEAL_SELECT + adminColumns + APPEAL_FROM + where + " ORDER BY a.id DESC LIMIT ? OFFSET ?",
                args.toArray());
        return Map.of("items", items, "total", total, "page", page, "size", size);
    }

    public Map<String, Object> ownAppeal(long owner, long id) {
        return db.queryForList(APPEAL_SELECT + APPEAL_FROM + " WHERE a.user_id=? AND a.id=?", owner, id).stream()
                .findFirst().orElseThrow(ApiException::notFound);
    }

    public Map<String, Object> decide(AdminAuthService.Identity actor, long id, long expected, String outcome,
            String key, String reply) {
        if (!Set.of("ACCEPTED", "REJECTED").contains(outcome)) throw ApiException.invalid();
        long admin = actor.adminId();
        String digest = AuthCrypto.tokenDigest(id + "\n" + expected + "\n" + outcome + "\n" + reply);
        return RequestTrace.write(tx, s -> {
            long owner = db.queryForList("SELECT user_id FROM community_appeal WHERE id=?", Long.class, id)
                    .stream().findFirst().orElseThrow(ApiException::notFound);
            user(owner);
            auth.lockSession(actor);
            var receipt = db.queryForList("""
                    SELECT digest,appeal_id id,revision,state FROM community_appeal_receipt WHERE admin_id=? AND request_id=?
                    """, admin, key);
            if (!receipt.isEmpty()) return replay(receipt.getFirst(), digest);
            var row = db.queryForMap("""
                    SELECT revision,state,restriction_id,restriction_revision FROM community_appeal WHERE id=? FOR UPDATE
                    """, id);
            if (number(row, "revision") != expected) throw conflict("REVISION_CONFLICT");
            if (!"OPEN".equals(row.get("state"))) throw conflict("APPEAL_CLOSED");
            if ("ACCEPTED".equals(outcome) && row.get("restriction_id") != null) {
                releaseAppealedRestriction(admin, key, digest, reply, row);
            }
            long revision = expected + 1;
            db.update("""
                    UPDATE community_appeal SET state=?,revision=?,reply=?,decided_by=?,updated_at=? WHERE id=?
                    """, outcome, revision, reply, admin, Timestamp.from(clock.instant()), id);
            db.update("""
                    INSERT INTO community_appeal_receipt(admin_id,request_id,digest,appeal_id,revision,state) VALUES (?,?,?,?,?,?)
                    """, admin, key, digest, id, revision, outcome);
            notifications.record(owner, NotificationService.Kind.APPEAL_DECIDED, id, revision,
                    NotificationService.Outcome.valueOf(outcome));
            return Map.of("id", id, "revision", revision, "state", outcome);
        });
    }

    private void releaseAppealedRestriction(long admin, String key, String digest, String reply,
            Map<String, Object> appeal) {
        long id = number(appeal, "restriction_id");
        var current = restriction(id);
        long revision = number(current, "revision");
        if (revision != number(appeal, "restriction_revision") || !Boolean.TRUE.equals(current.get("enabled"))) {
            throw conflict("REVISION_CONFLICT");
        }
        db.update("""
                UPDATE community_restriction SET enabled=FALSE,revision=?,reason=?,admin_id=?,updated_at=? WHERE id=?
                """, revision + 1, reply, admin, Timestamp.from(clock.instant()), id);
        history(admin, key, AuthCrypto.tokenDigest("APPEAL\n" + digest), id, true, false, revision + 1, reply);
    }
}
