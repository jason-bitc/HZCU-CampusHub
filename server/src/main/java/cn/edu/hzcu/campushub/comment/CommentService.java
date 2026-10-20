package cn.edu.hzcu.campushub.comment;

import static cn.edu.hzcu.campushub.comment.CommentModels.*;
import cn.edu.hzcu.campushub.audit.BusinessAudit;
import cn.edu.hzcu.campushub.audit.BusinessAudit.*;
import cn.edu.hzcu.campushub.authoring.*;
import cn.edu.hzcu.campushub.common.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CommentService {
    private final CommentRepository comments;
    private final WritingRepository writing;
    private final MediaStore files;
    private final BusinessAudit audit;
    private final Clock clock;
    private final TransactionTemplate read,write;
    public CommentService(CommentRepository comments,WritingRepository writing,MediaStore files,BusinessAudit audit,Clock clock,PlatformTransactionManager manager){
        this.comments=comments;this.writing=writing;this.files=files;this.audit=audit;this.clock=clock;
        read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        write=new TransactionTemplate(manager);write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public Page roots(long user,long article,int page,int size){return read.execute(tx->{
        comments.publicArticle(article,false);return new Page(views(user,comments.page(article,null,page,size)),comments.count(article,null),page,size);
    });}
    public Replies replies(long user,long root,int page,int size){return read.execute(tx->{
        Row thread=comments.row(root);comments.publicArticle(thread.article(),false);if(thread.root()!=null)throw ApiException.notFound();
        var rows=new ArrayList<Row>();rows.add(thread);rows.addAll(comments.page(thread.article(),root,page,size));var views=views(user,rows);
        return new Replies(views.getFirst(),views.subList(1,views.size()),comments.count(thread.article(),root),page,size);
    });}
    private List<Map<String,Object>> views(long viewer,List<Row> rows){
        var parentIds=new LinkedHashSet<Long>();for(Row row:rows)if(row.replyTo()!=null)parentIds.add(row.replyTo());
        var parents=comments.rows(parentIds);var authors=new LinkedHashSet<Long>();
        for(Row row:rows)if(row.state().equals("VISIBLE"))authors.add(row.owner());for(Row row:parents.values())if(row.state().equals("VISIBLE"))authors.add(row.owner());
        var profiles=comments.commenters(authors);var pictures=comments.attached(rows.stream().filter(r->r.state().equals("VISIBLE")).map(Row::id).toList());
        var result=new ArrayList<Map<String,Object>>();
        for(Row row:rows){
            var item=new LinkedHashMap<String,Object>();item.put("commentId",row.id());item.put("articleId",row.article());item.put("rootCommentId",row.rootId());item.put("replyToCommentId",row.replyTo());
            item.put("available",row.state().equals("VISIBLE"));item.put("canDelete",row.owner()==viewer&&!row.state().equals("DELETED"));item.put("replyCount",row.replies());
            if(row.state().equals("VISIBLE")){
                item.put("body",row.body());item.put("bodyFormat","PLAIN_TEXT");item.put("images",pictures.getOrDefault(row.id(),List.of()));item.put("commenter",profiles.get(row.owner()));item.put("createdAt",row.created());
                if(row.replyTo()!=null){Row parent=parents.get(row.replyTo());var target=new LinkedHashMap<String,Object>();target.put("commentId",row.replyTo());boolean visible=parent!=null&&parent.state().equals("VISIBLE");target.put("available",visible);if(visible)target.put("commenter",profiles.get(parent.owner()));item.put("replyTo",target);}
            }
            result.add(item);
        }
        return result;
    }
    public Receipt create(long user,long article,Input input){String digest=CommentInput.fingerprint(article,input);return write.execute(tx->{
        // All comment writes lock the article first, then the per-user receipt gate.
        comments.publicArticle(article,true);writing.lockUser(user);
        RequestTrace.stage(RequestTrace.Stage.RECEIPT);
        var previous=comments.previous(user,input.requestId());
        if(previous.isPresent()){
            if(!previous.get().digest().equals(digest))throw WritingService.conflict("REQUEST_KEY_REUSED");
            audit.unchanged(Action.COMMENT_CREATE,ObjectType.COMMENT);return comments.row(previous.get().comment()).receipt();
        }
        Long root=null;
        if(input.replyToCommentId()!=null){Row parent=comments.row(input.replyToCommentId());if(parent.article()!=article||!parent.state().equals("VISIBLE"))throw ApiException.notFound();root=parent.rootId();}
        RequestTrace.stage(RequestTrace.Stage.ATTACHMENT);var images=comments.pictures(input.imageIds());if(images.size()!=input.imageIds().size())throw ApiException.notFound();
        for(Picture image:images){
            if(image.owner()!=user||image.article()!=article)throw ApiException.notFound();
            if(image.comment()!=null)throw WritingService.conflict("IMAGE_ALREADY_USED");RequestTrace.stage(RequestTrace.Stage.STORAGE);files.read(image.id(),image.size(),image.digest());
        }
        RequestTrace.stage(RequestTrace.Stage.PERSIST);long id=writing.allocate("comment");comments.create(id,article,user,root,input,digest,clock.instant());RequestTrace.comment(id);comments.attach(article,user,id,input.imageIds());
        audit.changed(user,Action.COMMENT_CREATE,ObjectType.COMMENT,id,0,1,null,State.VISIBLE);return comments.row(id).receipt();
    });}
    public Deleted delete(long user,long id){return write.execute(tx->{
        Row initial=comments.row(id);comments.article(initial.article(),true);Row row=comments.row(id);if(row.owner()!=user)throw ApiException.notFound();
        if(row.state().equals("DELETED")){audit.unchanged(Action.COMMENT_DELETE,ObjectType.COMMENT);return new Deleted(id,"DELETED");}
        RequestTrace.stage(RequestTrace.Stage.PERSIST);comments.delete(id);audit.changed(user,Action.COMMENT_DELETE,ObjectType.COMMENT,id,row.revision(),row.revision()+1,State.valueOf(row.state()),State.DELETED);return new Deleted(id,"DELETED");
    });}
    public WritingModels.Image upload(long user,long article,String request,MultipartFile file){
        read.execute(tx->{comments.publicArticle(article,false);return true;});var validated=files.validate(file);
        return write.execute(tx->{
            comments.publicArticle(article,true);writing.lockUser(user);RequestTrace.stage(RequestTrace.Stage.RECEIPT);var previous=comments.upload(user,request);
            if(previous.isPresent()){
                Picture image=previous.get();if(image.article()!=article||!image.digest().equals(validated.digest()))throw WritingService.conflict("REQUEST_KEY_REUSED");
                files.read(image.id(),image.size(),image.digest());return image.view();
            }
            String id=UUID.randomUUID().toString();files.install(id,validated.bytes());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)files.rollbackCreated(id);}});
            RequestTrace.stage(RequestTrace.Stage.PERSIST);comments.picture(article,user,request,id,validated,clock.instant());return comments.picture(id).orElseThrow().view();
        });
    }
    public WritingService.BinaryImage image(long user,String id){return read.execute(tx->{
        Picture image=comments.picture(WritingInput.requestId(id)).orElseThrow(ApiException::notFound);
        RequestTrace.article(image.article());if(image.comment()!=null)RequestTrace.comment(image.comment());
        if(!image.articlePublic()||(image.comment()==null?image.owner()!=user:!"VISIBLE".equals(image.commentState())))throw ApiException.notFound();
        return new WritingService.BinaryImage(files.read(image.id(),image.size(),image.digest()),image.mime());
    });}
}
