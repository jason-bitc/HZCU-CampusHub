package cn.edu.hzcu.campushub.coin;

import static cn.edu.hzcu.campushub.coin.CoinModels.*;
import cn.edu.hzcu.campushub.common.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class CoinRepository {
    private final NamedParameterJdbcTemplate db;
    public CoinRepository(NamedParameterJdbcTemplate db){this.db=db;}
    private static MapSqlParameterSource p(long user){return new MapSqlParameterSource("user",user);}
    private static Timestamp time(Instant t){return Timestamp.from(t);}
    public Target target(long article,boolean lock){
        RequestTrace.stage(RequestTrace.Stage.TARGET);RequestTrace.article(article);
        return db.query("SELECT a.owner_user_id,(a.state='PUBLISHED' AND EXISTS(SELECT 1 FROM article_version v WHERE v.id=a.public_version_id AND v.article_id=a.id AND v.published_at IS NOT NULL)) visible FROM article a WHERE a.id=:article"+(lock?" FOR UPDATE":""),new MapSqlParameterSource("article",article),(r,n)->new Target(r.getObject(1,Long.class),r.getBoolean(2))).stream().findFirst().filter(Target::visible).orElseThrow(ApiException::notFound);
    }
    public Wallet wallet(long user,boolean lock){
        if(lock)db.update("INSERT INTO coin_wallet(user_id) VALUES(:user) ON DUPLICATE KEY UPDATE user_id=coin_wallet.user_id",p(user));
        return db.query("SELECT balance,revision FROM coin_wallet WHERE user_id=:user"+(lock?" FOR UPDATE":""),p(user),(r,n)->new Wallet(r.getLong(1),r.getLong(2))).stream().findFirst().orElse(new Wallet(0,0));
    }
    public boolean login(long user,LocalDate date){
        return db.queryForObject("SELECT COUNT(*) FROM auth_session WHERE user_id=:user AND created_at>=:start AND created_at<:end",p(user).addValue("start",time(date.atStartOfDay(CoinRules.ZONE).toInstant())).addValue("end",time(date.plusDays(1).atStartOfDay(CoinRules.ZONE).toInstant())),Long.class)>0;
    }
    public boolean claimed(long user,Kind action,LocalDate date){return db.queryForObject("SELECT COUNT(*) FROM coin_event WHERE user_id=:user AND action=:action AND reward_date=:date",p(user).addValue("action",action.name()).addValue("date",date),Long.class)>0;}
    public long rewarded(long user,LocalDate date){return db.queryForObject("SELECT COALESCE(SUM(delta),0) FROM coin_event WHERE user_id=:user AND reward_date=:date",p(user).addValue("date",date),Long.class);}
    public long spent(long user,long article){return db.queryForObject("SELECT COALESCE(SUM(-delta),0) FROM coin_event WHERE user_id=:user AND article_id=:article AND action='ARTICLE_COIN'",p(user).addValue("article",article),Long.class);}
    public long contribution(long user){return db.queryForObject("SELECT COALESCE(SUM(-delta),0) FROM coin_event WHERE author_id=:user AND action='ARTICLE_COIN' AND effective=TRUE",p(user),Long.class);}
    private static Reading reading(ResultSet r)throws SQLException {Instant start=r.getTimestamp("started_at").toInstant();int seconds=r.getInt("required_seconds");return new Reading(r.getString("id"),r.getLong("article_id"),r.getObject("business_date",LocalDate.class),start,start.plusSeconds(seconds),seconds);}
    public Reading reading(long user,String id){return db.query("SELECT * FROM coin_reading_session WHERE user_id=:user AND id=:id",p(user).addValue("id",id),(r,n)->reading(r)).stream().findFirst().orElseThrow(ApiException::notFound);}
    private static final String PUBLIC_READING=" FROM coin_reading_session s JOIN article a ON a.id=s.article_id JOIN article_version v ON v.id=a.public_version_id AND v.article_id=a.id WHERE s.user_id=:user AND s.business_date=:date AND a.state='PUBLISHED' AND v.published_at IS NOT NULL ";
    public List<Reading> readings(long user,LocalDate day){return db.query("SELECT s.*"+PUBLIC_READING+" ORDER BY s.started_at DESC,s.id DESC LIMIT 100",p(user).addValue("date",day),(r,n)->reading(r));}
    public boolean ready(long user,LocalDate day,Instant now){return db.queryForObject("SELECT COUNT(*)"+PUBLIC_READING+" AND TIMESTAMPADD(SECOND,s.required_seconds,s.started_at)<=:now",p(user).addValue("date",day).addValue("now",time(now)),Long.class)>0;}
    public Optional<Reading> reading(long user,long article,LocalDate day){return db.query("SELECT * FROM coin_reading_session WHERE user_id=:user AND article_id=:article AND business_date=:date",p(user).addValue("article",article).addValue("date",day),(r,n)->reading(r)).stream().findFirst();}
    public Reading start(long user,long article,LocalDate day,Instant now,int seconds){String id=UUID.randomUUID().toString();db.update("INSERT INTO coin_reading_session(id,user_id,article_id,business_date,started_at,required_seconds) VALUES(:id,:user,:article,:date,:now,:seconds)",p(user).addValue("id",id).addValue("article",article).addValue("date",day).addValue("now",time(now)).addValue("seconds",seconds));return reading(user,id);}
    public Optional<Event> receipt(long user,Kind action,String key){RequestTrace.stage(RequestTrace.Stage.RECEIPT);return db.query("SELECT * FROM coin_event WHERE user_id=:user AND action=:action AND request_id=:key",p(user).addValue("action",action.name()).addValue("key",key),(r,n)->new Event(r.getString("request_id"),receipt(r))).stream().findFirst();}
    private static Receipt receipt(ResultSet r)throws SQLException{
        var rules=new Rules(r.getString("rule_version"),CoinRules.ZONE.getId(),r.getInt("login_reward"),r.getInt("reading_reward"),r.getInt("reading_seconds"),r.getInt("article_limit"),r.getInt("login_reward")+r.getInt("reading_reward"),false);
        return new Receipt(r.getString("event_id"),Kind.valueOf(r.getString("action")),r.getObject("business_date",LocalDate.class),r.getObject("article_id",Long.class),r.getString("reading_session_id"),r.getLong("delta"),r.getLong("balance_after"),r.getLong("wallet_revision"),r.getTimestamp("occurred_at").toInstant(),rules);
    }
    public Receipt append(long user,Kind action,String key,LocalDate date,Long article,Long author,String reading,long delta,Wallet before,Instant now,CoinRules rules){
        RequestTrace.stage(RequestTrace.Stage.PERSIST);RequestTrace.revisions(before.revision(),before.revision()+1);
        long after=Math.addExact(before.balance(),delta);if(after<0)throw new IllegalStateException("Negative wallet");
        var q=p(user).addValue("event",UUID.randomUUID().toString()).addValue("action",action.name()).addValue("key",key).addValue("date",date).addValue("rewardDate",action==Kind.ARTICLE_COIN?null:date).addValue("article",article).addValue("author",author).addValue("reading",reading).addValue("delta",delta).addValue("balance",after).addValue("revision",before.revision()+1).addValue("now",time(now)).addValue("login",rules.loginReward()).addValue("reward",rules.readingReward()).addValue("seconds",rules.readingSeconds()).addValue("limit",rules.articleLimit()).addValue("version",rules.version());
        db.update("UPDATE coin_wallet SET balance=:balance,revision=:revision WHERE user_id=:user",q);
        db.update("INSERT INTO coin_event(event_id,user_id,action,request_id,business_date,reward_date,article_id,author_id,reading_session_id,delta,balance_after,wallet_revision,occurred_at,login_reward,reading_reward,reading_seconds,article_limit,rule_version) VALUES(:event,:user,:action,:key,:date,:rewardDate,:article,:author,:reading,:delta,:balance,:revision,:now,:login,:reward,:seconds,:limit,:version)",q);
        return receipt(user,action,key).orElseThrow().receipt();
    }
    public Ledger ledger(long user,int page,int size,Long snapshot){
        long high=db.queryForObject("SELECT COALESCE(MAX(sequence_id),0) FROM coin_event WHERE user_id=:user",p(user),Long.class);
        long cutoff=snapshot==null?high:Math.min(snapshot,high);var q=p(user).addValue("snapshot",cutoff).addValue("offset",((long)page-1)*size).addValue("size",size);
        long total=db.queryForObject("SELECT COUNT(*) FROM coin_event WHERE user_id=:user AND sequence_id<=:snapshot",q,Long.class);
        var rows=db.query("SELECT * FROM coin_event WHERE user_id=:user AND sequence_id<=:snapshot ORDER BY sequence_id DESC LIMIT :size OFFSET :offset",q,(r,n)->receipt(r));
        return new Ledger(rows,total,page,size,cutoff);
    }
}
