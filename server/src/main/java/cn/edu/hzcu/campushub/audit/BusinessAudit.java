package cn.edu.hzcu.campushub.audit;

import cn.edu.hzcu.campushub.common.RequestTrace;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component
public class BusinessAudit {
    public enum Action { PROFILE_UPDATE, CONTACT_UPDATE, ARTICLE_PUBLISH, ARTICLE_UNPUBLISH, ARTICLE_RESTORE, ARTICLE_DELETE, COMMENT_CREATE, COMMENT_DELETE, LIKE_SET, LIKE_CANCEL, FAVORITE_SET, FAVORITE_CANCEL, READING_START, LOGIN_REWARD, READING_REWARD, ARTICLE_COIN, CASE_SUBMIT, CASE_IMAGE_UPLOAD, CASE_CLAIM, CASE_RELEASE, CASE_DECISION }
    public enum ObjectType { PROFILE, CONTACT, ARTICLE, COMMENT, LIKE, FAVORITE, READING, WALLET, CASE, CASE_IMAGE }
    public enum State { ENABLED, DISABLED, DRAFT, PUBLISHED, AUTHOR_OFFLINE, ADMIN_HIDDEN, DELETED, VISIBLE, OPEN, PROCESSING, NEEDS_ACTION, CLOSED }
    private static final Logger LOG=LoggerFactory.getLogger(BusinessAudit.class);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public BusinessAudit(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    public void changed(long actor,Action action,ObjectType type,long object,long before,long after,State oldState,State newState){
        changed(RequestTrace.Domain.USER,actor,action,type,object,before,after,oldState,newState);
    }
    public void changed(RequestTrace.Domain domain,long actor,Action action,ObjectType type,long object,long before,long after,State oldState,State newState){
        RequestTrace.stage(RequestTrace.Stage.AUDIT);
        if(!TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("Audit requires a writable business transaction");
        String supplied=RequestTrace.current();
        String trace=supplied!=null&&supplied.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")?supplied:UUID.randomUUID().toString();
        jdbc.update("INSERT INTO business_audit(event_id,occurred_at,trace_id,actor_id,action,object_type,object_id,before_revision,after_revision,before_state,after_state,result,actor_domain) VALUES(?,?,?,?,?,?,?,?,?,?,?,'SUCCESS',?)",
            UUID.randomUUID().toString(),java.sql.Timestamp.from(clock.instant()),trace,actor,action.name(),type.name(),object,before,after,oldState==null?null:oldState.name(),newState==null?null:newState.name(),domain.name());
        // A rolled-back mutation must not produce a successful runtime change event.
        var captured=new RequestTrace.Snapshot(trace,org.slf4j.MDC.get("action"),RequestTrace.context());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){RequestTrace.with(captured,()->LOG.info("business changed action={} objectType={} beforeRevision={} afterRevision={} actorDomain={}",action,type,before,after,domain));}
        });
    }
    public void unchanged(Action action,ObjectType type){unchanged(RequestTrace.Domain.USER,action,type);}
    public void unchanged(RequestTrace.Domain domain,Action action,ObjectType type){LOG.info("business unchanged action={} objectType={} actorDomain={}",action,type,domain);}
}
