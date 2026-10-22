package cn.edu.hzcu.campushub.coin;

import static cn.edu.hzcu.campushub.coin.CoinModels.*;
import cn.edu.hzcu.campushub.audit.BusinessAudit;
import cn.edu.hzcu.campushub.audit.BusinessAudit.*;
import cn.edu.hzcu.campushub.common.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CoinService {
    private final CoinRepository db;
    private final CoinRules rules;
    private final BusinessAudit audit;
    private final Clock clock;
    private final TransactionTemplate read,write;
    public CoinService(CoinRepository db,CoinRules rules,BusinessAudit audit,Clock clock,PlatformTransactionManager manager){
        this.db=db;this.rules=rules;this.audit=audit;this.clock=clock;
        read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        write=new TransactionTemplate(manager);write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    private LocalDate day(Instant now){return now.atZone(CoinRules.ZONE).toLocalDate();}
    private static ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
    private Receipt replay(long user,Kind action,String key,Long article,String reading,Integer amount){
        var old=db.receipt(user,action,key).orElse(null);if(old==null)return null;var r=old.receipt();
        if((action==Kind.ARTICLE_COIN&&(!Objects.equals(article,r.articleId())||-r.delta()!=amount))||(action==Kind.READING_REWARD&&!Objects.equals(reading,r.sessionId())))throw conflict("REQUEST_KEY_REUSED","该请求编号已用于其他内容");
        audit.unchanged(Action.valueOf(action.name()),ObjectType.WALLET);return r;
    }
    public Balance balance(long user){return read.execute(tx->{RequestTrace.stage(RequestTrace.Stage.READ);return new Balance(db.wallet(user,false).balance(),db.contribution(user));});}
    public Ledger ledger(long user,int page,int size,Long snapshot){return read.execute(tx->{RequestTrace.stage(RequestTrace.Stage.READ);return db.ledger(user,page,size,snapshot);});}
    public Rewards rewards(long user){return read.execute(tx->{
        RequestTrace.stage(RequestTrace.Stage.READ);Instant now=clock.instant();LocalDate date=day(now);boolean login=db.claimed(user,Kind.LOGIN_REWARD,date),reading=db.claimed(user,Kind.READING_REWARD,date);
        var sessions=db.readings(user,date);
        long amount=db.rewarded(user,date);return new Rewards(date,now,CoinModels.rules(rules),new Task(db.login(user,date)&&!login,login),new Task(!reading&&db.ready(user,date,now),reading),amount,Math.max(0,rules.dailyMaximum()-amount),sessions);
    });}
    public Reading start(long user,long article){return RequestTrace.write(write,tx->{
        // Article precedes wallet in every flow that needs both; never lock an author wallet.
        db.target(article,true);Instant now=clock.instant();LocalDate date=day(now);var old=db.reading(user,article,date);if(old.isPresent()){audit.unchanged(Action.READING_START,ObjectType.READING);return old.get();}
        RequestTrace.stage(RequestTrace.Stage.PERSIST);var reading=db.start(user,article,date,now,rules.readingSeconds());audit.changed(user,Action.READING_START,ObjectType.READING,article,0,1,null,null);return reading;
    });}
    private Receipt commit(long user,Kind action,String key,Long article,Long author,String session,long delta,Wallet wallet,Instant now){
        var result=db.append(user,action,key,day(now),article,author,session,delta,wallet,now,rules);
        audit.changed(user,Action.valueOf(action.name()),ObjectType.WALLET,user,wallet.revision(),wallet.revision()+1,null,null);return result;
    }
    public Receipt login(long user,String key){return RequestTrace.write(write,tx->{
        var wallet=db.wallet(user,true);var prior=replay(user,Kind.LOGIN_REWARD,key,null,null,null);if(prior!=null)return prior;
        Instant now=clock.instant();LocalDate date=day(now);if(db.claimed(user,Kind.LOGIN_REWARD,date))throw conflict("REWARD_ALREADY_CLAIMED","今日该奖励已领取");
        if(!db.login(user,date))throw conflict("LOGIN_REQUIRED_TODAY","请先在今日重新登录");return commit(user,Kind.LOGIN_REWARD,key,null,null,null,rules.loginReward(),wallet,now);
    });}
    public Receipt reading(long user,String session,String key){return RequestTrace.write(write,tx->{
        var prior=replay(user,Kind.READING_REWARD,key,null,session,null);if(prior!=null)return prior;
        var reading=db.reading(user,session);
        try{db.target(reading.articleId(),true);}catch(ApiException unavailable){
            // A concurrent successful claim can commit before withdrawal wins the article lock.
            prior=replay(user,Kind.READING_REWARD,key,null,session,null);if(prior!=null)return prior;throw unavailable;
        }
        var wallet=db.wallet(user,true);
        prior=replay(user,Kind.READING_REWARD,key,null,session,null);if(prior!=null)return prior;
        Instant now=clock.instant();if(!reading.businessDate().equals(day(now)))throw conflict("READING_DAY_EXPIRED","请重新开始今日阅读计时");
        if(db.claimed(user,Kind.READING_REWARD,day(now)))throw conflict("REWARD_ALREADY_CLAIMED","今日该奖励已领取");
        if(now.isBefore(reading.claimableAt()))throw conflict("READING_NOT_READY","阅读计时尚未达到领取条件");
        return commit(user,Kind.READING_REWARD,key,reading.articleId(),null,session,rules.readingReward(),wallet,now);
    });}
    public CoinState state(long user,long article){return read.execute(tx->{RequestTrace.stage(RequestTrace.Stage.READ);var target=db.target(article,false);long spent=db.spent(user,article);String reason=target.author()==null?"NO_REAL_AUTHOR":target.author()==user?"SELF_ARTICLE":spent>=rules.articleLimit()?"ARTICLE_COIN_LIMIT":"ELIGIBLE";return new CoinState(article,spent,(int)Math.max(0,rules.articleLimit()-spent),reason.equals("ELIGIBLE"),reason);});}
    public Receipt spend(long user,long article,int amount,String key){return RequestTrace.write(write,tx->{
        var prior=replay(user,Kind.ARTICLE_COIN,key,article,null,amount);if(prior!=null)return prior;
        Target target;
        try{target=db.target(article,true);}catch(ApiException unavailable){
            prior=replay(user,Kind.ARTICLE_COIN,key,article,null,amount);if(prior!=null)return prior;throw unavailable;
        }
        var wallet=db.wallet(user,true);
        prior=replay(user,Kind.ARTICLE_COIN,key,article,null,amount);if(prior!=null)return prior;
        if(target.author()==null)throw conflict("NO_REAL_AUTHOR","该文章暂无关联真实作者，不能投币");
        if(target.author()==user)throw conflict("SELF_ARTICLE","不能向本人文章投币");
        if(amount<1||amount>rules.articleLimit()||db.spent(user,article)+amount>rules.articleLimit())throw conflict("ARTICLE_COIN_LIMIT","超过本人向该文章投币的累计额度");
        if(wallet.balance()<amount)throw conflict("INSUFFICIENT_COINS","可用余额不足");
        return commit(user,Kind.ARTICLE_COIN,key,article,target.author(),null,-amount,wallet,clock.instant());
    });}
}
