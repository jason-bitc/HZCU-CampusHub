package cn.edu.hzcu.campushub.article;

import java.time.Instant;
import java.util.List;

public final class ArticleModels {
    private ArticleModels() {}
    public record Category(long id, String name) {}
    public record Catalog(List<Category> jobs, List<Category> majors, List<Category> companies, List<Category> topics) {}
    public record Categories(List<Category> jobs, List<Category> majors, List<Category> companies, List<Category> topics) {}
    public record Counts(long effectiveCoins, long effectiveLikes, String origin) {}
    public record Summary(long id, String title, String summary, Categories categories, String experienceStage,
            String sourceType, Instant publishedAt, Counts counts) {}
    public record SourceLink(String label, String url) {}
    public record Detail(long id, String title, String summary, Categories categories, String experienceStage,
            String sourceType, Instant publishedAt, Counts counts, String body, String bodyFormat,
            String sourceNote, List<SourceLink> sourceLinks) {}
    public record Page(List<Summary> items, long total, int page, int size) {}
}
