package cn.edu.hzcu.campushub.author;

import cn.edu.hzcu.campushub.article.ArticleModels.Category;

public final class AuthorModels {
    private AuthorModels(){}
    public record Entry(long authorId,String nickname){}
    public record Home(long authorId,String nickname,Category major,String currentStage,String bio,String campusCertification,long publicArticleCount){}
    public record Contact(long revision,boolean enabled,String type,String value){
        @Override public String toString(){return "Contact[redacted]";}
    }
}
