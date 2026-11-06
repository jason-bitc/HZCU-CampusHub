package cn.edu.hzcu.campushub.author;

import cn.edu.hzcu.campushub.auth.*;
import cn.edu.hzcu.campushub.common.ApiException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContactInputTest {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    static Map<String,Object> input(boolean enabled,Object type,Object value){var b=new LinkedHashMap<String,Object>();b.put("expectedRevision",0);b.put("enabled",enabled);b.put("type",type);b.put("value",value);return b;}
    @Test void disabledCanClearOrRetainAndEnabledValuesAreTrimmed(){
        var empty=ContactInput.parse(JSON.valueToTree(input(false,null,null)));assertThat(empty.type()).isNull();assertThat(empty.value()).isNull();
        assertThat(ContactInput.parse(JSON.valueToTree(input(false,"QQ","  qq-value  "))).value()).isEqualTo("qq-value");
        var chosen=ContactInput.parse(JSON.valueToTree(input(true,"WECHAT","  微信文本  ")));assertThat(chosen.value()).isEqualTo("微信文本");assertThat(chosen.toString()).doesNotContain("微信文本");
    }
    @Test void unicodeCodePointLimitsAndAbsoluteWebAddresses(){
        assertThat(ContactInput.parse(JSON.valueToTree(input(true,"WECHAT","😀".repeat(200)))).value()).hasSize(400);
        assertThat(ContactInput.parse(JSON.valueToTree(input(true,"WEB","https://example.test/"+"x".repeat(1979)))).value()).hasSize(2000);
        for(String url:List.of("/relative","javascript:alert(1)","file:///tmp/x","https://user:pass@example.test/","https://example.test/\n"))
            assertThatThrownBy(()->ContactInput.parse(JSON.valueToTree(input(true,"WEB",url)))).isInstanceOf(ApiException.class);
    }
    @Test void inconsistentUnknownControlAndOversizedInputIsRejected(){
        var invalid=new ArrayList<Map<String,Object>>();invalid.add(input(true,null,null));invalid.add(input(false,"QQ",null));invalid.add(input(false,null,"value"));invalid.add(input(true,"QR","value"));invalid.add(input(true,"QQ"," "));invalid.add(input(true,"QQ","x\n"));invalid.add(input(true,"WECHAT","😀".repeat(201)));invalid.add(input(true,"WEB","https://example.test/"+"x".repeat(1980)));
        var forged=input(false,null,null);forged.put("userId",12);invalid.add(forged);
        var booleanString=input(true,"QQ","value");booleanString.put("enabled","true");invalid.add(booleanString);
        var revision=input(true,"QQ","value");revision.put("expectedRevision",-1);invalid.add(revision);
        for(var body:invalid)assertThatThrownBy(()->ContactInput.parse(JSON.valueToTree(body))).isInstanceOf(ApiException.class);
    }
    @Test void readerGuardRequiresOrdinaryActiveCertifiedIdentity(){
        var auth=mock(AuthService.class,CALLS_REAL_METHODS);
        for(String role:List.of("COMMUNITY_ADMIN","OPS_ADMIN","ADMIN")){
            var me=new AuthModels.Me(1,"not-disclosed",role,"ACTIVE","VALID",Instant.EPOCH);
            assertThatThrownBy(()->auth.me(new AuthModels.Identity(1,me))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(403));
        }
        var ordinary=new AuthModels.Me(1,"not-disclosed","USER","ACTIVE","VALID",Instant.EPOCH);assertThat(auth.me(new AuthModels.Identity(1,ordinary))).isEqualTo(ordinary);
    }
}
