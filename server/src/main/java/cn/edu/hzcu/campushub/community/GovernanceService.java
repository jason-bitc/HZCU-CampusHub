package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.auth.AuthCrypto;
import cn.edu.hzcu.campushub.common.*;
import cn.edu.hzcu.campushub.notification.NotificationService;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Only published-version context is exposed; never an author's unsubmitted draft. */
@Service
public class GovernanceService {
    public record CaseLink(long id, long revision) {}
    private final JdbcTemplate db;
    private final CommunityRepository cases;
    private final AdminAuthService auth;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final NotificationService notifications;
    public GovernanceService(JdbcTemplate db, CommunityRepository cases, AdminAuthService auth, PlatformTransactionManager manager, Clock clock,
            NotificationService notifications) {
        this.db=db; this.cases=cases; this.auth=auth; this.tx=new TransactionTemplate(manager); this.clock=clock;
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.notifications=notifications;
    }
    public Map<String,Object> overview(long admin) {
        auth.active(admin,false);
        return Map.of("openCases",count("SELECT COUNT(*) FROM community_case WHERE state='OPEN'"),
            "processingCases",count("SELECT COUNT(*) FROM community_case WHERE state='PROCESSING'"),
            "needsActionCases",count("SELECT COUNT(*) FROM community_case WHERE state='NEEDS_ACTION'"),
            "hiddenArticles",count("SELECT COUNT(*) FROM article WHERE state='ADMIN_HIDDEN'"));
    }
    private long count(String sql,Object... args) { return db.queryForObject(sql,Long.class,args); }
    private Map<String,Object> page(String select,String from,List<Object> args,int page,int size) {
        long total=count("SELECT COUNT(*) "+from,args.toArray());
        var paged=new ArrayList<>(args); paged.add(size); paged.add(((long)page-1)*size);
        return Map.of("items",db.queryForList(select+" "+from+" ORDER BY id DESC LIMIT ? OFFSET ?",paged.toArray()),"total",total,"page",page,"size",size);
    }
    public Map<String,Object> articles(long admin,String state,int page,int size) {
        auth.active(admin,false); var args=new ArrayList<Object>();
        String from="FROM article a JOIN article_version v ON v.id=a.public_version_id WHERE v.published_at IS NOT NULL";
        if(state!=null) { if(!Set.of("PUBLISHED","AUTHOR_OFFLINE","ADMIN_HIDDEN","DELETED").contains(state))throw ApiException.invalid(); from+=" AND a.state=?";args.add(state); }
        return page("SELECT a.id,v.title,a.state,a.governance_revision revision,a.updated_at updatedAt",from,args,page,size);
    }
    public Map<String,Object> article(long admin,long id) {
        auth.active(admin,false);
        return db.queryForList("SELECT a.id,a.state,a.governance_revision revision,v.id versionId,v.title,v.body,v.body_format bodyFormat FROM article a JOIN article_version v ON v.id=a.public_version_id WHERE a.id=? AND v.published_at IS NOT NULL",id).stream().findFirst().orElseThrow(ApiException::notFound);
    }
    public Map<String,Object> comments(long admin,long article,int page,int size) {
        article(admin,article);
        return page("SELECT id,article_id articleId,root_comment_id rootCommentId,reply_to_comment_id replyToCommentId,state,revision,body,created_at createdAt",
            "FROM discussion_comment WHERE article_id=?",List.of(article),page,size);
    }
    public Map<String,Object> categories(long admin,String kind,int page,int size) {
        auth.active(admin,false);var args=new ArrayList<Object>();String from="FROM category WHERE 1=1";
        if(kind!=null){kind(kind);from+=" AND kind=?";args.add(kind);}
        return page("SELECT id,kind,name,enabled,display_order displayOrder,revision,canonical_id canonicalId",from,args,page,size);
    }
    public Map<String,Object> events(long admin,int page,int size) {
        auth.active(admin,false);
        return page("SELECT id,admin_id adminId,target_type targetType,target_id targetId,action,before_state beforeState,after_state afterState,revision,reason,occurred_at occurredAt,related_category_id canonicalId,case_id caseId,case_revision caseRevision",
            "FROM governance_event",List.of(),page,size);
    }
    static void kind(String value){if(!Set.of("JOB","MAJOR","COMPANY","TOPIC").contains(value))throw ApiException.invalid();}
    private static ApiException conflict(String code){return new ApiException(org.springframework.http.HttpStatus.CONFLICT,code,"对象已变化，请刷新后重试");}
    private Map<String,Object> replay(long admin,String key,String digest) {
        var old=db.queryForList("SELECT content_digest,target_id targetId,after_state state,revision,related_category_id canonicalId,case_id caseId,case_revision caseRevision FROM governance_event WHERE admin_id=? AND request_id=?",admin,key);
        if(old.isEmpty())return null;var result=new LinkedHashMap<>(old.getFirst());
        if(!digest.equals(result.remove("content_digest")))throw conflict("REQUEST_KEY_REUSED");
        if(result.get("canonicalId")==null)result.remove("canonicalId");
        if(result.get("caseId")==null){result.remove("caseId");result.remove("caseRevision");}
        return result;
    }
    private Map<String,Object> event(long admin,String key,String digest,String type,long id,String action,String before,String after,long revision,String reason) {
        return event(admin,key,digest,type,id,action,before,after,revision,reason,null,null);
    }
    private Map<String,Object> event(long admin,String key,String digest,String type,long id,String action,
            String before,String after,long revision,String reason,Long canonical,CaseLink link) {
        db.update("""
                INSERT INTO governance_event(admin_id,request_id,content_digest,target_type,target_id,action,
                  before_state,after_state,revision,reason,occurred_at,related_category_id,case_id,case_revision)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, admin,key,digest,type,id,action,before,after,revision,reason,Timestamp.from(clock.instant()),
                canonical,link==null?null:link.id(),link==null?null:link.revision());
        var receipt=new LinkedHashMap<String,Object>(Map.of("targetId",id,"state",after,"revision",revision));
        if(canonical!=null)receipt.put("canonicalId",canonical);
        if(link!=null){receipt.put("caseId",link.id());receipt.put("caseRevision",link.revision());}
        return receipt;
    }
    public Map<String,Object> moderate(AdminAuthService.Identity identity,String type,long id,String action,long expected,
            String key,String reason,CaseLink link) {
        if(!Set.of("ARTICLE","COMMENT").contains(type)||!Set.of("HIDE","RESTORE").contains(action))throw ApiException.invalid();
        long admin=identity.adminId();
        String command=type+"\n"+id+"\n"+action+"\n"+expected+"\n"+reason;
        // Only linked commands use a new fingerprint, preserving existing patrol receipts.
        String digest=AuthCrypto.tokenDigest(link==null?command:"CASE_LINK\n"+link.id()+"\n"+link.revision()+"\n"+command);
        return RequestTrace.write(tx,s->{
            // With a linked report, lock it before the article, comment and administrator.
            var report=link==null?null:cases.row(link.id(),true);
            long article=id;
            if(type.equals("COMMENT"))article=db.queryForList("SELECT article_id FROM discussion_comment WHERE id=?",id).stream().map(r->((Number)r.get("article_id")).longValue()).findFirst().orElseThrow(ApiException::notFound);
            var a=db.queryForList("SELECT state,public_version_id,governance_revision,owner_user_id FROM article WHERE id=? FOR UPDATE",article).stream().findFirst().orElseThrow(ApiException::notFound);
            Map<String,Object> c=type.equals("COMMENT")?db.queryForList("SELECT state,revision,user_id owner_user_id FROM discussion_comment WHERE id=? FOR UPDATE",id).getFirst():a;
            auth.lockSession(identity);var previous=replay(admin,key,digest);if(previous!=null)return previous;
            if(report!=null)validateCaseLink(report,link,admin,type,id);
            String before=(String)c.get("state");long revision=((Number)c.get(type.equals("ARTICLE")?"governance_revision":"revision")).longValue();
            if(revision!=expected)throw conflict("REVISION_CONFLICT");
            String after;
            if(action.equals("HIDE")){
                if(type.equals("ARTICLE")?!Set.of("PUBLISHED","AUTHOR_OFFLINE").contains(before):!before.equals("VISIBLE"))throw conflict("STATE_CONFLICT");
                after="ADMIN_HIDDEN";
                if(type.equals("ARTICLE"))db.update("INSERT INTO governance_article_hold(article_id,previous_state) VALUES(?,?)",id,before);
            }else{
                if(!before.equals("ADMIN_HIDDEN"))throw conflict("STATE_CONFLICT");
                if(type.equals("ARTICLE")){
                    after=db.queryForList("SELECT previous_state FROM governance_article_hold WHERE article_id=?",id).stream().map(r->(String)r.get("previous_state")).findFirst().orElseThrow(()->conflict("RESTORE_CONTEXT_MISSING"));
                    if(a.get("public_version_id")==null)throw conflict("STATE_CONFLICT");
                    db.update("DELETE FROM governance_article_hold WHERE article_id=?",id);
                }else after="VISIBLE";
            }
            if(type.equals("ARTICLE"))db.update("UPDATE article SET state=?,governance_revision=governance_revision+1,updated_at=? WHERE id=?",after,Timestamp.from(clock.instant()),id);
            else db.update("UPDATE discussion_comment SET state=?,revision=revision+1 WHERE id=?",after,id);
            if (c.get("owner_user_id") instanceof Number owner) {
                var kind = type.equals("ARTICLE") ? NotificationService.Kind.ARTICLE_MODERATION
                        : NotificationService.Kind.COMMENT_MODERATION;
                var outcome = action.equals("HIDE") ? NotificationService.Outcome.HIDDEN
                        : NotificationService.Outcome.RESTORED;
                notifications.record(owner.longValue(), kind, id, revision + 1, outcome);
            }
            return event(admin,key,digest,type,id,action,before,after,revision+1,reason,null,link);
        });
    }

    private static void validateCaseLink(CommunityModels.Case report, CaseLink link, long admin, String type, long target) {
        Long reportedTarget = type.equals("ARTICLE") ? report.article() : report.comment();
        if (!"REPORT".equals(report.kind()) || !type.equals(report.targetType()) || !Objects.equals(reportedTarget,target)) {
            throw ApiException.notFound();
        }
        if (report.revision()!=link.revision()) throw CommunityService.conflict("REVISION_CONFLICT");
        if (!Objects.equals(report.assignee(),admin)) throw CommunityService.conflict("CASE_NOT_ASSIGNEE");
        if (!Set.of("PROCESSING","NEEDS_ACTION").contains(report.state())) throw CommunityService.conflict("CASE_STATE_CONFLICT");
    }
    public Map<String,Object> category(long admin,Long id,String kind,String name,boolean enabled,int order,long expected,String key,String reason) {
        kind(kind);String digest=AuthCrypto.tokenDigest("CATEGORY\n"+id+"\n"+kind+"\n"+name+"\n"+enabled+"\n"+order+"\n"+expected+"\n"+reason);
        try{return RequestTrace.write(tx,s->{
            lockCategories();
            auth.active(admin,true);var old=replay(admin,key,digest);if(old!=null)return old;
            long target;String before;long revision;
            if(id==null){if(expected!=0)throw ApiException.invalid();target=count("SELECT COALESCE(MAX(id),0)+1 FROM category");before="ABSENT";revision=1;db.update("INSERT INTO category(id,kind,name,enabled,display_order,revision) VALUES(?,?,?,?,?,1)",target,kind,name,enabled,order);}
            else{target=id;var row=lockedCategory(id);revision=row.revision();if(revision!=expected)throw conflict("REVISION_CONFLICT");if(row.canonicalId()!=null)throw conflict("CATEGORY_ALREADY_MERGED");if(!row.kind().equals(kind))throw ApiException.invalid();before=row.state();revision++;db.update("UPDATE category SET name=?,enabled=?,display_order=?,revision=? WHERE id=?",name,enabled,order,revision,id);}
            return event(admin,key,digest,"CATEGORY",target,id==null?"CREATE":"UPDATE",before,enabled?"ENABLED":"DISABLED",revision,reason);
        });}catch(org.springframework.dao.DuplicateKeyException e){throw conflict("CATEGORY_EXISTS");}
    }

    public Map<String, Object> mergeCategory(AdminAuthService.Identity admin, long sourceId, long canonicalId,
            long expected, long expectedCanonical, String key, String reason) {
        if (sourceId == canonicalId) throw ApiException.invalid();
        String digest = AuthCrypto.tokenDigest("CATEGORY_MERGE\n" + sourceId + "\n" + canonicalId + "\n"
                + expected + "\n" + expectedCanonical + "\n" + reason);
        return RequestTrace.write(tx, s -> {
            lockCategories();
            auth.lockSession(admin);
            var previous = replay(admin.adminId(), key, digest);
            if (previous != null) return previous;
            var source = lockedCategory(sourceId);
            var canonical = lockedCategory(canonicalId);
            if (source.revision() != expected || canonical.revision() != expectedCanonical) {
                throw conflict("REVISION_CONFLICT");
            }
            if (!source.kind().equals(canonical.kind())) throw ApiException.invalid();
            if (source.canonicalId() != null) throw conflict("CATEGORY_ALREADY_MERGED");
            if (canonical.canonicalId() != null || !canonical.enabled()) throw conflict("CATEGORY_TARGET_UNAVAILABLE");
            RequestTrace.stage(RequestTrace.Stage.PERSIST);
            // Keep every alias pointing directly at its current canonical category.
            db.update("UPDATE category SET canonical_id=?,revision=revision+1 WHERE canonical_id=?", canonicalId, sourceId);
            db.update("UPDATE category SET canonical_id=?,enabled=FALSE,revision=revision+1 WHERE id=?", canonicalId, sourceId);
            RequestTrace.stage(RequestTrace.Stage.AUDIT);
            return event(admin.adminId(),key,digest,"CATEGORY",sourceId,"MERGE",source.state(),"MERGED",
                    expected+1,reason,canonicalId,null);
        });
    }

    private void lockCategories() {
        // Coordinate merges with category creation and editing, including MAX(id) allocation.
        db.update("INSERT INTO authoring_sequence(name,next_value) VALUES('category',1) ON DUPLICATE KEY UPDATE next_value=next_value");
        db.queryForObject("SELECT next_value FROM authoring_sequence WHERE name='category' FOR UPDATE",Long.class);
    }

    private record CategoryRow(String kind, boolean enabled, long revision, Long canonicalId) {
        String state() { return enabled ? "ENABLED" : "DISABLED"; }
    }

    private CategoryRow lockedCategory(long id) {
        return db.query("SELECT kind,enabled,revision,canonical_id FROM category WHERE id=? FOR UPDATE",
                (r, n) -> new CategoryRow(r.getString("kind"), r.getBoolean("enabled"), r.getLong("revision"),
                        r.getObject("canonical_id", Long.class)), id).stream().findFirst().orElseThrow(ApiException::notFound);
    }
}
