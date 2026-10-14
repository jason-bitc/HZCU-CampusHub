package cn.edu.hzcu.campushub.authoring;

import cn.edu.hzcu.campushub.common.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MarkdownReferencesTest {
    private final MarkdownReferences parser=new MarkdownReferences(1024*1024);
    private final String image="/api/v1/media/images/01234567-89ab-cdef-0123-456789abcdef";
    @Test void referenceStyleEscapesAndCodeDoNotBecomePhantomImageAccess(){
        String body="# 原文\n\n![真图][photo]\n\n[photo]: "+image+"\n\n`![假图]("+image+")`\n\n```md\n![假图](/api/v1/media/images/11111111-1111-1111-1111-111111111111)\n```\n\n\\![转义]("+image+")";
        var result=parser.inspect(body);assertThat(result.imageIds()).containsExactly("01234567-89ab-cdef-0123-456789abcdef");assertThat(result.readableText()).contains("原文","假图");
    }
    @Test void imageOnlyAndCodeOnlyHaveDifferentPublicationMeaning(){assertThat(parser.inspect("![图]("+image+")").readableText()).isEmpty();assertThat(parser.inspect("```java\nint x=1;\n```").readableText()).contains("int x=1;");}
    @Test void unsafeImageAndSourceUrlsCannotBecomeFileKeys(){
        for(String destination:new String[]{"../../secret.png","/api/v1/media/images/../secret","data:image/png;base64,AAAA","file:///etc/passwd","javascript:alert(1)"})assertThatThrownBy(()->parser.inspect("![图]("+destination+")")).isInstanceOf(ApiException.class);
        for(String destination:new String[]{"file:///etc/passwd","https://user:password@example.com/a","https://example.com/\nfoo"})assertThatThrownBy(()->WritingInput.httpUrl(destination)).isInstanceOf(ApiException.class);
    }
    @Test void utf8LimitIsBytesAndSummaryDoesNotSplitUnicode(){assertThatThrownBy(()->new MarkdownReferences(5).inspect("中文")).isInstanceOf(ApiException.class);assertThat(MarkdownReferences.summary("😀".repeat(1001)).codePointCount(0,2000)).isEqualTo(1000);}
}
