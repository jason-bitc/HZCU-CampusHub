package cn.edu.hzcu.campushub.comment;

import cn.edu.hzcu.campushub.common.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class CommentInputTest {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final CommentInput input=new CommentInput(10000);
    static Map<String,Object> body(String text,List<String> images,Long reply){var b=new LinkedHashMap<String,Object>();b.put("requestId",UUID.randomUUID().toString());b.put("body",text);b.put("imageIds",images);b.put("replyToCommentId",reply);return b;}
    @Test void plainTextPreservesHtmlAndWhitespaceWhileNormalizingLineEndings(){
        var value=input.parse(JSON.valueToTree(body(" <script>literal</script>\r\n下一行\r\t缩进 ",List.of(),null)));
        assertThat(value.body()).isEqualTo(" <script>literal</script>\n下一行\n\t缩进 ");assertThat(value.toString()).doesNotContain("script");
        assertThat(input.parse(JSON.valueToTree(body("😀".repeat(10000),List.of(),null))).body()).hasSize(20000);
        assertThatThrownBy(()->input.parse(JSON.valueToTree(body("😀".repeat(10001),List.of(),null)))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(413));
    }
    @Test void completeFieldsValidIdsAndControlCharactersAreRequired(){
        var invalid=new ArrayList<Map<String,Object>>();invalid.add(body("x\u0000",List.of(),null));invalid.add(body("x\u001b",List.of(),null));invalid.add(body("text",List.of("not-uuid"),null));String id=UUID.randomUUID().toString();invalid.add(body("text",List.of(id,id),null));invalid.add(body("text",List.of(),0L));
        var forged=body("text",List.of(),null);forged.put("rootCommentId",1);invalid.add(forged);var foreign=body("text",List.of(),null);foreign.put("userId",2);invalid.add(foreign);
        for(var value:invalid)assertThatThrownBy(()->input.parse(JSON.valueToTree(value))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->input.parse(JSON.valueToTree(body(" \n\t",List.of(),null)))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("COMMENT_EMPTY"));
    }
    @Test void retryFingerprintUsesNormalizedBusinessContentAndOrderedImages(){
        String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();var first=input.parse(JSON.valueToTree(body("line\r\ntext",List.of(a,b),4L)));var retry=input.parse(JSON.valueToTree(body("line\ntext",List.of(a,b),4L)));
        String digest=CommentInput.fingerprint(1001,first);assertThat(CommentInput.fingerprint(1001,retry)).isEqualTo(digest);
        for(var changed:List.of(body("changed",List.of(a,b),4L),body("line\ntext",List.of(b,a),4L),body("line\ntext",List.of(a,b),5L)))assertThat(CommentInput.fingerprint(1001,input.parse(JSON.valueToTree(changed)))).isNotEqualTo(digest);
        assertThat(CommentInput.fingerprint(1002,retry)).isNotEqualTo(digest);
    }
    @Test void imageOnlyAndManyImagesUseBoundedSqlChunksWithoutBusinessCountLimit(){
        var ids=new ArrayList<String>();for(int i=0;i<250;i++)ids.add(UUID.randomUUID().toString());var value=input.parse(JSON.valueToTree(body("",ids,null)));assertThat(value.imageIds()).hasSize(250);
        var chunks=CommentRepository.chunks(ids);assertThat(chunks).hasSize(3);assertThat(chunks).allSatisfy(c->assertThat(c.size()).isLessThanOrEqualTo(100));assertThat(chunks.stream().flatMap(Collection::stream).toList()).containsExactlyElementsOf(ids);
    }
}
