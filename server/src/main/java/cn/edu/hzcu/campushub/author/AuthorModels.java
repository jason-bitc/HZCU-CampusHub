package cn.edu.hzcu.campushub.author;

import cn.edu.hzcu.campushub.article.ArticleModels.Category;
import cn.edu.hzcu.campushub.coin.HonorRules.Honor;
import com.fasterxml.jackson.annotation.JsonInclude;

public final class AuthorModels {
    private AuthorModels(){}
    public record Entry(long authorId, String nickname,
                        @JsonInclude(JsonInclude.Include.NON_NULL) Honor honor) {}
    public record Home(long authorId, String nickname, Category major, String currentStage, String bio,
                       String campusCertification, long publicArticleCount,
                       @JsonInclude(JsonInclude.Include.NON_NULL) Honor honor) {}
    public record Contact(long revision,boolean enabled,String type,String value){
        @Override public String toString(){return "Contact[redacted]";}
    }
}
