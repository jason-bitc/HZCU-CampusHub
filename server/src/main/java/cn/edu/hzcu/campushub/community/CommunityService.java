package cn.edu.hzcu.campushub.community;

import static cn.edu.hzcu.campushub.community.CommunityModels.*;
import cn.edu.hzcu.campushub.audit.BusinessAudit;
import cn.edu.hzcu.campushub.audit.BusinessAudit.*;
import cn.edu.hzcu.campushub.authoring.*;
import cn.edu.hzcu.campushub.common.*;
import java.time.Clock;
import java.util.*;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CommunityService {
    private static final Logger LOG=LoggerFactory.getLogger(CommunityService.class);
    private final CommunityRepository db;private final WritingRepository writing;private final MediaStore files;private final BusinessAudit audit;private final AdminAuthService admins;private final Clock clock;private final TransactionTemplate read,write;
    public CommunityService(CommunityRepository db,WritingRepository writing,MediaStore files,BusinessAudit audit,AdminAuthService admins,Clock clock,PlatformTransactionManager manager){this.db=db;this.writing=writing;this.files=files;this.audit=audit;this.admins=admins;this.clock=clock;read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);write=new TransactionTemplate(manager);write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);}
    public static ApiException conflict(String code){return new ApiException(HttpStatus.CONFLICT,code,switch(code){case "REQUEST_KEY_REUSED"->"该请求编号已用于其他内容";case "REVISION_CONFLICT"->"案件已更新，请刷新后重试";case "CASE_ASSIGNED"->"案件已由其他管理员认领";case "CASE_NOT_ASSIGNEE"->"仅当前受理人可执行此操作";case "IMAGE_ALREADY_USED"->"证据图片已绑定其他案件";default->"当前案件状态不允许此操作";});}
    private Receipt replay(Previous p,String digest){if(!p.digest().equals(digest))throw conflict("REQUEST_KEY_REUSED");RequestTrace.caseId(p.receipt().caseId());return p.receipt();}
    public Receipt submit(long user,CommunityInput.Submission input){return RequestTrace.write(write,tx->{
        String digest=CommunityInput.fingerprint(input);var previous=db.previous(user,input.requestId());if(previous.isPresent())return replay(previous.get(),digest);
        Target target;try{target=db.target(input.targetType(),input.targetId());}catch(ApiException unavailable){previous=db.previous(user,input.requestId());if(previous.isPresent())return replay(previous.get(),digest);throw unavailable;}
        writing.lockUser(user);previous=db.previous(user,input.requestId());if(previous.isPresent())return replay(previous.get(),digest);
        RequestTrace.stage(RequestTrace.Stage.ATTACHMENT);for(String id:input.imageIds()){var image=db.image(id).orElseThrow(ApiException::notFound);if(image.user()!=user)throw ApiException.notFound();if(image.caseId()!=null)throw conflict("IMAGE_ALREADY_USED");files.read(image.id(),image.size(),image.digest());}
        RequestTrace.stage(RequestTrace.Stage.PERSIST);long id=writing.allocate("community");var now=clock.instant();db.create(id,user,input,target,digest,now);db.attach(user,id,input.imageIds());Case c=db.row(id,false);db.history(id,c,RequestTrace.Domain.USER,user,"CASE_SUBMIT",null,"案件已提交","","",now);audit.changed(user,Action.CASE_SUBMIT,ObjectType.CASE,id,0,1,null,State.OPEN);return new Receipt(id,1,"OPEN");
    });}
    public Map<String,Object> upload(long user,String key,MultipartFile file){
        var validated=files.validate(file);return RequestTrace.write(write,tx->{writing.lockUser(user);RequestTrace.stage(RequestTrace.Stage.RECEIPT);var old=db.upload(user,key);if(old.isPresent()){var image=old.get();if(!image.digest().equals(validated.digest()))throw conflict("REQUEST_KEY_REUSED");files.read(image.id(),image.size(),image.digest());audit.unchanged(Action.CASE_IMAGE_UPLOAD,ObjectType.CASE_IMAGE);return image.view();}
            String id=UUID.randomUUID().toString();files.install(id,validated.bytes());TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)files.rollbackCreated(id);}});
            RequestTrace.stage(RequestTrace.Stage.PERSIST);var image=db.createImage(user,key,id,validated,clock.instant());audit.changed(user,Action.CASE_IMAGE_UPLOAD,ObjectType.CASE_IMAGE,image.sequence(),0,1,null,null);return image.view();
        });
    }
    public WritingService.BinaryImage image(long user,String id){return read.execute(tx->{var image=db.image(WritingInput.requestId(id)).filter(i->i.user()==user).orElseThrow(ApiException::notFound);return new WritingService.BinaryImage(files.read(image.id(),image.size(),image.digest()),image.mime());});}
    public Page page(long actor,boolean admin,CommunityPage page){return read.execute(tx->{RequestTrace.stage(RequestTrace.Stage.READ);return db.page(actor,admin,page);});}
    public Map<String,Object> detail(long actor,long id,boolean admin){return read.execute(tx->{Case c=db.row(id,false);if(!admin&&c.user()!=actor)throw ApiException.notFound();RequestTrace.stage(RequestTrace.Stage.READ);return db.detail(c,admin);});}
    public Map<String,Object> context(long admin,long id){return read.execute(tx->{Case c=db.row(id,false);RequestTrace.stage(RequestTrace.Stage.READ);var result=db.context(c);LOG.info("case access actorId={} caseId={} objectType={}",admin,id,"CONTEXT");return result;});}
    public WritingService.BinaryImage adminImage(long admin,long id,String imageId){return read.execute(tx->{db.row(id,false);String image=WritingInput.requestId(imageId);var evidence=db.image(image).filter(i->Objects.equals(i.caseId(),id));byte[] bytes;String mime;
        if(evidence.isPresent()){var i=evidence.get();bytes=files.read(i.id(),i.size(),i.digest());mime=i.mime();}else{var metadata=db.contextMedia(id,image);bytes=files.read(image,((Number)metadata.get("size")).longValue(),(String)metadata.get("digest"));mime=(String)metadata.get("mime");}
        LOG.info("case access actorId={} caseId={} objectType={}",admin,id,"MEDIA");return new WritingService.BinaryImage(bytes,mime);
    });}
    public Receipt manage(long admin,long id,CommunityInput.Command command){return RequestTrace.write(write,tx->{
        admins.active(admin,false);String digest=CommunityInput.fingerprint(id,command);var previous=db.adminReceipt(admin,command.requestId());if(previous.isPresent())return replay(previous.get(),digest);
        Case c=db.row(id,true);admins.active(admin,true);previous=db.adminReceipt(admin,command.requestId());if(previous.isPresent())return replay(previous.get(),digest);
        RequestTrace.revisions(command.expectedRevision(),c.revision());if(command.expectedRevision()!=c.revision())throw conflict("REVISION_CONFLICT");String state;Long assignee=admin;
        switch(command.action()){
            case "CASE_CLAIM"->{if(c.state().equals("PROCESSING")&&Objects.equals(c.assignee(),admin)){db.receipt(admin,command.requestId(),digest,c);audit.unchanged(RequestTrace.Domain.COMMUNITY_ADMIN,Action.CASE_CLAIM,ObjectType.CASE);return new Receipt(c.id(),c.revision(),c.state());}if(!c.state().equals("OPEN")){if(c.assignee()!=null&&!Objects.equals(c.assignee(),admin))throw conflict("CASE_ASSIGNED");throw conflict("CASE_STATE_CONFLICT");}state="PROCESSING";}
            case "CASE_RELEASE"->{if(!Objects.equals(c.assignee(),admin))throw conflict("CASE_NOT_ASSIGNEE");if(!Set.of("PROCESSING","NEEDS_ACTION").contains(c.state()))throw conflict("CASE_STATE_CONFLICT");state="OPEN";assignee=null;}
            case "CASE_DECISION"->{if(!Objects.equals(c.assignee(),admin))throw conflict("CASE_NOT_ASSIGNEE");if(!Set.of("PROCESSING","NEEDS_ACTION").contains(c.state()))throw conflict("CASE_STATE_CONFLICT");if(!(c.kind().equals("FEEDBACK")?Set.of("ANSWERED","RECORDED"):Set.of("NO_VIOLATION","DUPLICATE","REQUIRES_GOVERNANCE")).contains(command.outcome()))throw conflict("CASE_OUTCOME_INVALID");state=command.outcome().equals("REQUIRES_GOVERNANCE")?"NEEDS_ACTION":"CLOSED";}
            default->throw ApiException.invalid();
        }
        RequestTrace.stage(RequestTrace.Stage.PERSIST);long sequence=writing.allocate("community");var now=clock.instant();db.state(id,state,assignee,now);Case after=db.row(id,false);db.history(sequence,after,RequestTrace.Domain.COMMUNITY_ADMIN,admin,command.action(),command.outcome(),command.reply(),command.note(),command.reason(),now);db.receipt(admin,command.requestId(),digest,after);
        audit.changed(RequestTrace.Domain.COMMUNITY_ADMIN,admin,Action.valueOf(command.action()),ObjectType.CASE,id,c.revision(),after.revision(),State.valueOf(c.state()),State.valueOf(after.state()));return new Receipt(id,after.revision(),state);
    });}
}
