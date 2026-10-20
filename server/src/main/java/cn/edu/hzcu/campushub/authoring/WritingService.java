package cn.edu.hzcu.campushub.authoring;

import static cn.edu.hzcu.campushub.authoring.WritingModels.*;
import cn.edu.hzcu.campushub.auth.AuthService;
import cn.edu.hzcu.campushub.common.ApiException;
import cn.edu.hzcu.campushub.audit.BusinessAudit;
import cn.edu.hzcu.campushub.audit.BusinessAudit.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WritingService {
    private final WritingRepository repository;
    private final MarkdownReferences markdown;
    private final MediaStore files;
    private final AuthService auth;
    private final Clock clock;
    private final BusinessAudit audit;
    private final TransactionTemplate write,read;
    public WritingService(WritingRepository repository,MarkdownReferences markdown,MediaStore files,AuthService auth,Clock clock,PlatformTransactionManager manager,BusinessAudit audit){
        this.repository=repository;this.markdown=markdown;this.files=files;this.auth=auth;this.clock=clock;this.audit=audit;
        write=new TransactionTemplate(manager);write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public static ApiException conflict(String code){return new ApiException(HttpStatus.CONFLICT,code,"修订号或内容状态已变化，请重新读取后确认操作");}
    private static void revision(Article a,long expected){if(a.revision()!=expected)throw conflict("REVISION_CONFLICT");}
    private static void editable(Article a){if(Set.of("ADMIN_HIDDEN","DELETED").contains(a.state()))throw conflict("STATE_CONFLICT");}
    private void categories(Content c){repository.categories(c.tags().jobIds(),"JOB");repository.categories(c.tags().majorIds(),"MAJOR");repository.categories(c.tags().companyIds(),"COMPANY");repository.categories(c.tags().topicIds(),"TOPIC");}
    public Profile profile(long user){return read.execute(tx->repository.profile(user));}
    public Profile updateProfile(long user,Profile incoming){return write.execute(tx->{
        repository.lockUser(user);Profile previous=repository.profile(user);if(previous.revision()!=incoming.revision())throw conflict("REVISION_CONFLICT");
        if(incoming.majorId()!=null)repository.categories(List.of(incoming.majorId()),"MAJOR");
        Profile saved=new Profile(previous.revision()+1,incoming.nickname(),incoming.majorId(),incoming.currentStage(),incoming.bio());repository.profile(user,saved);
        audit.changed(user,Action.PROFILE_UPDATE,ObjectType.PROFILE,user,previous.revision(),saved.revision(),null,null);return saved;
    });}
    public Created create(long user,String request){return write.execute(tx->{repository.lockUser(user);Long previous=repository.created(user,request);if(previous!=null)return new Created(previous);long id=repository.allocate("article");repository.create(id,user,request,clock.instant());return new Created(id);});}
    public OwnedPage list(long user,int page,int size){if(page<1||page>1_000_000||size<1||size>100)throw ApiException.invalid();return read.execute(tx->repository.list(user,page,size));}
    public Draft draft(long user,long id){return read.execute(tx->{Article a=repository.owned(user,id,false);if(a.state().equals("DELETED"))throw ApiException.notFound();return view(a);});}
    private Draft view(Article a){return new Draft(a.id(),a.state(),a.revision(),repository.content(a.id()),repository.images(a.id()),repository.referenced(a.id()));}
    public Draft save(long user,long id,long expected,Content content){
        var references=markdown.inspect(content.body());
        return write.execute(tx->{Article a=repository.owned(user,id,true);editable(a);revision(a,expected);categories(content);repository.imageReferences(id,references.imageIds());repository.save(id,content,references.imageIds(),clock.instant());return view(repository.owned(user,id,false));});
    }
    public Published publish(long user,long id,long expected,String request){return write.execute(tx->{
        Article a=repository.owned(user,id,true);editable(a);
        Published previous=repository.published(id,request,expected);if(previous!=null){if(!a.state().equals("PUBLISHED"))throw conflict("STATE_CONFLICT");audit.unchanged(Action.ARTICLE_PUBLISH,ObjectType.ARTICLE);return previous;}
        revision(a,expected);Content c=repository.content(id);var refs=markdown.inspect(c.body());categories(c);repository.imageReferences(id,refs.imageIds());
        var missing=new ArrayList<String>();if(c.title().isBlank())missing.add("title");if(c.tags().jobIds().isEmpty())missing.add("jobIds");if(c.tags().majorIds().isEmpty())missing.add("majorIds");
        if(c.experienceStage().isBlank())missing.add("experienceStage");if(c.sourceType()==null)missing.add("sourceType");
        if(refs.readableText().isBlank()&&refs.imageIds().isEmpty())missing.add("body或有效上传图片");if("CURATED".equals(c.sourceType())&&c.sourceNote().isBlank())missing.add("sourceNote");
        if(!missing.isEmpty())throw new ApiException(HttpStatus.valueOf(422),"PUBLISH_INCOMPLETE","发布前请补充："+String.join("、",missing));
        for(String image:refs.imageIds()){ImageRow row=repository.image(image).orElseThrow(ApiException::notFound);files.read(row.id(),row.size(),row.digest());}
        Published published=repository.publish(a,c,c.summary().isBlank()?MarkdownReferences.summary(refs.readableText()):c.summary(),request,clock.instant());
        audit.changed(user,Action.ARTICLE_PUBLISH,ObjectType.ARTICLE,id,a.revision(),published.revision(),State.valueOf(a.state()),State.PUBLISHED);return published;
    });}
    public StateResult changeState(long user,long id,long expected,String action){return write.execute(tx->{
        Article a=repository.owned(user,id,true);String target=action.equals("unpublish")?"AUTHOR_OFFLINE":action.equals("restore")?"PUBLISHED":"DELETED";
        Action operation=action.equals("unpublish")?Action.ARTICLE_UNPUBLISH:action.equals("restore")?Action.ARTICLE_RESTORE:Action.ARTICLE_DELETE;
        if(a.state().equals(target)){audit.unchanged(operation,ObjectType.ARTICLE);return new StateResult(id,target,a.revision());}
        if(!target.equals("DELETED")){
            editable(a);
            if(action.equals("unpublish")&&!a.state().equals("PUBLISHED"))throw conflict("STATE_CONFLICT");
            if(action.equals("restore")&&!a.state().equals("AUTHOR_OFFLINE"))throw conflict("STATE_CONFLICT");
        }
        revision(a,expected);repository.state(id,target,clock.instant());
        audit.changed(user,operation,ObjectType.ARTICLE,id,a.revision(),a.revision()+1,State.valueOf(a.state()),State.valueOf(target));return new StateResult(id,target,a.revision()+1);
    });}
    public Image upload(long user,long article,String request,MultipartFile multipart){
        // Reject foreign/noneditable targets before reading or writing their upload body.
        Article initial=read.execute(tx->repository.owned(user,article,false));editable(initial);
        MediaStore.Validated validated=files.validate(multipart);
        return write.execute(tx->{Article a=repository.owned(user,article,true);editable(a);var previous=repository.uploaded(article,request);
            if(previous.isPresent()){if(!previous.get().digest().equals(validated.digest()))throw conflict("REQUEST_KEY_REUSED");files.read(previous.get().id(),previous.get().size(),previous.get().digest());return previous.get().view();}
            String id=UUID.randomUUID().toString();files.install(id,validated.bytes());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)files.rollbackCreated(id);}
            });
            repository.image(article,request,id,validated,clock.instant());return repository.image(id).orElseThrow().view();
        });
    }
    public record BinaryImage(byte[] bytes,String mime) {}
    public BinaryImage image(String id,String authorization){return read.execute(tx->{
        ImageRow image=repository.image(WritingInput.requestId(id)).orElseThrow(ApiException::notFound);
        if(!image.visible()){
            if(image.state().equals("DELETED")||authorization==null||!authorization.startsWith("Bearer "))throw ApiException.notFound();
            try{var me=auth.me(auth.authenticate(authorization.substring(7)));if(image.owner()==null||me.userId()!=image.owner())throw ApiException.notFound();}
            catch(ApiException failure){if(failure.status()==HttpStatus.UNAUTHORIZED||failure.status()==HttpStatus.FORBIDDEN)throw ApiException.notFound();throw failure;}
        }
        return new BinaryImage(files.read(image.id(),image.size(),image.digest()),image.mime());
    });}
}
