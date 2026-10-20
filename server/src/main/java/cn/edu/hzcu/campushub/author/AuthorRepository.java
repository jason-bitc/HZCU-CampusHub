package cn.edu.hzcu.campushub.author;

import static cn.edu.hzcu.campushub.author.AuthorModels.*;
import cn.edu.hzcu.campushub.article.ArticleModels.Category;
import cn.edu.hzcu.campushub.common.ApiException;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class AuthorRepository {
    private static final String PUBLIC="a.state='PUBLISHED' AND v.published_at IS NOT NULL";
    private static final String VISIBLE="u.account_state='ACTIVE' AND u.role='USER' AND cert.state='VALID'";
    private static final String COUNT="(SELECT COUNT(*) FROM article a JOIN article_version v ON v.id=a.public_version_id AND v.article_id=a.id WHERE a.owner_user_id=u.id AND "+PUBLIC+")";
    private static final String USER=" FROM platform_user u JOIN campus_certification cert ON cert.user_id=u.id LEFT JOIN author_profile p ON p.user_id=u.id ";
    private final NamedParameterJdbcTemplate jdbc;
    public AuthorRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public Entry entry(long article){
        return jdbc.query("SELECT u.id,COALESCE(p.nickname,'城院同学') nickname FROM article a JOIN article_version v ON v.id=a.public_version_id AND v.article_id=a.id JOIN platform_user u ON u.id=a.owner_user_id JOIN campus_certification cert ON cert.user_id=u.id LEFT JOIN author_profile p ON p.user_id=u.id WHERE a.id=:id AND "+PUBLIC+" AND "+VISIBLE,Map.of("id",article),(r,n)->new Entry(r.getLong("id"),r.getString("nickname"))).stream().findFirst().orElseThrow(ApiException::notFound);
    }
    public Home home(long author){
        return jdbc.query("SELECT u.id,COALESCE(p.nickname,'城院同学') nickname,p.major_id,c.name major_name,COALESCE(p.current_stage,'UNSET') stage,COALESCE(p.bio,'') bio,"+COUNT+" public_count"+USER+" LEFT JOIN category c ON c.id=p.major_id WHERE u.id=:id AND "+VISIBLE+" AND "+COUNT+">0",Map.of("id",author),(r,n)->new Home(r.getLong("id"),r.getString("nickname"),r.getObject("major_id")==null?null:new Category(r.getLong("major_id"),r.getString("major_name")),r.getString("stage"),r.getString("bio"),"VALID",r.getLong("public_count"))).stream().findFirst().orElseThrow(ApiException::notFound);
    }
    public Contact contact(long user){return jdbc.query("SELECT revision,enabled,contact_type,contact_value FROM author_contact_setting WHERE user_id=:id",Map.of("id",user),(r,n)->new Contact(r.getLong(1),r.getBoolean(2),r.getString(3),r.getString(4))).stream().findFirst().orElse(new Contact(0,false,null,null));}
    public void contact(long user,Contact value){jdbc.update("INSERT INTO author_contact_setting(user_id,revision,enabled,contact_type,contact_value) VALUES(:user,:revision,:enabled,:type,:value) ON DUPLICATE KEY UPDATE revision=:revision,enabled=:enabled,contact_type=:type,contact_value=:value",new MapSqlParameterSource("user",user).addValue("revision",value.revision()).addValue("enabled",value.enabled()).addValue("type",value.type()).addValue("value",value.value()));}
    public Map<String,Object> publicContact(long author){
        // One fresh SQL read gates the target and reads the current switch; no publication snapshot.
        return jdbc.query("SELECT COALESCE(s.enabled,FALSE) enabled,s.contact_type,s.contact_value"+USER+" LEFT JOIN author_contact_setting s ON s.user_id=u.id WHERE u.id=:id AND "+VISIBLE+" AND "+COUNT+">0",Map.of("id",author),(r,n)->r.getBoolean("enabled")?Map.<String,Object>of("enabled",true,"type",r.getString("contact_type"),"value",r.getString("contact_value")):Map.<String,Object>of("enabled",false)).stream().findFirst().orElseThrow(ApiException::notFound);
    }
}
