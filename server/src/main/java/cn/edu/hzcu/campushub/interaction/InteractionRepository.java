package cn.edu.hzcu.campushub.interaction;

import static cn.edu.hzcu.campushub.interaction.InteractionModels.*;
import cn.edu.hzcu.campushub.common.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class InteractionRepository {
    public enum Kind { LIKE("article_like"),FAVORITE("article_favorite");final String table;Kind(String table){this.table=table;} }
    private final NamedParameterJdbcTemplate jdbc;
    public InteractionRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    private static MapSqlParameterSource pair(long user,long article){return new MapSqlParameterSource("user",user).addValue("article",article);}
    public boolean article(long id,boolean lock){
        RequestTrace.stage(RequestTrace.Stage.TARGET);
        var found=jdbc.query("SELECT (a.state='PUBLISHED' AND EXISTS(SELECT 1 FROM article_version v WHERE v.id=a.public_version_id AND v.article_id=a.id AND v.published_at IS NOT NULL)) visible FROM article a WHERE a.id=:id"+(lock?" FOR UPDATE":""),new MapSqlParameterSource("id",id),(r,n)->r.getBoolean(1)).stream().findFirst();
        if(found.isEmpty())throw ApiException.notFound();RequestTrace.article(id);return found.get();
    }
    public Relation relation(Kind kind,long user,long article){return jdbc.query("SELECT active,revision FROM "+kind.table+" WHERE user_id=:user AND article_id=:article",pair(user,article),(r,n)->new Relation(r.getBoolean(1),r.getLong(2))).stream().findFirst().orElse(new Relation(false,0));}
    public Relation set(Kind kind,long user,long article,Relation old,boolean active,Instant now){
        var p=pair(user,article).addValue("active",active).addValue("revision",old.revision()+1).addValue("now",Timestamp.from(now));
        if(old.revision()==0)jdbc.update("INSERT INTO "+kind.table+"(user_id,article_id,active,revision,activated_at,updated_at) VALUES(:user,:article,:active,:revision,:now,:now)",p);
        else jdbc.update("UPDATE "+kind.table+" SET active=:active,revision=:revision,updated_at=:now,activated_at=IF(:active,:now,activated_at) WHERE user_id=:user AND article_id=:article",p);
        return relation(kind,user,article);
    }
    public long likes(long article){return jdbc.queryForObject("SELECT COUNT(*) FROM article_like WHERE article_id=:article AND active=TRUE",new MapSqlParameterSource("article",article),Long.class);}
    public List<Long> favorites(long user,int page,int size){return jdbc.queryForList("SELECT article_id FROM article_favorite WHERE user_id=:user AND active=TRUE ORDER BY activated_at DESC,article_id DESC LIMIT :size OFFSET :offset",new MapSqlParameterSource("user",user).addValue("size",size).addValue("offset",((long)page-1)*size),Long.class);}
    public long favoriteCount(long user){return jdbc.queryForObject("SELECT COUNT(*) FROM article_favorite WHERE user_id=:user AND active=TRUE",new MapSqlParameterSource("user",user),Long.class);}
}
