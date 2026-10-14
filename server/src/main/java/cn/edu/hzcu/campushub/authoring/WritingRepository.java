package cn.edu.hzcu.campushub.authoring;

import static cn.edu.hzcu.campushub.authoring.WritingModels.*;
import cn.edu.hzcu.campushub.article.ArticleModels.SourceLink;
import cn.edu.hzcu.campushub.common.ApiException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class WritingRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public WritingRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    static MapSqlParameterSource p(String key,Object value){return new MapSqlParameterSource(key,value);}
    static Timestamp time(Instant t){return Timestamp.from(t);}
    public long allocate(String name){long next=jdbc.queryForObject("SELECT next_value FROM authoring_sequence WHERE name=:name FOR UPDATE",p("name",name),Long.class);jdbc.update("UPDATE authoring_sequence SET next_value=next_value+1 WHERE name=:name",p("name",name));return next;}
    public void lockUser(long id){if(jdbc.query("SELECT id FROM platform_user WHERE id=:id FOR UPDATE",p("id",id),(r,n)->r.getLong(1)).isEmpty())throw ApiException.notFound();}
    public Profile profile(long user){return jdbc.query("SELECT * FROM author_profile WHERE user_id=:id",p("id",user),(r,n)->new Profile(r.getLong("revision"),r.getString("nickname"),r.getObject("major_id",Long.class),r.getString("current_stage"),r.getString("bio"))).stream().findFirst().orElse(new Profile(0,"城院同学",null,"UNSET",""));}
    public void profile(long user,Profile profile){jdbc.update("INSERT INTO author_profile(user_id,revision,nickname,major_id,current_stage,bio) VALUES(:user,:rev,:name,:major,:stage,:bio) ON DUPLICATE KEY UPDATE revision=:rev,nickname=:name,major_id=:major,current_stage=:stage,bio=:bio",p("user",user).addValue("rev",profile.revision()).addValue("name",profile.nickname()).addValue("major",profile.majorId()).addValue("stage",profile.currentStage()).addValue("bio",profile.bio()));}
    public void categories(List<Long> ids,String kind){
        if(ids.isEmpty())return;
        long available=jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE kind=:kind AND enabled=TRUE",p("kind",kind),Long.class);
        if(ids.size()>available)throw ApiException.invalid();
        long matched=jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE id IN (:ids) AND kind=:kind AND enabled=TRUE",p("kind",kind).addValue("ids",ids),Long.class);if(matched!=ids.size())throw ApiException.invalid();
    }
    public Long created(long user,String request){return jdbc.query("SELECT article_id FROM author_create_request WHERE user_id=:user AND request_id=:request",p("user",user).addValue("request",request),(r,n)->r.getLong(1)).stream().findFirst().orElse(null);}
    public void create(long id,long user,String request,Instant now){
        var params=p("id",id).addValue("user",user).addValue("request",request).addValue("now",time(now));
        jdbc.update("INSERT INTO article(id,state,owner_user_id,count_origin,created_at,updated_at) VALUES(:id,'DRAFT',:user,'NONE',:now,:now)",params);
        jdbc.update("INSERT INTO article_draft(article_id,body) VALUES(:id,'')",params);
        jdbc.update("INSERT INTO author_create_request(user_id,request_id,article_id) VALUES(:user,:request,:id)",params);
    }
    public Article owned(long user,long id,boolean lock){return jdbc.query("SELECT a.id,a.state,d.revision,a.first_published_at FROM article a JOIN article_draft d ON d.article_id=a.id WHERE a.id=:id AND a.owner_user_id=:user"+(lock?" FOR UPDATE":""),p("id",id).addValue("user",user),(r,n)->new Article(r.getLong("id"),r.getString("state"),r.getLong("revision"),r.getTimestamp("first_published_at")==null?null:r.getTimestamp("first_published_at").toInstant())).stream().findFirst().orElseThrow(ApiException::notFound);}
    public OwnedPage list(long user,int page,int size){
        var params=p("user",user).addValue("size",size).addValue("offset",((long)page-1)*size);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM article WHERE owner_user_id=:user",params,Long.class);
        var rows=jdbc.query("SELECT a.id,a.state,d.revision,d.title,a.updated_at FROM article a JOIN article_draft d ON d.article_id=a.id WHERE a.owner_user_id=:user ORDER BY a.updated_at DESC,a.id DESC LIMIT :size OFFSET :offset",params,(r,n)->new OwnedArticle(r.getLong("id"),r.getString("state"),r.getLong("revision"),r.getString("title"),r.getTimestamp("updated_at").toInstant()));return new OwnedPage(rows,total,page,size);
    }
    public Content content(long id){return jdbc.query("SELECT * FROM article_draft WHERE article_id=:id",p("id",id),(r,n)->new Content(r.getString("title"),r.getString("summary"),r.getString("body"),tags(id),r.getString("experience_stage"),r.getString("source_type"),r.getString("source_note"),links(id))).getFirst();}
    private Tags tags(long id){var groups=new HashMap<String,List<Long>>();jdbc.query("SELECT c.kind,c.id FROM article_draft_category d JOIN category c ON c.id=d.category_id WHERE d.article_id=:id ORDER BY c.id",p("id",id),r->{groups.computeIfAbsent(r.getString(1),k->new ArrayList<>()).add(r.getLong(2));});return new Tags(groups.getOrDefault("JOB",List.of()),groups.getOrDefault("MAJOR",List.of()),groups.getOrDefault("COMPANY",List.of()),groups.getOrDefault("TOPIC",List.of()));}
    private List<SourceLink> links(long id){return jdbc.query("SELECT label,url FROM article_draft_source_link WHERE article_id=:id ORDER BY link_order",p("id",id),(r,n)->new SourceLink(r.getString(1),r.getString(2)));}
    public List<String> referenced(long id){return jdbc.query("SELECT image_id FROM article_draft_image WHERE article_id=:id ORDER BY image_id",p("id",id),(r,n)->r.getString(1));}
    public void imageReferences(long article,List<String> ids){for(String id:ids)if(image(id).filter(m->m.articleId()==article).isEmpty())throw ApiException.notFound();}
    public List<Image> images(long article){return jdbc.query("SELECT m.*,a.owner_user_id,a.state,FALSE visible FROM media_image m JOIN article a ON a.id=m.article_id WHERE m.article_id=:id ORDER BY m.created_at,m.id",p("id",article),(r,n)->image(r).view());}
    public Optional<ImageRow> image(String id){return jdbc.query("SELECT m.*,a.owner_user_id,a.state,(a.state='PUBLISHED' AND EXISTS(SELECT 1 FROM article_version_image r WHERE r.version_id=a.public_version_id AND r.image_id=m.id)) visible FROM media_image m JOIN article a ON a.id=m.article_id WHERE m.id=:id",p("id",id),(r,n)->image(r)).stream().findFirst();}
    private static ImageRow image(ResultSet r)throws SQLException{return new ImageRow(r.getString("id"),r.getLong("article_id"),r.getObject("owner_user_id",Long.class),r.getString("state"),r.getString("content_digest"),r.getString("mime_type"),r.getLong("size_bytes"),r.getInt("width"),r.getInt("height"),r.getBoolean("visible"));}
    public Optional<ImageRow> uploaded(long article,String request){return jdbc.query("SELECT m.*,a.owner_user_id,a.state,FALSE visible FROM media_image m JOIN article a ON a.id=m.article_id WHERE m.article_id=:id AND m.request_id=:request",p("id",article).addValue("request",request),(r,n)->image(r)).stream().findFirst();}
    public void image(long article,String request,String id,MediaStore.Validated file,Instant now){jdbc.update("INSERT INTO media_image(id,article_id,request_id,content_digest,mime_type,size_bytes,width,height,created_at) VALUES(:id,:article,:request,:digest,:mime,:size,:width,:height,:now)",p("id",id).addValue("article",article).addValue("request",request).addValue("digest",file.digest()).addValue("mime",file.mime()).addValue("size",file.bytes().length).addValue("width",file.width()).addValue("height",file.height()).addValue("now",time(now)));}
    public void save(long id,Content c,List<String> images,Instant now){
        var params=p("id",id).addValue("title",c.title()).addValue("summary",c.summary()).addValue("body",c.body()).addValue("stage",c.experienceStage()).addValue("source",c.sourceType()).addValue("note",c.sourceNote());
        jdbc.update("UPDATE article_draft SET title=:title,summary=:summary,body=:body,experience_stage=:stage,source_type=:source,source_note=:note WHERE article_id=:id",params);
        jdbc.update("DELETE FROM article_draft_category WHERE article_id=:id",params);
        for(var ids:List.of(c.tags().jobIds(),c.tags().majorIds(),c.tags().companyIds(),c.tags().topicIds()))for(long category:ids)jdbc.update("INSERT INTO article_draft_category VALUES(:id,:category)",p("id",id).addValue("category",category));
        jdbc.update("DELETE FROM article_draft_source_link WHERE article_id=:id",params);int order=0;
        for(SourceLink link:c.sourceLinks())jdbc.update("INSERT INTO article_draft_source_link VALUES(:id,:ord,:label,:url)",p("id",id).addValue("ord",order++).addValue("label",link.label()).addValue("url",link.url()));
        jdbc.update("DELETE FROM article_draft_image WHERE article_id=:id",params);for(String image:images)jdbc.update("INSERT INTO article_draft_image VALUES(:id,:image)",p("id",id).addValue("image",image));bump(id,now);
    }
    public void bump(long id,Instant now){jdbc.update("UPDATE article_draft SET revision=revision+1 WHERE article_id=:id",p("id",id));jdbc.update("UPDATE article SET updated_at=:now WHERE id=:id",p("id",id).addValue("now",time(now)));}
    public Published published(long id,String request,long expected){
        return jdbc.query("SELECT r.*,v.published_at,v.updated_at FROM article_publish_request r JOIN article_version v ON v.id=r.version_id WHERE r.article_id=:id AND r.request_id=:request",p("id",id).addValue("request",request),(r,n)->{if(r.getLong("expected_revision")!=expected)throw WritingService.conflict("REQUEST_KEY_REUSED");return new Published(id,r.getLong("version_id"),r.getLong("result_revision"),r.getTimestamp("published_at").toInstant(),r.getTimestamp("updated_at").toInstant());}).stream().findFirst().orElse(null);
    }
    public Published publish(Article a,Content c,String summary,String request,Instant now){
        long version=allocate("version");Instant first=a.firstPublishedAt()==null?now:a.firstPublishedAt();
        long number=jdbc.queryForObject("SELECT COALESCE(MAX(version_number),0)+1 FROM article_version WHERE article_id=:id",p("id",a.id()),Long.class);
        var params=p("id",a.id()).addValue("version",version).addValue("number",number).addValue("title",c.title()).addValue("summary",summary).addValue("body",c.body()).addValue("stage",c.experienceStage()).addValue("source",c.sourceType()).addValue("note",c.sourceNote()).addValue("first",time(first)).addValue("now",time(now));
        jdbc.update("INSERT INTO article_version(id,article_id,version_number,title,summary,body,experience_stage,source_type,source_note,published_at,created_at,updated_at) VALUES(:version,:id,:number,:title,:summary,:body,:stage,:source,:note,:first,:now,:now)",params);
        jdbc.update("INSERT INTO article_version_category SELECT :version,category_id FROM article_draft_category WHERE article_id=:id",params);
        for(SourceLink link:c.sourceLinks())jdbc.update("INSERT INTO article_source_link(id,version_id,label,url) VALUES(:link,:version,:label,:url)",p("link",allocate("source")).addValue("version",version).addValue("label",link.label()).addValue("url",link.url()));
        jdbc.update("INSERT INTO article_version_image SELECT article_id,:version,image_id FROM article_draft_image WHERE article_id=:id",params);
        jdbc.update("UPDATE article SET public_version_id=:version,state='PUBLISHED',first_published_at=:first WHERE id=:id",params);bump(a.id(),now);
        jdbc.update("INSERT INTO article_publish_request VALUES(:id,:request,:expected,:result,:version)",params.addValue("request",request).addValue("expected",a.revision()).addValue("result",a.revision()+1));
        return new Published(a.id(),version,a.revision()+1,first,now);
    }
    public void state(long id,String state,Instant now){jdbc.update("UPDATE article SET state=:state WHERE id=:id",p("id",id).addValue("state",state));bump(id,now);}
}
