package cn.edu.hzcu.campushub.interaction;

import static cn.edu.hzcu.campushub.interaction.InteractionModels.*;
import static cn.edu.hzcu.campushub.interaction.InteractionRepository.Kind;
import cn.edu.hzcu.campushub.article.ArticleRepository;
import cn.edu.hzcu.campushub.audit.BusinessAudit;
import cn.edu.hzcu.campushub.audit.BusinessAudit.*;
import cn.edu.hzcu.campushub.common.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InteractionService {
    private final InteractionRepository interactions;
    private final ArticleRepository articles;
    private final BusinessAudit audit;
    private final Clock clock;
    private final TransactionTemplate read,write;
    public InteractionService(InteractionRepository interactions,ArticleRepository articles,BusinessAudit audit,Clock clock,PlatformTransactionManager manager){
        this.interactions=interactions;this.articles=articles;this.audit=audit;this.clock=clock;
        read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        write=new TransactionTemplate(manager);write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    private void set(Kind kind,long user,long article,boolean active){
        // The article lock also serializes withdrawal and all writers for this article.
        boolean visible=interactions.article(article,true);var previous=interactions.relation(kind,user,article);
        if(!visible&&(kind==Kind.LIKE||active||previous.revision()==0))throw ApiException.notFound();
        Action action=kind==Kind.LIKE?(active?Action.LIKE_SET:Action.LIKE_CANCEL):(active?Action.FAVORITE_SET:Action.FAVORITE_CANCEL);
        ObjectType object=kind==Kind.LIKE?ObjectType.LIKE:ObjectType.FAVORITE;
        RequestTrace.stage(RequestTrace.Stage.RECEIPT);RequestTrace.revisions(previous.revision(),previous.revision());
        if(previous.active()==active){audit.unchanged(action,object);return;}
        RequestTrace.stage(RequestTrace.Stage.PERSIST);var next=interactions.set(kind,user,article,previous,active,clock.instant());RequestTrace.revisions(previous.revision(),next.revision());
        audit.changed(user,action,object,article,previous.revision(),next.revision(),previous.active()?BusinessAudit.State.ENABLED:BusinessAudit.State.DISABLED,active?BusinessAudit.State.ENABLED:BusinessAudit.State.DISABLED);
    }
    public Like like(long user,long article,boolean active){return RequestTrace.write(write,tx->{set(Kind.LIKE,user,article,active);return new Like(article,active,interactions.likes(article));});}
    public Favorite favorite(long user,long article,boolean active){return RequestTrace.write(write,tx->{set(Kind.FAVORITE,user,article,active);return new Favorite(article,active);});}
    public InteractionModels.State state(long user,long article){return read.execute(tx->{if(!interactions.article(article,false))throw ApiException.notFound();RequestTrace.stage(RequestTrace.Stage.READ);return new InteractionModels.State(article,interactions.relation(Kind.LIKE,user,article).active(),interactions.relation(Kind.FAVORITE,user,article).active());});}
    public Favorites favorites(long user,int page,int size){return read.execute(tx->{
        RequestTrace.stage(RequestTrace.Stage.READ);var ids=interactions.favorites(user,page,size);var summaries=articles.summaries(ids);var items=new ArrayList<Map<String,Object>>();
        for(long id:ids){var item=new LinkedHashMap<String,Object>();item.put("articleId",id);item.put("available",summaries.containsKey(id));if(summaries.containsKey(id))item.put("article",summaries.get(id));items.add(item);}
        return new Favorites(items,interactions.favoriteCount(user),page,size);
    });}
}
