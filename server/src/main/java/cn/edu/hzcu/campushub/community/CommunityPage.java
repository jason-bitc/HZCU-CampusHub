package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.article.ArticleQuery;
import cn.edu.hzcu.campushub.common.ApiException;
import java.util.*;
import org.springframework.util.*;

public record CommunityPage(int page,int size,Long snapshot,String kind,String state){
    public static CommunityPage parse(MultiValueMap<String,String> p,boolean admin){
        var allowed=admin?Set.of("page","size","snapshot","kind","state"):Set.of("page","size","snapshot");if(p.entrySet().stream().anyMatch(e->!allowed.contains(e.getKey())||e.getValue().size()!=1))throw ApiException.invalid();
        var ordinary=new LinkedMultiValueMap<String,String>();for(String k:List.of("page","size"))if(p.containsKey(k))ordinary.put(k,p.get(k));var q=ArticleQuery.parse(ordinary);Long snapshot=null;
        if(p.containsKey("snapshot")){String raw=p.getFirst("snapshot");try{if(!raw.matches("[0-9]{1,19}"))throw ApiException.invalid();snapshot=Long.parseLong(raw);}catch(NumberFormatException failure){throw ApiException.invalid();}}
        String kind=p.getFirst("kind"),state=p.getFirst("state");if(kind!=null&&!Set.of("FEEDBACK","REPORT").contains(kind)||state!=null&&!Set.of("OPEN","PROCESSING","NEEDS_ACTION","CLOSED").contains(state))throw ApiException.invalid();return new CommunityPage(q.page(),q.size(),snapshot,kind,state);
    }
}
