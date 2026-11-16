package cn.edu.hzcu.campushub.authoring;

import static cn.edu.hzcu.campushub.authoring.WritingModels.*;
import cn.edu.hzcu.campushub.article.ArticleModels.SourceLink;
import cn.edu.hzcu.campushub.common.ApiException;
import java.net.URI;
import java.util.*;
import tools.jackson.databind.JsonNode;

public final class WritingInput {
    private WritingInput() {}
    public static void fields(JsonNode n,String... fields){
        if(n==null||!n.isObject()||n.size()!=fields.length)throw ApiException.invalid();
        for(var entry:n.properties())if(!Set.of(fields).contains(entry.getKey()))throw ApiException.invalid();
    }
    public static String text(JsonNode n,String key,int max){
        if(!n.path(key).isString())throw ApiException.invalid();String s=n.path(key).asString();
        if(s.codePointCount(0,s.length())>max||!java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(s))throw ApiException.invalid();return s;
    }
    public static long number(JsonNode n){if(!n.isIntegralNumber()||!n.canConvertToLong()||n.asLong()<0)throw ApiException.invalid();return n.asLong();}
    public static long revision(JsonNode n){return number(n.path("expectedRevision"));}
    public static String requestId(String id){if(id==null||!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw ApiException.invalid();return id;}
    public static String requestId(JsonNode n){return requestId(text(n,"requestId",36));}
    public static Profile profile(JsonNode n){
        fields(n,"expectedRevision","nickname","majorId","currentStage","bio");String name=text(n,"nickname",40).strip();
        if(name.isEmpty()||name.codePoints().anyMatch(Character::isISOControl))throw ApiException.invalid();
        String stage=text(n,"currentStage",24);if(!Set.of("UNSET","ENROLLED","GRADUATED").contains(stage))throw ApiException.invalid();
        Long major=n.path("majorId").isNull()?null:number(n.path("majorId"));if(major!=null&&major==0)throw ApiException.invalid();
        return new Profile(revision(n),name,major,stage,text(n,"bio",1000));
    }
    private static List<Long> ids(JsonNode n,String key){
        if(!n.path(key).isArray())throw ApiException.invalid();var ids=new TreeSet<Long>();
        for(var value:n.path(key)){long id=number(value);if(id==0||!ids.add(id))throw ApiException.invalid();}return List.copyOf(ids);
    }
    public static Content content(JsonNode n){
        fields(n,"expectedRevision","title","summary","body","jobIds","majorIds","companyIds","topicIds","experienceStage","sourceType","sourceNote","sourceLinks");
        String type=n.path("sourceType").isNull()?null:text(n,"sourceType",24);
        if(type!=null&&!Set.of("DEMO","CURATED","FIRST_HAND").contains(type))throw ApiException.invalid();
        if(!n.path("sourceLinks").isArray())throw ApiException.invalid();var links=new ArrayList<SourceLink>();
        for(var link:n.path("sourceLinks")){fields(link,"label","url");String label=text(link,"label",200).strip();String url=text(link,"url",2000);if(label.isEmpty())throw ApiException.invalid();httpUrl(url);links.add(new SourceLink(label,url));}
        return new Content(text(n,"title",200),text(n,"summary",1000),text(n,"body",Integer.MAX_VALUE),new Tags(ids(n,"jobIds"),ids(n,"majorIds"),ids(n,"companyIds"),ids(n,"topicIds")),text(n,"experienceStage",64),type,text(n,"sourceNote",2000),List.copyOf(links));
    }
    public static void httpUrl(String value){
        try{URI uri=URI.create(value);if(uri.getScheme()==null||!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||value.codePoints().anyMatch(Character::isISOControl))throw ApiException.invalid();}
        catch(IllegalArgumentException e){throw ApiException.invalid();}
    }
}
