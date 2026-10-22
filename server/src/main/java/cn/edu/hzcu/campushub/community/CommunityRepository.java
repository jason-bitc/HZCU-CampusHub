package cn.edu.hzcu.campushub.community;

import static cn.edu.hzcu.campushub.community.CommunityModels.*;
import cn.edu.hzcu.campushub.authoring.MediaStore;
import cn.edu.hzcu.campushub.common.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CommunityRepository {
    private final NamedParameterJdbcTemplate db;
    public CommunityRepository(NamedParameterJdbcTemplate db){this.db=db;}
    private static MapSqlParameterSource p(String key,Object value){return new MapSqlParameterSource(key,value);}
    private static Timestamp time(Instant t){return Timestamp.from(t);}
    private static Case row(ResultSet r)throws SQLException{return new Case(r.getLong("id"),r.getLong("user_id"),r.getString("kind"),r.getString("category"),r.getString("target_type"),r.getObject("article_id",Long.class),r.getObject("comment_id",Long.class),r.getObject("article_version_id",Long.class),r.getString("description"),r.getString("state"),r.getLong("revision"),r.getObject("assignee_id",Long.class),r.getString("content_digest"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());}
    public Case row(long id,boolean lock){RequestTrace.caseId(id);RequestTrace.stage(RequestTrace.Stage.TARGET);return db.query("SELECT * FROM community_case WHERE id=:id"+(lock?" FOR UPDATE":""),p("id",id),(r,n)->row(r)).stream().findFirst().orElseThrow(ApiException::notFound);}
    public Optional<Previous> previous(long user,String key){RequestTrace.stage(RequestTrace.Stage.RECEIPT);return db.query("SELECT id,content_digest FROM community_case WHERE user_id=:user AND request_id=:key",p("user",user).addValue("key",key),(r,n)->new Previous(r.getString(2),new Receipt(r.getLong(1),1,"OPEN"))).stream().findFirst();}
    public Target target(String type,Long id){
        if(type==null)return new Target(null,null,null);RequestTrace.stage(RequestTrace.Stage.TARGET);long article=id;
        if(type.equals("COMMENT"))article=db.query("SELECT article_id FROM discussion_comment WHERE id=:id",p("id",id),(r,n)->r.getLong(1)).stream().findFirst().orElseThrow(ApiException::notFound);
        var version=db.query("SELECT a.public_version_id FROM article a WHERE a.id=:id AND a.state='PUBLISHED' AND EXISTS(SELECT 1 FROM article_version v WHERE v.id=a.public_version_id AND v.article_id=a.id AND v.published_at IS NOT NULL) FOR UPDATE",p("id",article),(r,n)->r.getLong(1)).stream().findFirst().orElseThrow(ApiException::notFound);RequestTrace.article(article);
        if(type.equals("COMMENT")){long count=db.queryForObject("SELECT COUNT(*) FROM discussion_comment WHERE id=:id AND article_id=:article AND state='VISIBLE'",p("id",id).addValue("article",article),Long.class);if(count!=1)throw ApiException.notFound();RequestTrace.comment(id);}
        return new Target(article,type.equals("COMMENT")?id:null,version);
    }
    public void create(long id,long user,CommunityInput.Submission input,Target target,String digest,Instant now){
        db.update("INSERT INTO community_case(id,user_id,request_id,content_digest,kind,category,target_type,article_id,comment_id,article_version_id,description,created_at,updated_at) VALUES(:id,:user,:key,:digest,:kind,:category,:type,:article,:comment,:version,:description,:now,:now)",p("id",id).addValue("user",user).addValue("key",input.requestId()).addValue("digest",digest).addValue("kind",input.kind()).addValue("category",input.category()).addValue("type",input.targetType()).addValue("article",target.article()).addValue("comment",target.comment()).addValue("version",target.version()).addValue("description",input.description()).addValue("now",time(now)));
        capture(id,target);
    }
    private void capture(long id,Target target){
        if(target.article()==null)return;var q=p("id",id).addValue("version",target.version());
        db.update("INSERT INTO community_case_media SELECT :id,m.id,'ARTICLE_IMAGE',m.content_digest,m.mime_type,m.size_bytes FROM article_version_image vi JOIN media_image m ON m.id=vi.image_id WHERE vi.version_id=:version",q);
        if(target.comment()==null)return;var initial=db.queryForMap("SELECT root_comment_id,reply_to_comment_id FROM discussion_comment WHERE id=:comment",p("comment",target.comment()));
        var contexts=new LinkedHashMap<String,Long>();contexts.put("TARGET",target.comment());if(initial.get("root_comment_id")!=null)contexts.put("ROOT",((Number)initial.get("root_comment_id")).longValue());if(initial.get("reply_to_comment_id")!=null)contexts.put("REPLY_TO",((Number)initial.get("reply_to_comment_id")).longValue());
        for(var entry:contexts.entrySet()){
            var args=p("id",id).addValue("role",entry.getKey()).addValue("comment",entry.getValue());
            db.update("INSERT INTO community_case_comment SELECT :id,:role,id,state='VISIBLE',IF(state='VISIBLE',body,NULL),IF(state='VISIBLE',created_at,NULL) FROM discussion_comment WHERE id=:comment",args);
            db.update("INSERT IGNORE INTO community_case_media SELECT :id,m.id,'COMMENT_IMAGE',m.content_digest,m.mime_type,m.size_bytes FROM comment_image m JOIN discussion_comment c ON c.id=m.comment_id WHERE c.id=:comment AND c.state='VISIBLE'",args);
        }
    }
    public void history(long sequence,Case c,RequestTrace.Domain domain,long actor,String action,String outcome,String reply,String note,String reason,Instant now){
        db.update("INSERT INTO community_case_history(sequence_id,case_id,revision,actor_domain,actor_id,action,state,assignee_id,outcome,public_reply,internal_note,reason,occurred_at) VALUES(:sequence,:id,:revision,:domain,:actor,:action,:state,:assignee,:outcome,:reply,:note,:reason,:now)",p("sequence",sequence).addValue("id",c.id()).addValue("revision",c.revision()).addValue("domain",domain.name()).addValue("actor",actor).addValue("action",action).addValue("state",c.state()).addValue("assignee",c.assignee()).addValue("outcome",outcome).addValue("reply",reply).addValue("note",note).addValue("reason",reason).addValue("now",time(now)));
    }
    public void state(long id,String state,Long assignee,Instant now){db.update("UPDATE community_case SET state=:state,assignee_id=:assignee,revision=revision+1,updated_at=:now WHERE id=:id",p("id",id).addValue("state",state).addValue("assignee",assignee).addValue("now",time(now)));}
    public Optional<Previous> adminReceipt(long admin,String key){RequestTrace.stage(RequestTrace.Stage.RECEIPT);return db.query("SELECT case_id,revision,state,content_digest FROM community_admin_receipt WHERE admin_id=:admin AND request_id=:key",p("admin",admin).addValue("key",key),(r,n)->new Previous(r.getString(4),new Receipt(r.getLong(1),r.getLong(2),r.getString(3)))).stream().findFirst();}
    public void receipt(long admin,String key,String digest,Case c){db.update("INSERT INTO community_admin_receipt VALUES(:admin,:key,:id,:digest,:revision,:state)",p("admin",admin).addValue("key",key).addValue("id",c.id()).addValue("digest",digest).addValue("revision",c.revision()).addValue("state",c.state()));}
    public Page page(long user,boolean admin,CommunityPage query){
        long high=db.queryForObject("SELECT COALESCE(MAX(sequence_id),0) FROM community_case_history",new MapSqlParameterSource(),Long.class);long snapshot=query.snapshot()==null?high:Math.min(high,query.snapshot());
        var q=p("user",user).addValue("snapshot",snapshot).addValue("offset",((long)query.page()-1)*query.size()).addValue("size",query.size());
        String from=" FROM community_case c JOIN community_case_history h ON h.case_id=c.id AND h.sequence_id=(SELECT MAX(h2.sequence_id) FROM community_case_history h2 WHERE h2.case_id=c.id AND h2.sequence_id<=:snapshot) WHERE "+(admin?"TRUE":"c.user_id=:user");
        if(query.kind()!=null){from+=" AND c.kind=:kind";q.addValue("kind",query.kind());}if(query.state()!=null){from+=" AND h.state=:state";q.addValue("state",query.state());}
        long total=db.queryForObject("SELECT COUNT(*)"+from,q,Long.class);
        var items=db.query("SELECT c.id,c.kind,c.category,c.article_id,c.comment_id,c.target_type,c.created_at,h.state,h.revision,h.assignee_id,h.occurred_at"+from+" ORDER BY c.id DESC LIMIT :size OFFSET :offset",q,(r,n)->{
            var item=new LinkedHashMap<String,Object>();item.put("caseId",r.getLong("id"));item.put("kind",r.getString("kind"));item.put("category",r.getString("category"));item.put("state",r.getString("state"));item.put("revision",r.getLong("revision"));item.put("createdAt",r.getTimestamp("created_at").toInstant());item.put("updatedAt",r.getTimestamp("occurred_at").toInstant());item.put("targetType",r.getString("target_type"));item.put("articleId",r.getObject("article_id",Long.class));item.put("commentId",r.getObject("comment_id",Long.class));if(admin)item.put("assigneeId",r.getObject("assignee_id",Long.class));return (Map<String,Object>)item;
        });return new Page(items,total,query.page(),query.size(),snapshot);
    }
    public Map<String,Object> detail(Case c,boolean admin){
        var out=new LinkedHashMap<String,Object>();out.put("caseId",c.id());out.put("kind",c.kind());out.put("category",c.category());out.put("description",c.description());out.put("state",c.state());out.put("revision",c.revision());out.put("targetType",c.targetType());out.put("articleId",c.article());out.put("commentId",c.comment());out.put("createdAt",c.created());out.put("updatedAt",c.updated());out.put("images",images(c.id()).stream().map(Image::view).toList());
        if(admin){out.put("submitterId",c.user());out.put("assigneeId",c.assignee());out.put("articleVersionId",c.version());}
        var history=db.query("SELECT * FROM community_case_history WHERE case_id=:id ORDER BY revision",p("id",c.id()),(r,n)->{var item=new LinkedHashMap<String,Object>();item.put("revision",r.getLong("revision"));item.put("action",r.getString("action"));item.put("state",r.getString("state"));item.put("reply",r.getString("public_reply"));item.put("outcome",r.getString("outcome"));item.put("occurredAt",r.getTimestamp("occurred_at").toInstant());if(admin){item.put("actorDomain",r.getString("actor_domain"));item.put("actorId",r.getLong("actor_id"));item.put("assigneeId",r.getObject("assignee_id",Long.class));item.put("internalNote",r.getString("internal_note"));item.put("reason",r.getString("reason"));}return item;});out.put("history",history);return out;
    }
    public Map<String,Object> context(Case c){
        var out=new LinkedHashMap<String,Object>();out.put("caseId",c.id());if(c.version()!=null){
            var article=db.query("SELECT v.id,v.article_id,v.title,v.summary,v.body,v.body_format,v.source_type,v.published_at,a.state FROM article_version v JOIN article a ON a.id=v.article_id WHERE v.id=:version AND v.article_id=:article AND v.published_at IS NOT NULL",p("version",c.version()).addValue("article",c.article()),(r,n)->{var item=new LinkedHashMap<String,Object>();item.put("versionId",r.getLong(1));item.put("articleId",r.getLong(2));item.put("title",r.getString(3));item.put("summary",r.getString(4));item.put("body",r.getString(5));item.put("bodyFormat",r.getString(6));item.put("sourceType",r.getString(7));item.put("publishedAt",r.getTimestamp(8).toInstant());item.put("currentState",r.getString(9));return item;}).stream().findFirst();out.put("article",article.orElse(null));out.put("articleAvailable",article.isPresent());
        }else{out.put("article",null);out.put("articleAvailable",false);}
        out.put("comments",db.query("SELECT context_role,comment_id,available,body,created_at FROM community_case_comment WHERE case_id=:id ORDER BY context_role",p("id",c.id()),(r,n)->{var item=new LinkedHashMap<String,Object>();item.put("role",r.getString(1));item.put("commentId",r.getLong(2));item.put("available",r.getBoolean(3));item.put("body",r.getString(4));item.put("createdAt",r.getTimestamp(5)==null?null:r.getTimestamp(5).toInstant());return item;}));
        out.put("media",db.query("SELECT image_id,source_kind FROM community_case_media WHERE case_id=:id ORDER BY image_id",p("id",c.id()),(r,n)->Map.of("id",r.getString(1),"sourceKind",r.getString(2))));return out;
    }
    private static Image image(ResultSet r)throws SQLException{return new Image(r.getLong("sequence_id"),r.getString("id"),r.getLong("user_id"),r.getObject("case_id",Long.class),r.getString("content_digest"),r.getString("mime_type"),r.getLong("size_bytes"),r.getInt("width"),r.getInt("height"));}
    public Optional<Image> image(String id){return db.query("SELECT * FROM community_case_image WHERE id=:id",p("id",id),(r,n)->image(r)).stream().findFirst();}
    public Optional<Image> upload(long user,String key){return db.query("SELECT * FROM community_case_image WHERE user_id=:user AND request_id=:key",p("user",user).addValue("key",key),(r,n)->image(r)).stream().findFirst();}
    public List<Image> images(long id){return db.query("SELECT * FROM community_case_image WHERE case_id=:id ORDER BY image_order",p("id",id),(r,n)->image(r));}
    public Image createImage(long user,String key,String id,MediaStore.Validated v,Instant now){var holder=new GeneratedKeyHolder();db.update("INSERT INTO community_case_image(id,user_id,request_id,content_digest,mime_type,size_bytes,width,height,created_at) VALUES(:id,:user,:key,:digest,:mime,:size,:width,:height,:now)",p("id",id).addValue("user",user).addValue("key",key).addValue("digest",v.digest()).addValue("mime",v.mime()).addValue("size",v.bytes().length).addValue("width",v.width()).addValue("height",v.height()).addValue("now",time(now)),holder,new String[]{"sequence_id"});return image(id).orElseThrow();}
    public void attach(long user,long id,List<String> images){for(int start=0;start<images.size();start+=100){var args=new ArrayList<MapSqlParameterSource>();for(int i=start;i<Math.min(start+100,images.size());i++)args.add(p("user",user).addValue("id",id).addValue("image",images.get(i)).addValue("order",i));for(int count:db.batchUpdate("UPDATE community_case_image SET case_id=:id,image_order=:order WHERE id=:image AND user_id=:user AND case_id IS NULL",args.toArray(MapSqlParameterSource[]::new)))if(count!=1&&count!=Statement.SUCCESS_NO_INFO)throw new IllegalStateException("Case image binding failed");}}
    public Map<String,Object> contextMedia(long id,String image){return db.query("SELECT content_digest,mime_type,size_bytes FROM community_case_media WHERE case_id=:id AND image_id=:image",p("id",id).addValue("image",image),(r,n)->Map.<String,Object>of("digest",r.getString(1),"mime",r.getString(2),"size",r.getLong(3))).stream().findFirst().orElseThrow(ApiException::notFound);}
}
