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
    public enum Action { PROFILE_UPDATE, CONTACT_UPDATE, ARTICLE_PUBLISH, ARTICLE_UNPUBLISH, ARTICLE_RESTORE, ARTICLE_DELETE }
    public enum ObjectType { PROFILE, CONTACT, ARTICLE }
    public enum State { ENABLED, DISABLED, DRAFT, PUBLISHED, AUTHOR_OFFLINE, ADMIN_HIDDEN, DELETED }
    private static final Logger LOG=LoggerFactory.getLogger(BusinessAudit.class);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public BusinessAudit(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    public void changed(long actor,Action action,ObjectType type,long object,long before,long after,State oldState,State newState){
        if(!TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("Audit requires a writable business transaction");
        String supplied=RequestTrace.current();
        String trace=supplied!=null&&supplied.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")?supplied:UUID.randomUUID().toString();
        jdbc.update("INSERT INTO business_audit(event_id,occurred_at,trace_id,actor_id,action,object_type,object_id,before_revision,after_revision,before_state,after_state,result) VALUES(?,?,?,?,?,?,?,?,?,?,?,'SUCCESS')",
            UUID.randomUUID().toString(),java.sql.Timestamp.from(clock.instant()),trace,actor,action.name(),type.name(),object,before,after,oldState==null?null:oldState.name(),newState==null?null:newState.name());
        // A rolled-back mutation must not produce a successful runtime change event.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){LOG.info("business changed action={} objectType={} beforeRevision={} afterRevision={}",action,type,before,after);}
        });
    }
    public void unchanged(Action action,ObjectType type){LOG.info("business unchanged action={} objectType={}",action,type);}
}
