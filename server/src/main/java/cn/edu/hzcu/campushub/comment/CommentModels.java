package cn.edu.hzcu.campushub.comment;

import cn.edu.hzcu.campushub.authoring.WritingModels.Image;
import java.time.Instant;
import java.util.*;

public final class CommentModels {
    private CommentModels(){}
    public record Input(String requestId,String body,List<String> imageIds,Long replyToCommentId){
        @Override public String toString(){return "CommentInput[redacted]";}
    }
    public record Receipt(long commentId,long articleId,long rootCommentId,Long replyToCommentId){}
    public record Deleted(long commentId,String state){}
    public record Page(List<Map<String,Object>> items,long total,int page,int size){}
    public record Replies(Map<String,Object> root,List<Map<String,Object>> items,long total,int page,int size){}
    record Row(long id,long article,long owner,Long root,Long replyTo,String state,long revision,String body,Instant created,long replies){
        long rootId(){return root==null?id:root;}
        Receipt receipt(){return new Receipt(id,article,rootId(),replyTo);}
        @Override public String toString(){return "CommentRow[redacted]";}
    }
    record Picture(String id,long article,long owner,String digest,String mime,long size,int width,int height,Long comment,String commentState,boolean articlePublic){
        Image view(){return new Image(id,"/api/v1/media/comment-images/"+id,mime,size,width,height);}
    }
    record Previous(long comment,String digest){}
}
