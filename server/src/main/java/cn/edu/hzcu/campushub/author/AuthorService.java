package cn.edu.hzcu.campushub.author;

import static cn.edu.hzcu.campushub.author.AuthorModels.*;
import cn.edu.hzcu.campushub.article.*;
import cn.edu.hzcu.campushub.authoring.*;
import cn.edu.hzcu.campushub.audit.BusinessAudit;
import cn.edu.hzcu.campushub.audit.BusinessAudit.*;
import cn.edu.hzcu.campushub.common.RequestTrace;
import static cn.edu.hzcu.campushub.common.RequestTrace.stage;
import static cn.edu.hzcu.campushub.common.RequestTrace.Stage.*;
import cn.edu.hzcu.campushub.coin.CoinRepository;
import cn.edu.hzcu.campushub.coin.HonorRules;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthorService {
    private final AuthorRepository authors;
    private final ArticleRepository articles;
    private final WritingRepository writing;
    private final BusinessAudit audit;
    private final CoinRepository coins;
    private final HonorRules honors;
    private final TransactionTemplate read,write,contactRead;

    public AuthorService(AuthorRepository authors, ArticleRepository articles, WritingRepository writing,
            BusinessAudit audit, PlatformTransactionManager manager, CoinRepository coins, HonorRules honors) {
        this.authors = authors;
        this.articles = articles;
        this.writing = writing;
        this.audit = audit;
        this.coins = coins;
        this.honors = honors;
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        contactRead = new TransactionTemplate(manager);
        contactRead.setReadOnly(true);
        contactRead.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write = new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public Entry entry(long id) {
        return read.execute(tx -> {
            stage(TARGET);
            var entry = authors.entry(id);
            RequestTrace.article(id);
            RequestTrace.author(entry.authorId());
            var honor = honorFor(entry.authorId());
            return honor == null ? entry : new Entry(entry.authorId(), entry.nickname(), honor);
        });
    }
    public Home home(long id) {
        return read.execute(tx -> {
            stage(TARGET);
            var home = authors.home(id);
            RequestTrace.author(home.authorId());
            var honor = honorFor(home.authorId());
            return honor == null ? home : new Home(home.authorId(), home.nickname(), home.major(),
                    home.currentStage(), home.bio(), home.campusCertification(), home.publicArticleCount(), honor);
        });
    }

    private HonorRules.Honor honorFor(long authorId) {
        if (!honors.hasEnabledRules()) return null;
        stage(READ);
        return honors.current(coins.contribution(authorId));
    }
    public ArticleModels.Page articles(long id,int page,int size){return read.execute(tx->{stage(TARGET);authors.home(id);RequestTrace.author(id);stage(READ);return articles.byAuthor(id,page,size);});}
    public Contact ownContact(long user){return contactRead.execute(tx->{stage(READ);return authors.contact(user);});}
    public Map<String,Object> contact(long author){return contactRead.execute(tx->{stage(TARGET);var contact=authors.publicContact(author);RequestTrace.author(author);return contact;});}
    public Contact updateContact(long user,Contact incoming){return RequestTrace.write(write,tx->{
        stage(TARGET);writing.lockUser(user);Contact previous=authors.contact(user);
        cn.edu.hzcu.campushub.common.RequestTrace.revisions(incoming.revision(),previous.revision());
        if(previous.revision()!=incoming.revision())throw WritingService.conflict("REVISION_CONFLICT");
        stage(PERSIST);Contact next=new Contact(previous.revision()+1,incoming.enabled(),incoming.type(),incoming.value());authors.contact(user,next);
        audit.changed(user,Action.CONTACT_UPDATE,ObjectType.CONTACT,user,previous.revision(),next.revision(),previous.enabled()?State.ENABLED:State.DISABLED,next.enabled()?State.ENABLED:State.DISABLED);
        return next;
    });}
}
