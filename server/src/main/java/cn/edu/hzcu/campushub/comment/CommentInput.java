package cn.edu.hzcu.campushub.comment;

import static cn.edu.hzcu.campushub.comment.CommentModels.*;
import cn.edu.hzcu.campushub.authoring.*;
import cn.edu.hzcu.campushub.common.ApiException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class CommentInput {
    private final int maxCodePoints;
    public CommentInput(@Value("${campushub.comments.body-max-code-points}") int maxCodePoints){
        if(maxCodePoints<1||maxCodePoints>100_000)throw new IllegalArgumentException("Invalid comment text limit");this.maxCodePoints=maxCodePoints;
    }
    public Input parse(JsonNode n){
        WritingInput.fields(n,"requestId","body","imageIds","replyToCommentId");
        String body=WritingInput.text(n,"body",Integer.MAX_VALUE).replace("\r\n","\n").replace('\r','\n');
        if(body.codePointCount(0,body.length())>maxCodePoints)throw new ApiException(HttpStatus.valueOf(413),"COMMENT_TOO_LARGE","评论文字超过允许大小");
        if(body.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))throw ApiException.invalid();
        if(!n.path("imageIds").isArray())throw ApiException.invalid();
        var ids=new ArrayList<String>();var unique=new HashSet<String>();
        for(var value:n.path("imageIds")){
            if(!value.isString())throw ApiException.invalid();String id=WritingInput.requestId(value.asString());
            if(!unique.add(id))throw ApiException.invalid();ids.add(id);
        }
        Long reply=n.path("replyToCommentId").isNull()?null:WritingInput.number(n.path("replyToCommentId"));
        if(reply!=null&&reply==0)throw ApiException.invalid();
        if(body.isBlank()&&ids.isEmpty())throw new ApiException(HttpStatus.valueOf(422),"COMMENT_EMPTY","请填写评论文字或添加有效图片");
        return new Input(WritingInput.requestId(n),body,List.copyOf(ids),reply);
    }
    public static String fingerprint(long article,Input input){
        try{
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            out.writeByte(1);out.writeLong(article);out.writeBoolean(input.replyToCommentId()!=null);
            if(input.replyToCommentId()!=null)out.writeLong(input.replyToCommentId());
            byte[] text=input.body().getBytes(StandardCharsets.UTF_8);out.writeInt(text.length);out.write(text);out.writeInt(input.imageIds().size());
            for(String id:input.imageIds())out.write(id.getBytes(StandardCharsets.US_ASCII));
            return MediaStore.digest(bytes.toByteArray());
        }catch(IOException impossible){throw new IllegalStateException("Comment digest failed");}
    }
}
