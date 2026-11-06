package cn.edu.hzcu.campushub.interaction;

import java.util.*;

public final class InteractionModels {
    private InteractionModels(){}
    public record Like(long articleId,boolean liked,long effectiveLikes){}
    public record Favorite(long articleId,boolean favorited){}
    public record State(long articleId,boolean liked,boolean favorited){}
    public record Favorites(List<Map<String,Object>> items,long total,int page,int size){}
    record Relation(boolean active,long revision){}
}
