package cn.edu.hzcu.campushub.comment;

import static cn.edu.hzcu.campushub.comment.CommentModels.*;
import cn.edu.hzcu.campushub.authoring.MediaStore;
import cn.edu.hzcu.campushub.authoring.WritingModels.Image;
import cn.edu.hzcu.campushub.authoring.WritingService;
import cn.edu.hzcu.campushub.common.ApiException;
import cn.edu.hzcu.campushub.common.RequestTrace;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class CommentRepository {
    public static final int SQL_BATCH=100;
    private static final String PUBLIC="a.state='PUBLISHED' AND EXISTS(SELECT 1 FROM article_version v WHERE v.id=a.public_version_id AND v.article_id=a.id AND v.published_at IS NOT NULL)";
    private static final String COLUMNS="c.*,(SELECT COUNT(*) FROM discussion_comment r WHERE r.root_comment_id=c.id) reply_count";
    private static final String PICTURES="SELECT m.*,c.state comment_state,("+PUBLIC+") article_public FROM comment_image m JOIN article a ON a.id=m.article_id LEFT JOIN discussion_comment c ON c.id=m.comment_id ";
    private final NamedParameterJdbcTemplate jdbc;
    public CommentRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    static MapSqlParameterSource p(String key,Object value){return new MapSqlParameterSource(key,value);}
    public boolean article(long id,boolean lock){
        RequestTrace.stage(RequestTrace.Stage.TARGET);var found=jdbc.query("SELECT ("+PUBLIC+") visible FROM article a WHERE a.id=:id"+(lock?" FOR UPDATE":""),p("id",id),(r,n)->r.getBoolean(1)).stream().findFirst();
        if(found.isEmpty())throw ApiException.notFound();RequestTrace.article(id);return found.get();
    }
    public void publicArticle(long id,boolean lock){if(!article(id,lock))throw ApiException.notFound();}
    public Row row(long id){RequestTrace.stage(RequestTrace.Stage.TARGET);Row row=jdbc.query("SELECT "+COLUMNS+" FROM discussion_comment c WHERE c.id=:id",p("id",id),(r,n)->row(r)).stream().findFirst().orElseThrow(ApiException::notFound);RequestTrace.article(row.article());RequestTrace.comment(row.id());return row;}
    private static Row row(ResultSet r)throws SQLException{return new Row(r.getLong("id"),r.getLong("article_id"),r.getLong("user_id"),r.getObject("root_comment_id",Long.class),r.getObject("reply_to_comment_id",Long.class),r.getString("state"),r.getLong("revision"),r.getString("body"),r.getTimestamp("created_at").toInstant(),r.getLong("reply_count"));}
    public List<Row> page(long article,Long root,int page,int size){
        return jdbc.query("SELECT "+COLUMNS+" FROM discussion_comment c WHERE c.article_id=:article AND "+(root==null?"c.root_comment_id IS NULL":"c.root_comment_id=:root")+" ORDER BY c.created_at "+(root==null?"DESC":"ASC")+",c.id "+(root==null?"DESC":"ASC")+" LIMIT :size OFFSET :offset",p("article",article).addValue("root",root).addValue("size",size).addValue("offset",((long)page-1)*size),(r,n)->row(r));
    }
    public long count(long article,Long root){return jdbc.queryForObject("SELECT COUNT(*) FROM discussion_comment WHERE article_id=:article AND "+(root==null?"root_comment_id IS NULL":"root_comment_id=:root"),p("article",article).addValue("root",root),Long.class);}
    public Map<Long,Row> rows(Collection<Long> ids){
        var result=new HashMap<Long,Row>();for(var chunk:chunks(ids))for(var row:jdbc.query("SELECT "+COLUMNS+" FROM discussion_comment c WHERE c.id IN (:ids)",p("ids",chunk),(r,n)->row(r)))result.put(row.id(),row);return result;
    }
    public Map<Long,Map<String,Object>> commenters(Collection<Long> ids){
        var result=new HashMap<Long,Map<String,Object>>();
        for(var chunk:chunks(ids))jdbc.query("SELECT u.id,COALESCE(p.nickname,'城院同学') nickname,EXISTS(SELECT 1 FROM article a WHERE a.owner_user_id=u.id AND "+PUBLIC+") author_visible FROM platform_user u JOIN campus_certification cert ON cert.user_id=u.id LEFT JOIN author_profile p ON p.user_id=u.id WHERE u.id IN (:ids) AND u.account_state='ACTIVE' AND u.role='USER' AND cert.state='VALID'",p("ids",chunk),r->{
            var profile=new LinkedHashMap<String,Object>();profile.put("nickname",r.getString("nickname"));if(r.getBoolean("author_visible"))profile.put("authorId",r.getLong("id"));result.put(r.getLong("id"),profile);
        });return result;
    }
    public Map<Long,List<Image>> attached(Collection<Long> comments){
        var result=new HashMap<Long,List<Image>>();for(var chunk:chunks(comments))jdbc.query(PICTURES+"WHERE m.comment_id IN (:ids) ORDER BY m.comment_id,m.image_order",p("ids",chunk),r->{var image=picture(r);result.computeIfAbsent(image.comment(),ignored->new ArrayList<>()).add(image.view());});return result;
    }
    public Optional<Previous> previous(long user,String request){return jdbc.query("SELECT comment_id,content_digest FROM comment_create_receipt WHERE user_id=:user AND request_id=:request",p("user",user).addValue("request",request),(r,n)->new Previous(r.getLong(1),r.getString(2))).stream().findFirst();}
    public void create(long id,long article,long user,Long root,Input input,String digest,Instant now){
        var params=p("id",id).addValue("article",article).addValue("user",user).addValue("root",root).addValue("reply",input.replyToCommentId()).addValue("body",input.body()).addValue("now",Timestamp.from(now)).addValue("request",input.requestId()).addValue("digest",digest);
        jdbc.update("INSERT INTO discussion_comment(id,article_id,user_id,root_comment_id,reply_to_comment_id,body,created_at) VALUES(:id,:article,:user,:root,:reply,:body,:now)",params);
        jdbc.update("INSERT INTO comment_create_receipt VALUES(:user,:request,:article,:id,:digest)",params);
    }
    public void delete(long id){jdbc.update("UPDATE discussion_comment SET state='DELETED',revision=revision+1 WHERE id=:id",p("id",id));}
    private static Picture picture(ResultSet r)throws SQLException{return new Picture(r.getString("id"),r.getLong("article_id"),r.getLong("user_id"),r.getString("content_digest"),r.getString("mime_type"),r.getLong("size_bytes"),r.getInt("width"),r.getInt("height"),r.getObject("comment_id",Long.class),r.getString("comment_state"),r.getBoolean("article_public"));}
    public Optional<Picture> picture(String id){return jdbc.query(PICTURES+"WHERE m.id=:id",p("id",id),(r,n)->picture(r)).stream().findFirst();}
    public Optional<Picture> upload(long user,String request){return jdbc.query(PICTURES+"WHERE m.user_id=:user AND m.request_id=:request",p("user",user).addValue("request",request),(r,n)->picture(r)).stream().findFirst();}
    public List<Picture> pictures(Collection<String> ids){var result=new ArrayList<Picture>();for(var chunk:chunks(ids))result.addAll(jdbc.query(PICTURES+"WHERE m.id IN (:ids)",p("ids",chunk),(r,n)->picture(r)));return result;}
    public void picture(long article,long user,String request,String id,MediaStore.Validated file,Instant now){
        jdbc.update("INSERT INTO comment_image(id,article_id,user_id,request_id,content_digest,mime_type,size_bytes,width,height,created_at) VALUES(:id,:article,:user,:request,:digest,:mime,:size,:width,:height,:now)",p("id",id).addValue("article",article).addValue("user",user).addValue("request",request).addValue("digest",file.digest()).addValue("mime",file.mime()).addValue("size",file.bytes().length).addValue("width",file.width()).addValue("height",file.height()).addValue("now",Timestamp.from(now)));
    }
    public void attach(long article,long user,long comment,List<String> ids){
        int order=0;
        for(var chunk:chunks(ids)){
            var params=new ArrayList<MapSqlParameterSource>();
            for(String id:chunk)params.add(p("id",id).addValue("article",article).addValue("user",user).addValue("comment",comment).addValue("order",order++));
            int[] updated=jdbc.batchUpdate("UPDATE comment_image SET comment_id=:comment,image_order=:order WHERE id=:id AND article_id=:article AND user_id=:user AND comment_id IS NULL",params.toArray(MapSqlParameterSource[]::new));
            for(int count:updated)if(count!=1&&count!=Statement.SUCCESS_NO_INFO)throw WritingService.conflict("IMAGE_ALREADY_USED");
        }
    }
    static <T> List<List<T>> chunks(Collection<T> ids){var all=List.copyOf(new LinkedHashSet<>(ids));var chunks=new ArrayList<List<T>>();for(int i=0;i<all.size();i+=SQL_BATCH)chunks.add(all.subList(i,Math.min(all.size(),i+SQL_BATCH)));return chunks;}
}
