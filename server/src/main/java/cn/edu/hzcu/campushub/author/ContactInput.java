package cn.edu.hzcu.campushub.author;

import cn.edu.hzcu.campushub.authoring.WritingInput;
import cn.edu.hzcu.campushub.common.ApiException;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class ContactInput {
    private ContactInput(){}
    public static AuthorModels.Contact parse(JsonNode body){
        WritingInput.fields(body,"expectedRevision","enabled","type","value");
        if(!body.path("enabled").isBoolean())throw ApiException.invalid();
        boolean enabled=body.path("enabled").asBoolean();long revision=WritingInput.revision(body);
        if(body.path("type").isNull()&&body.path("value").isNull()){
            if(enabled)throw ApiException.invalid();return new AuthorModels.Contact(revision,false,null,null);
        }
        String type=WritingInput.text(body,"type",16);
        if(!Set.of("WECHAT","QQ","WEB").contains(type))throw ApiException.invalid();
        String raw=WritingInput.text(body,"value",type.equals("WEB")?2000:200);
        if(raw.codePoints().anyMatch(Character::isISOControl))throw ApiException.invalid();
        String value=raw.strip();if(value.isEmpty())throw ApiException.invalid();
        if(type.equals("WEB"))WritingInput.httpUrl(value);
        return new AuthorModels.Contact(revision,enabled,type,value);
    }
}
