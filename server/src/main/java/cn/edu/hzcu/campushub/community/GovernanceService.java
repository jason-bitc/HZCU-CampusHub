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
import org.springframework.transaction.support.TransactionTemplate;

/** Only published-version context is exposed; never an author's unsubmitted draft. */
@Service
public class GovernanceService {
    private final JdbcTemplate db;
    private final AdminAuthService auth;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final NotificationService notifications;
    public GovernanceService(JdbcTemplate db, AdminAuthService auth, PlatformTransactionManager manager, Clock clock,
            NotificationService notifications) {
        this.db=db; this.auth=auth; this.tx=new TransactionTemplate(manager); this.clock=clock;
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
        return page("SELECT id,kind,name,enabled,display_order displayOrder,revision",from,args,page,size);
    }
    public Map<String,Object> events(long admin,int page,int size) {
        auth.active(admin,false);
        return page("SELECT id,admin_id adminId,target_type targetType,target_id targetId,action,before_state beforeState,after_state afterState,revision,reason,occurred_at occurredAt",
            "FROM governance_event",List.of(),page,size);
    }
    static void kind(String value){if(!Set.of("JOB","MAJOR","COMPANY","TOPIC").contains(value))throw ApiException.invalid();}
    private static ApiException conflict(String code){return new ApiException(org.springframework.http.HttpStatus.CONFLICT,code,"对象已变化，请刷新后重试");}
    private Map<String,Object> replay(long admin,String key,String digest) {
        var old=db.queryForList("SELECT content_digest,target_id targetId,after_state state,revision FROM governance_event WHERE admin_id=? AND request_id=?",admin,key);
        if(old.isEmpty())return null;var result=new LinkedHashMap<>(old.getFirst());
        if(!digest.equals(result.remove("content_digest")))throw conflict("REQUEST_KEY_REUSED");return result;
    }
    private Map<String,Object> event(long admin,String key,String digest,String type,long id,String action,String before,String after,long revision,String reason) {
        db.update("INSERT INTO governance_event(admin_id,request_id,content_digest,target_type,target_id,action,before_state,after_state,revision,reason,occurred_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            admin,key,digest,type,id,action,before,after,revision,reason,Timestamp.from(clock.instant()));
        return Map.of("targetId",id,"state",after,"revision",revision);
    }
    public Map<String,Object> moderate(long admin,String type,long id,String action,long expected,String key,String reason) {
        if(!Set.of("ARTICLE","COMMENT").contains(type)||!Set.of("HIDE","RESTORE").contains(action))throw ApiException.invalid();
        String digest=AuthCrypto.tokenDigest(type+"\n"+id+"\n"+action+"\n"+expected+"\n"+reason);
        return RequestTrace.write(tx,s->{
            // Same lock order as existing comment/authoring writes: article -> comment -> admin.
            long article=id;
            if(type.equals("COMMENT"))article=db.queryForList("SELECT article_id FROM discussion_comment WHERE id=?",id).stream().map(r->((Number)r.get("article_id")).longValue()).findFirst().orElseThrow(ApiException::notFound);
            var a=db.queryForList("SELECT state,public_version_id,governance_revision,owner_user_id FROM article WHERE id=? FOR UPDATE",article).stream().findFirst().orElseThrow(ApiException::notFound);
            Map<String,Object> c=type.equals("COMMENT")?db.queryForList("SELECT state,revision,user_id owner_user_id FROM discussion_comment WHERE id=? FOR UPDATE",id).getFirst():a;
            auth.active(admin,true);var previous=replay(admin,key,digest);if(previous!=null)return previous;
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
            return event(admin,key,digest,type,id,action,before,after,revision+1,reason);
        });
    }
    public Map<String,Object> category(long admin,Long id,String kind,String name,boolean enabled,int order,long expected,String key,String reason) {
        kind(kind);String digest=AuthCrypto.tokenDigest("CATEGORY\n"+id+"\n"+kind+"\n"+name+"\n"+enabled+"\n"+order+"\n"+expected+"\n"+reason);
        try{return RequestTrace.write(tx,s->{
            // This sequence serializes IDs with existing authoring sequence use; no MAX(id) race.
            db.update("INSERT INTO authoring_sequence(name,next_value) VALUES('category',1) ON DUPLICATE KEY UPDATE next_value=next_value");
            db.queryForObject("SELECT next_value FROM authoring_sequence WHERE name='category' FOR UPDATE",Long.class);
            auth.active(admin,true);var old=replay(admin,key,digest);if(old!=null)return old;
            long target;String before;long revision;
            if(id==null){if(expected!=0)throw ApiException.invalid();target=count("SELECT COALESCE(MAX(id),0)+1 FROM category");before="ABSENT";revision=1;db.update("INSERT INTO category(id,kind,name,enabled,display_order,revision) VALUES(?,?,?,?,?,1)",target,kind,name,enabled,order);}
            else{target=id;var row=db.queryForList("SELECT kind,enabled,revision FROM category WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(ApiException::notFound);revision=((Number)row.get("revision")).longValue();if(revision!=expected)throw conflict("REVISION_CONFLICT");if(!row.get("kind").equals(kind))throw ApiException.invalid();before=Boolean.TRUE.equals(row.get("enabled"))?"ENABLED":"DISABLED";revision++;db.update("UPDATE category SET name=?,enabled=?,display_order=?,revision=? WHERE id=?",name,enabled,order,revision,id);}
            return event(admin,key,digest,"CATEGORY",target,id==null?"CREATE":"UPDATE",before,enabled?"ENABLED":"DISABLED",revision,reason);
        });}catch(org.springframework.dao.DuplicateKeyException e){throw conflict("CATEGORY_EXISTS");}
    }
}
