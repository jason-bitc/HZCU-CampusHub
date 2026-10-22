package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.authoring.*;
import cn.edu.hzcu.campushub.coin.CoinInput;
import cn.edu.hzcu.campushub.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class CommunityInput {
    private final int max;
    public CommunityInput(@Value("${campushub.community.text-max-code-points:10000}") int max){if(max<1||max>100000)throw new IllegalArgumentException("Invalid case text limit");this.max=max;}
    public record Submission(String requestId,String kind,String category,String targetType,Long targetId,String description,List<String> imageIds){}
    public record Command(String requestId,long expectedRevision,String action,String outcome,String reply,String note,String reason){}
    public static String login(String s){if(s==null||!s.matches("[a-z0-9][a-z0-9._-]{2,63}"))throw ApiException.invalid();return s;}
    public String text(JsonNode n,String name,boolean required){String s=WritingInput.text(n,name,Integer.MAX_VALUE).replace("\r\n","\n").replace('\r','\n');if(s.codePointCount(0,s.length())>max||s.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t')||(required&&s.isBlank()))throw ApiException.invalid();return s;}
    public Submission submission(String raw){
        var n=CoinInput.parse(raw,"requestId","kind","category","targetType","targetId","description","imageIds");String kind=WritingInput.text(n,"kind",16),category=WritingInput.text(n,"category",32);
        if(!Set.of("FEEDBACK","REPORT").contains(kind)||!(kind.equals("FEEDBACK")?Set.of("BUG","SUGGESTION","OTHER"):Set.of("ADVERTISEMENT","MISLEADING_CONTENT","OTHER")).contains(category))throw ApiException.invalid();
        String type=n.path("targetType").isNull()?null:WritingInput.text(n,"targetType",16);Long target=n.path("targetId").isNull()?null:WritingInput.number(n.path("targetId"));
        if(type==null?(target!=null||kind.equals("REPORT")):(target==null||target<=0||!Set.of("ARTICLE","COMMENT").contains(type)))throw ApiException.invalid();
        if(!n.path("imageIds").isArray())throw ApiException.invalid();var images=new ArrayList<String>();var seen=new HashSet<String>();for(var image:n.path("imageIds")){if(!image.isString())throw ApiException.invalid();String id=WritingInput.requestId(image.asString());if(!seen.add(id))throw ApiException.invalid();images.add(id);}
        return new Submission(WritingInput.requestId(n),kind,category,type,target,text(n,"description",true),List.copyOf(images));
    }
    public Command command(String action,String raw){
        var n=switch(action){case "CASE_CLAIM"->CoinInput.parse(raw,"requestId","expectedRevision");case "CASE_RELEASE"->CoinInput.parse(raw,"requestId","expectedRevision","reason");case "CASE_DECISION"->CoinInput.parse(raw,"requestId","expectedRevision","outcome","reply","internalNote");default->throw ApiException.invalid();};
        long revision=WritingInput.revision(n);if(revision<1)throw ApiException.invalid();String outcome=action.equals("CASE_DECISION")?WritingInput.text(n,"outcome",32):null;
        if(outcome!=null&&!Set.of("ANSWERED","RECORDED","NO_VIOLATION","DUPLICATE","REQUIRES_GOVERNANCE").contains(outcome))throw ApiException.invalid();
        return new Command(WritingInput.requestId(n),revision,action,outcome,action.equals("CASE_DECISION")?text(n,"reply",true):action.equals("CASE_CLAIM")?"案件已受理":"案件已返回待受理",action.equals("CASE_DECISION")?text(n,"internalNote",false):"",action.equals("CASE_RELEASE")?text(n,"reason",true):"");
    }
    public static String digest(Object... values){try{return MediaStore.digest(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(values));}catch(RuntimeException e){throw new IllegalStateException("Case fingerprint failed");}}
    public static String fingerprint(Submission s){return digest(s.kind(),s.category(),s.targetType(),s.targetId(),s.description(),s.imageIds());}
    public static String fingerprint(long id,Command c){return digest(id,c.action(),c.expectedRevision(),c.outcome(),c.reply(),c.note(),c.reason());}
}
