package cn.edu.hzcu.campushub.article;

import cn.edu.hzcu.campushub.common.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.*;

class ArticleQueryTest {
    @Test void defaultsAndEmptyKeywordAreValid() {
        var params = new LinkedMultiValueMap<String,String>();
        params.add("keyword", "  ");
        var query = ArticleQuery.parse(params);
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.sort()).isEqualTo("recognized");
        assertThat(query.keyword()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"0", "-1", "1.5", "9223372036854775808", "1 OR 1=1", ""})
    void invalidIdentifiersRejected(String raw) {
        assertThatThrownBy(() -> ArticleQuery.requiredId(raw)).isInstanceOf(ApiException.class);
    }
    @Test void duplicatesUnknownKeysControlsAndUnboundedParametersRejected() {
        for (var entry : List.of(new String[]{"page","1000001"}, new String[]{"size","101"},
                new String[]{"sort","coins desc"}, new String[]{"authorId","1"},
                new String[]{"keyword","x\ny"}, new String[]{"keyword","x".repeat(101)})) {
            var params = new LinkedMultiValueMap<String,String>(); params.add(entry[0],entry[1]);
            assertThatThrownBy(() -> ArticleQuery.parse(params)).isInstanceOf(ApiException.class);
        }
        var params = new LinkedMultiValueMap<String,String>(); params.add("page","1"); params.add("page","2");
        assertThatThrownBy(() -> ArticleQuery.parse(params)).isInstanceOf(ApiException.class);
    }
    @Test void offsetUsesLongArithmetic() {
        var params = new LinkedMultiValueMap<String,String>(); params.add("page","1000000"); params.add("size","100");
        assertThat(ArticleQuery.parse(params).offset()).isEqualTo(99_999_900L);
    }
}
