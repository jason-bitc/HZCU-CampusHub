package cn.edu.hzcu.campushub.authoring;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import cn.edu.hzcu.campushub.article.ArticleModels.SourceLink;

public final class WritingModels {
    private WritingModels() {}
    public record Profile(long revision,String nickname,Long majorId,String currentStage,String bio) {}
    public record Tags(List<Long> jobIds,List<Long> majorIds,List<Long> companyIds,List<Long> topicIds) {}
    public record Content(String title,String summary,String body,Tags tags,String experienceStage,String sourceType,String sourceNote,List<SourceLink> sourceLinks) {}
    public record Draft(long articleId,String state,long revision,Content content,List<Image> images,List<String> referencedImageIds) {}
    public enum ArticleState { DRAFT, PUBLISHED, AUTHOR_OFFLINE, ADMIN_HIDDEN, DELETED }
    public enum WorkspaceAction {
        VIEW_DRAFT, EDIT_DRAFT, VIEW_PUBLIC, PUBLISH, UNPUBLISH, RESTORE, DELETE;

        static List<WorkspaceAction> allowed(ArticleState state, boolean hasPublicVersion, boolean canPublish) {
            if (state == ArticleState.DELETED) return List.of();
            var actions = new ArrayList<>(List.of(VIEW_DRAFT, DELETE));
            if (state == ArticleState.ADMIN_HIDDEN) return List.copyOf(actions);
            actions.add(EDIT_DRAFT);
            if (canPublish) actions.add(PUBLISH);
            if (state == ArticleState.PUBLISHED) {
                if (hasPublicVersion) actions.add(VIEW_PUBLIC);
                actions.add(UNPUBLISH);
            }
            if (state == ArticleState.AUTHOR_OFFLINE && hasPublicVersion && canPublish) actions.add(RESTORE);
            return List.copyOf(actions);
        }
    }
    public record OwnedArticle(long articleId, ArticleState state, long revision, String title, Instant updatedAt,
                               List<WorkspaceAction> allowedActions) {}
    public record OwnedPage(List<OwnedArticle> items, long total, int page, int size, boolean publishingRestricted) {}
    public record Created(long articleId) {}
    public record Published(long articleId,long versionId,long revision,Instant publishedAt,Instant updatedAt) {}
    public record StateResult(long articleId,String state,long revision) {}
    public record Image(String imageId,String url,String mimeType,long sizeBytes,int width,int height) {}
    record Article(long id,String state,long revision,Instant firstPublishedAt) {}
    record ImageRow(String id,long articleId,Long owner,String state,String digest,String mime,long size,int width,int height,boolean visible) {
        Image view(){return new Image(id,"/api/v1/media/images/"+id,mime,size,width,height);}
    }
}
