package cn.edu.hzcu.campushub.notification;

import cn.edu.hzcu.campushub.common.ApiException;
import cn.edu.hzcu.campushub.common.RequestTrace;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationService {
    public enum Kind {
        CAMPUS_VERIFIED("校园邮箱认证已通过"),
        COMMENT_REPLY("有人回复了你的评论"),
        CASE_DECISION("你提交的案件有处理结果"),
        ARTICLE_MODERATION("你的文章有治理结果"),
        COMMENT_MODERATION("你的评论有治理结果"),
        RESTRICTION_CHANGED("你的社区权限有更新"),
        APPEAL_DECIDED("你的申诉有处理结果"),
        CONTRIBUTION_INVALIDATED("你的文章有一笔贡献被标记为失效");

        private final String message;

        Kind(String message) {
            this.message = message;
        }
    }

    public record Target(String type, Long id, Long articleId, Long rootCommentId) {}
    public enum Outcome {
        HIDDEN, RESTORED, ANSWERED, RECORDED, NO_VIOLATION, DUPLICATE, REQUIRES_GOVERNANCE,
        GOVERNANCE_COMPLETED, ENABLED, DISABLED, ACCEPTED, REJECTED, INVALIDATED
    }

    public record Item(long id, Kind kind, String message, Outcome outcome, Instant createdAt, Instant readAt,
                       Target target) {}
    public record Page(List<Item> items, long total, long unreadCount, int page, int size) {}
    public record ReadReceipt(long id, Instant readAt) {}

    private static final String INBOX = """
            SELECT n.id,n.kind,n.outcome,n.created_at,n.read_at,
                   u.account_state='ACTIVE' AND cert.state='VALID' AND u.role='USER' certified,
                   a.id visible_article,c.id visible_comment,COALESCE(c.root_comment_id,c.id) root_comment,
                   k.id own_case,r.id own_restriction,p.id own_appeal
            FROM personal_notification n
            JOIN platform_user u ON u.id=n.recipient_id
            JOIN campus_certification cert ON cert.user_id=u.id
            LEFT JOIN discussion_comment c ON n.kind IN ('COMMENT_REPLY','COMMENT_MODERATION')
                 AND c.id=n.source_id AND c.state='VISIBLE'
            LEFT JOIN coin_event e ON n.kind='CONTRIBUTION_INVALIDATED'
                 AND e.sequence_id=n.source_id AND e.author_id=n.recipient_id
            LEFT JOIN article a ON a.id=CASE WHEN n.kind='ARTICLE_MODERATION' THEN n.source_id
                                           ELSE COALESCE(c.article_id,e.article_id) END
                 AND a.state='PUBLISHED'
                 AND EXISTS(SELECT 1 FROM article_version v WHERE v.id=a.public_version_id
                            AND v.article_id=a.id AND v.published_at IS NOT NULL)
            LEFT JOIN community_case k ON n.kind='CASE_DECISION' AND k.id=n.source_id AND k.user_id=n.recipient_id
            LEFT JOIN community_restriction r ON n.kind='RESTRICTION_CHANGED'
                 AND r.id=n.source_id AND r.user_id=n.recipient_id
            LEFT JOIN community_appeal p ON n.kind='APPEAL_DECIDED' AND p.id=n.source_id AND p.user_id=n.recipient_id
            WHERE n.recipient_id=? ORDER BY n.id DESC LIMIT ? OFFSET ?
            """;

    private final JdbcTemplate db;
    private final Clock clock;
    private final TransactionTemplate read;
    private final TransactionTemplate write;

    public NotificationService(JdbcTemplate db, Clock clock, PlatformTransactionManager manager) {
        this.db = db;
        this.clock = clock;
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        write = new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(long recipient, Kind kind, long sourceId, long revision, Outcome outcome) {
        db.update("""
                INSERT INTO personal_notification(recipient_id,kind,source_id,source_revision,outcome,created_at)
                VALUES (?,?,?,?,?,?)
                """, recipient, kind.name(), sourceId, revision, outcome == null ? null : outcome.name(),
                Timestamp.from(clock.instant()));
    }

    public Page list(long recipient, int page, int size) {
        return read.execute(tx -> {
            RequestTrace.stage(RequestTrace.Stage.READ);
            long total = db.queryForObject(
                    "SELECT COUNT(*) FROM personal_notification WHERE recipient_id=?", Long.class, recipient);
            long unread = db.queryForObject(
                    "SELECT COUNT(*) FROM personal_notification WHERE recipient_id=? AND read_at IS NULL",
                    Long.class, recipient);
            var items = db.query(INBOX, (r, n) -> {
                Kind kind = Kind.valueOf(r.getString("kind"));
                String outcome = r.getString("outcome");
                return new Item(r.getLong("id"), kind, kind.message, outcome == null ? null : Outcome.valueOf(outcome),
                        r.getTimestamp("created_at").toInstant(),
                        instant(r.getTimestamp("read_at")), target(kind, r));
            }, recipient, size, ((long) page - 1) * size);
            return new Page(items, total, unread, page, size);
        });
    }

    public ReadReceipt markRead(long recipient, long id) {
        return RequestTrace.write(write, tx -> {
            RequestTrace.stage(RequestTrace.Stage.PERSIST);
            db.update("UPDATE personal_notification SET read_at=? WHERE id=? AND recipient_id=? AND read_at IS NULL",
                    Timestamp.from(clock.instant()), id, recipient);
            return db.query("SELECT read_at FROM personal_notification WHERE id=? AND recipient_id=?",
                    (r, n) -> new ReadReceipt(id, r.getTimestamp(1).toInstant()), id, recipient)
                    .stream().findFirst().orElseThrow(ApiException::notFound);
        });
    }

    private static Instant instant(Timestamp time) {
        return time == null ? null : time.toInstant();
    }

    private static Target target(Kind kind, ResultSet r) throws SQLException {
        if (kind == Kind.CAMPUS_VERIFIED) return new Target("CERTIFICATION", null, null, null);
        if (!r.getBoolean("certified")) return null;
        return switch (kind) {
            case COMMENT_REPLY, COMMENT_MODERATION -> {
                Long article = r.getObject("visible_article", Long.class);
                Long comment = r.getObject("visible_comment", Long.class);
                yield article == null || comment == null ? null
                        : new Target("COMMENT", comment, article, r.getObject("root_comment", Long.class));
            }
            case ARTICLE_MODERATION, CONTRIBUTION_INVALIDATED -> ownedTarget(r, "visible_article", "ARTICLE");
            case CASE_DECISION -> ownedTarget(r, "own_case", "CASE");
            case RESTRICTION_CHANGED -> ownedTarget(r, "own_restriction", "RESTRICTION");
            case APPEAL_DECIDED -> ownedTarget(r, "own_appeal", "APPEAL");
            case CAMPUS_VERIFIED -> throw new IllegalStateException("Certification target handled above");
        };
    }

    private static Target ownedTarget(ResultSet r, String field, String type) throws SQLException {
        Long id = r.getObject(field, Long.class);
        return id == null ? null : new Target(type, id, null, null);
    }
}
