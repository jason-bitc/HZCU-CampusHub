package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.common.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class CommunityInputTest {
    final CommunityInput input=new CommunityInput(10000);
    static final JsonMapper JSON=JsonMapper.builder().build();
    static Map<String,Object> submission(){var d=new LinkedHashMap<String,Object>();d.put("requestId",UUID.randomUUID().toString());d.put("kind","FEEDBACK");d.put("category","BUG");d.put("targetType",null);d.put("targetId",null);d.put("description","问题");d.put("imageIds",List.of());return d;}
    @Test void reportTargetCategoryAndIdentityFieldsAreStrict(){
        var d=submission();d.put("kind","REPORT");assertThatThrownBy(()->input.submission(JSON.writeValueAsString(d))).isInstanceOf(ApiException.class);d.put("category","ADVERTISEMENT");d.put("targetType","ARTICLE");d.put("targetId",1);assertThat(input.submission(JSON.writeValueAsString(d)).targetId()).isEqualTo(1);
        for(String field:List.of("userId","role","status","assigneeId","articleVersionId")){var bad=new LinkedHashMap<>(d);bad.put(field,1);assertThatThrownBy(()->input.submission(JSON.writeValueAsString(bad))).isInstanceOf(ApiException.class);}
    }
    @Test void descriptionsNormalizeNewlinesButHaveNoMinimumWordCount(){var d=submission();d.put("description","字");assertThat(input.submission(JSON.writeValueAsString(d)).description()).isEqualTo("字");d.put("description","a\r\nb");assertThat(input.submission(JSON.writeValueAsString(d)).description()).isEqualTo("a\nb");for(String value:List.of(" ","\u0001","字".repeat(10001))){d.put("description",value);assertThatThrownBy(()->input.submission(JSON.writeValueAsString(d))).isInstanceOf(ApiException.class);}}
    @Test void pictureListsHaveNoBusinessCountCapButRequireUniqueUuids(){var d=submission();var images=new ArrayList<String>();for(int i=0;i<120;i++)images.add(UUID.randomUUID().toString());d.put("imageIds",images);assertThat(input.submission(JSON.writeValueAsString(d)).imageIds()).hasSize(120);images.add(images.getFirst());assertThatThrownBy(()->input.submission(JSON.writeValueAsString(d))).isInstanceOf(ApiException.class);}
    @Test void normalizedFingerprintsIncludeTargetAndImageOrder(){var d=submission();var a=input.submission(JSON.writeValueAsString(d));d.put("requestId",UUID.randomUUID().toString());assertThat(CommunityInput.fingerprint(input.submission(JSON.writeValueAsString(d)))).isEqualTo(CommunityInput.fingerprint(a));d.put("description","不同");assertThat(CommunityInput.fingerprint(input.submission(JSON.writeValueAsString(d)))).isNotEqualTo(CommunityInput.fingerprint(a));}
    @Test void commandsRequireRevisionReasonAndSeparateInternalNote(){String key=UUID.randomUUID().toString();assertThatThrownBy(()->input.command("CASE_RELEASE",JSON.writeValueAsString(Map.of("requestId",key,"expectedRevision",1,"reason"," ")))).isInstanceOf(ApiException.class);var c=input.command("CASE_DECISION",JSON.writeValueAsString(Map.of("requestId",key,"expectedRevision",2,"outcome","REQUIRES_GOVERNANCE","reply","待后续处理","internalNote","内部核查")));assertThat(c.reply()).isNotEqualTo(c.note());assertThatThrownBy(()->input.command("CASE_CLAIM",JSON.writeValueAsString(Map.of("requestId",key,"expectedRevision",0)))).isInstanceOf(ApiException.class);}
    @Test void paginationRejectsIdentityDuplicateAndUnsupportedFilters(){for(String name:List.of("userId","adminId","kind","role")){var p=new LinkedMultiValueMap<String,String>();p.add(name,"1");assertThatThrownBy(()->CommunityPage.parse(p,false)).isInstanceOf(ApiException.class);}var p=new LinkedMultiValueMap<String,String>();p.add("page","1");p.add("page","2");assertThatThrownBy(()->CommunityPage.parse(p,true)).isInstanceOf(ApiException.class);assertThat(CommunityPage.parse(new LinkedMultiValueMap<>(),true).size()).isEqualTo(20);}
    @Test void actorDomainAndCaseSurviveAllTraceMutationsAndClear(){try{MDC.put("traceId",UUID.randomUUID().toString());RequestTrace.domain(RequestTrace.Domain.COMMUNITY_ADMIN);RequestTrace.actor(1);RequestTrace.caseId(2);RequestTrace.article(3);RequestTrace.comment(4);RequestTrace.author(5);RequestTrace.revisions(1,2);RequestTrace.stage(RequestTrace.Stage.AUDIT);assertThat(RequestTrace.context().fields()).contains("actorDomain=COMMUNITY_ADMIN","caseId=2","actorId=1");var captured=RequestTrace.snapshot();RequestTrace.clear();RequestTrace.with(captured,()->assertThat(RequestTrace.context().actorDomain()).isEqualTo(RequestTrace.Domain.COMMUNITY_ADMIN));assertThat(RequestTrace.context().actorDomain()).isEqualTo(RequestTrace.Domain.USER);}finally{RequestTrace.clear();}}
    @Test void noAdminPrivilegeCanBeEncodedInLoginNameOrInvalidConfig(){for(String name:List.of("ab","root@school","../admin","Admin"," admin "))assertThatThrownBy(()->CommunityInput.login(name)).isInstanceOf(ApiException.class);assertThatThrownBy(()->new CommunityInput(0)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new CommunityInput(100001)).isInstanceOf(IllegalArgumentException.class);}
}
