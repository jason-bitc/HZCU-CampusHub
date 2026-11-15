package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.audit.BusinessAudit;
import cn.edu.hzcu.campushub.auth.AuthCrypto;
import cn.edu.hzcu.campushub.common.ApiException;
import cn.edu.hzcu.campushub.common.RequestTrace;
import cn.edu.hzcu.campushub.notification.NotificationService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CertificationModerationService {
    public record Revocation(long revision, long adminId, String reason, Instant occurredAt) {}
    public record Status(long userId, String state, long revision, Instant verifiedAt, Revocation lastRevocation) {}
    public record Receipt(long userId, String state, long revision) {}
    private record StoredReceipt(long userId, long revision, String digest) {}

    private final JdbcTemplate db;
    private final AdminAuthService auth;
    private final Clock clock;
    private final TransactionTemplate write;
    private final BusinessAudit audit;
    private final NotificationService notifications;

    public CertificationModerationService(JdbcTemplate db, AdminAuthService auth, Clock clock,
            PlatformTransactionManager manager, BusinessAudit audit, NotificationService notifications) {
        this.db = db;
        this.auth = auth;
        this.clock = clock;
        this.audit = audit;
        this.notifications = notifications;
        write = new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public Status status(long adminId, long userId) {
        auth.active(adminId, false);
        RequestTrace.stage(RequestTrace.Stage.READ);
        return db.query("""
                SELECT c.state,c.revision,c.verified_at,h.revision revoked_revision,h.admin_id,h.reason,h.occurred_at
                FROM campus_certification c LEFT JOIN campus_certification_revocation h
                  ON h.user_id=c.user_id AND h.revision=(
                     SELECT MAX(r.revision) FROM campus_certification_revocation r WHERE r.user_id=c.user_id)
                WHERE c.user_id=?
                """, (r, n) -> {
            Long revision = r.getObject("revoked_revision", Long.class);
            var last = revision == null ? null : new Revocation(revision, r.getLong("admin_id"),
                    r.getString("reason"), r.getTimestamp("occurred_at").toInstant());
            return new Status(userId, r.getString("state"), r.getLong("revision"),
                    r.getTimestamp("verified_at").toInstant(), last);
        }, userId).stream().findFirst().orElseThrow(ApiException::notFound);
    }

    public Receipt revoke(AdminAuthService.Identity admin, long userId, long expected, String key, String reason) {
        String digest = AuthCrypto.tokenDigest(userId + "\n" + expected + "\n" + reason);
        return RequestTrace.write(write, tx -> {
            RequestTrace.stage(RequestTrace.Stage.TARGET);
            // Match the account lock used by publication, comments and email reverification.
            if (db.queryForList("SELECT id FROM platform_user WHERE id=? FOR UPDATE", Long.class, userId).isEmpty()) {
                throw ApiException.notFound();
            }
            auth.lockSession(admin);
            RequestTrace.stage(RequestTrace.Stage.RECEIPT);
            var previous = db.query("""
                    SELECT user_id,revision,digest FROM campus_certification_revocation
                    WHERE admin_id=? AND request_id=?
                    """, (r, n) -> new StoredReceipt(r.getLong(1), r.getLong(2), r.getString(3)),
                    admin.adminId(), key).stream().findFirst();
            if (previous.isPresent()) {
                var receipt = previous.get();
                if (!digest.equals(receipt.digest())) {
                    throw new ApiException(HttpStatus.CONFLICT, "REQUEST_KEY_REUSED", "请求编号已用于其他操作");
                }
                return new Receipt(receipt.userId(), "REVOKED", receipt.revision());
            }

            var current = db.query("SELECT state,revision FROM campus_certification WHERE user_id=?",
                    (r, n) -> new Receipt(userId, r.getString(1), r.getLong(2)), userId)
                    .stream().findFirst().orElseThrow(ApiException::notFound);
            RequestTrace.revisions(expected, current.revision());
            if (current.revision() != expected) {
                throw new ApiException(HttpStatus.CONFLICT, "REVISION_CONFLICT", "认证状态已变化，请刷新后确认");
            }
            if (!"VALID".equals(current.state())) {
                throw new ApiException(HttpStatus.CONFLICT, "CERTIFICATION_ALREADY_REVOKED", "校园认证已撤销");
            }

            RequestTrace.stage(RequestTrace.Stage.PERSIST);
            long next = current.revision() + 1;
            db.update("UPDATE campus_certification SET state='REVOKED',revision=? WHERE user_id=?", next, userId);
            db.update("""
                    INSERT INTO campus_certification_revocation(user_id,revision,admin_id,request_id,digest,reason,occurred_at)
                    VALUES (?,?,?,?,?,?,?)
                    """, userId, next, admin.adminId(), key, digest, reason, Timestamp.from(clock.instant()));
            notifications.record(userId, NotificationService.Kind.CAMPUS_REVOKED, userId, next, null);
            audit.changed(RequestTrace.Domain.COMMUNITY_ADMIN, admin.adminId(), BusinessAudit.Action.CAMPUS_REVOKE,
                    BusinessAudit.ObjectType.CERTIFICATION, userId, current.revision(), next,
                    BusinessAudit.State.VALID, BusinessAudit.State.REVOKED);
            return new Receipt(userId, "REVOKED", next);
        });
    }
}
