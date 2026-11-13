package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.audit.BusinessAudit;
import cn.edu.hzcu.campushub.auth.AuthCrypto;
import cn.edu.hzcu.campushub.common.ApiException;
import cn.edu.hzcu.campushub.common.RequestTrace;
import cn.edu.hzcu.campushub.notification.NotificationService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ContributionGovernanceService {
    public record Invalidation(long caseId, long adminId, String reason, Instant occurredAt) {}
    public record Item(String eventId, long amount, Instant occurredAt, boolean effective, Invalidation invalidation) {}
    public record Page(List<Item> items, long total, int page, int size) {}
    public record Receipt(long caseId, String eventId, boolean effective) {}

    private record Contribution(long sequenceId, long authorId, boolean effective) {}

    private final JdbcTemplate db;
    private final CommunityRepository cases;
    private final AdminAuthService admins;
    private final BusinessAudit audit;
    private final Clock clock;
    private final NotificationService notifications;
    private final TransactionTemplate read;
    private final TransactionTemplate write;

    public ContributionGovernanceService(JdbcTemplate db, CommunityRepository cases, AdminAuthService admins,
            BusinessAudit audit, Clock clock, PlatformTransactionManager manager, NotificationService notifications) {
        this.db = db;
        this.cases = cases;
        this.admins = admins;
        this.audit = audit;
        this.clock = clock;
        this.notifications = notifications;
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        write = new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private static long articleTarget(CommunityModels.Case report) {
        if (!report.kind().equals("REPORT") || !"ARTICLE".equals(report.targetType()) || report.article() == null) {
            throw ApiException.notFound();
        }
        RequestTrace.article(report.article());
        return report.article();
    }

    public Page list(long admin, long caseId, int page, int size) {
        return read.execute(tx -> {
            admins.active(admin, false);
            long article = articleTarget(cases.row(caseId, false));
            RequestTrace.stage(RequestTrace.Stage.READ);
            long total = db.queryForObject("""
                    SELECT COUNT(*) FROM coin_event WHERE article_id=? AND action='ARTICLE_COIN'
                    """, Long.class, article);
            var items = db.query("""
                    SELECT e.event_id,-e.delta amount,e.occurred_at,e.effective,
                           i.case_id,i.admin_id,i.reason,i.occurred_at invalidated_at
                    FROM coin_event e LEFT JOIN contribution_invalidation i ON i.coin_sequence_id=e.sequence_id
                    WHERE e.article_id=? AND e.action='ARTICLE_COIN'
                    ORDER BY e.sequence_id DESC LIMIT ? OFFSET ?
                    """, (r, n) -> {
                Long correctionCase = r.getObject("case_id", Long.class);
                var invalidation = correctionCase == null ? null : new Invalidation(correctionCase,
                        r.getLong("admin_id"), r.getString("reason"), r.getTimestamp("invalidated_at").toInstant());
                return new Item(r.getString("event_id"), r.getLong("amount"), r.getTimestamp("occurred_at").toInstant(),
                        r.getBoolean("effective"), invalidation);
            }, article, size, ((long) page - 1) * size);
            return new Page(items, total, page, size);
        });
    }

    public Receipt invalidate(long admin, long caseId, String eventId, long expectedRevision,
            String requestId, String reason) {
        String digest = AuthCrypto.tokenDigest(caseId + "\n" + eventId + "\n" + expectedRevision + "\n" + reason);
        return RequestTrace.write(write, tx -> {
            // Case -> administrator is the same lock order used for case decisions.
            var report = cases.row(caseId, true);
            admins.active(admin, true);
            RequestTrace.stage(RequestTrace.Stage.RECEIPT);
            var previous = db.query("""
                    SELECT content_digest FROM contribution_invalidation WHERE admin_id=? AND request_id=?
                    """, (r, n) -> r.getString(1), admin, requestId);
            if (!previous.isEmpty()) {
                if (!previous.getFirst().equals(digest)) {
                    throw conflict("REQUEST_KEY_REUSED", "该请求编号已用于其他处置");
                }
                return new Receipt(caseId, eventId, false);
            }

            long article = articleTarget(report);
            RequestTrace.revisions(expectedRevision, report.revision());
            if (report.revision() != expectedRevision) {
                throw conflict("REVISION_CONFLICT", "案件已变化，请刷新后重试");
            }
            if (!Objects.equals(report.assignee(), admin)) {
                throw conflict("CASE_NOT_ASSIGNEE", "请由认领该举报的管理员处理");
            }
            if (!report.state().equals("PROCESSING") && !report.state().equals("NEEDS_ACTION")) {
                throw conflict("CASE_STATE_CONFLICT", "当前案件状态无法处置贡献");
            }
            RequestTrace.stage(RequestTrace.Stage.TARGET);
            var contribution = db.query("""
                    SELECT sequence_id,author_id,effective FROM coin_event
                    WHERE event_id=? AND article_id=? AND action='ARTICLE_COIN' FOR UPDATE
                    """, (r, n) -> new Contribution(r.getLong(1), r.getLong(2), r.getBoolean(3)), eventId, article)
                    .stream().findFirst().orElseThrow(ApiException::notFound);
            if (!contribution.effective()) {
                throw conflict("CONTRIBUTION_ALREADY_INVALID", "这笔贡献已失效");
            }
            RequestTrace.stage(RequestTrace.Stage.PERSIST);
            db.update("UPDATE coin_event SET effective=FALSE WHERE sequence_id=?", contribution.sequenceId());
            db.update("""
                    INSERT INTO contribution_invalidation
                      (coin_sequence_id,case_id,admin_id,request_id,content_digest,reason,occurred_at)
                    VALUES (?,?,?,?,?,?,?)
                    """, contribution.sequenceId(), caseId, admin, requestId, digest, reason,
                    Timestamp.from(clock.instant()));
            notifications.record(contribution.authorId(), NotificationService.Kind.CONTRIBUTION_INVALIDATED,
                    contribution.sequenceId(), 1, NotificationService.Outcome.INVALIDATED);
            audit.changed(RequestTrace.Domain.COMMUNITY_ADMIN, admin, BusinessAudit.Action.CONTRIBUTION_INVALIDATE,
                    BusinessAudit.ObjectType.CONTRIBUTION, contribution.sequenceId(), 0, 1,
                    BusinessAudit.State.ENABLED, BusinessAudit.State.DISABLED);
            return new Receipt(caseId, eventId, false);
        });
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
