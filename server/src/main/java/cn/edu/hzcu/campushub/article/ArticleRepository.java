package cn.edu.hzcu.campushub.article;

import static cn.edu.hzcu.campushub.article.ArticleModels.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ArticleRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String FROM = " FROM article a JOIN article_version v ON v.id=a.public_version_id AND v.article_id=a.id ";
    private static final String PUBLIC = " a.state='PUBLISHED' AND v.published_at IS NOT NULL ";
    private static final String COLUMNS = "a.id,v.id version_id,v.title,v.summary,v.experience_stage,v.source_type,v.published_at,v.updated_at,a.effective_coins,a.effective_likes,a.count_origin";
    public ArticleRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Catalog catalog() {
        var groups = new HashMap<String, List<Category>>();
        jdbc.query("SELECT id,kind,name FROM category WHERE enabled=TRUE ORDER BY kind,display_order,id", rs -> {
            groups.computeIfAbsent(rs.getString("kind"), k -> new ArrayList<>()).add(new Category(rs.getLong("id"), rs.getString("name")));
        });
        return new Catalog(group(groups, "JOB"), group(groups, "MAJOR"), group(groups, "COMPANY"), group(groups, "TOPIC"));
    }

    public Page list(ArticleQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(PUBLIC);
        if (query.keyword() != null && !query.keyword().isBlank()) {
            where.append(" AND (v.title LIKE :keyword ESCAPE '!' OR v.summary LIKE :keyword ESCAPE '!')");
            String escaped = query.keyword().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            params.addValue("keyword", "%" + escaped + "%");
        }
        filter(where, params, "JOB", "jobId", query.jobId());
        filter(where, params, "MAJOR", "majorId", query.majorId());
        filter(where, params, "COMPANY", "companyId", query.companyId());
        filter(where, params, "TOPIC", "topicId", query.topicId());
        long total = jdbc.queryForObject("SELECT COUNT(*)" + FROM + "WHERE " + where, params, Long.class);
        String order = query.sort().equals("latest") ? "v.published_at DESC,a.id DESC"
                : "a.effective_coins DESC,a.effective_likes DESC,v.published_at DESC,a.id DESC";
        params.addValue("limit", query.size()).addValue("offset", query.offset());
        List<Row> rows = jdbc.query("SELECT " + COLUMNS + FROM + "WHERE " + where + " ORDER BY " + order
                + " LIMIT :limit OFFSET :offset", params, (rs, n) -> row(rs));
        var categories = categories(rows.stream().map(Row::versionId).toList());
        return new Page(rows.stream().map(r -> r.summary(categories.get(r.versionId()))).toList(), total, query.page(), query.size());
    }

    public Optional<Detail> detail(long id) {
        var params = new MapSqlParameterSource("id", id);
        return jdbc.query("SELECT " + COLUMNS + ",v.body,v.body_format,v.source_note" + FROM
                + "WHERE " + PUBLIC + " AND a.id=:id", params, (rs, n) -> {
            Row r = row(rs);
            var links = jdbc.query("SELECT label,url FROM article_source_link WHERE version_id=:version ORDER BY display_order,id",
                    new MapSqlParameterSource("version", r.versionId()), (link, x) -> new SourceLink(link.getString("label"), link.getString("url")));
            return new Detail(r.id(), r.title(), r.textSummary(), categories(List.of(r.versionId())).get(r.versionId()),
                    r.stage(), r.sourceType(), r.publishedAt(), r.updatedAt(), r.counts(), rs.getString("body"), rs.getString("body_format"), rs.getString("source_note"), links);
        }).stream().findFirst();
    }

    private static void filter(StringBuilder where, MapSqlParameterSource params, String kind, String key, Long value) {
        if (value == null) return;
        where.append(" AND EXISTS (SELECT 1 FROM article_version_category vc JOIN category c ON c.id=vc.category_id"
                + " WHERE vc.version_id=v.id AND c.kind=:").append(key).append("Kind AND c.id=:").append(key).append(")");
        params.addValue(key, value).addValue(key + "Kind", kind);
    }

    private Map<Long, Categories> categories(List<Long> versions) {
        var result = new HashMap<Long, Categories>();
        if (versions.isEmpty()) return result;
        var groups = new HashMap<Long, Map<String, List<Category>>>();
        jdbc.query("SELECT vc.version_id,c.id,c.kind,c.name FROM article_version_category vc JOIN category c ON c.id=vc.category_id"
                + " WHERE vc.version_id IN (:versions) ORDER BY c.kind,c.display_order,c.id", new MapSqlParameterSource("versions", versions), rs -> {
            groups.computeIfAbsent(rs.getLong("version_id"), k -> new HashMap<>())
                    .computeIfAbsent(rs.getString("kind"), k -> new ArrayList<>()).add(new Category(rs.getLong("id"), rs.getString("name")));
        });
        for (long version : versions) {
            var group = groups.getOrDefault(version, Map.of());
            result.put(version, new Categories(group(group, "JOB"), group(group, "MAJOR"), group(group, "COMPANY"), group(group, "TOPIC")));
        }
        return result;
    }
    private static List<Category> group(Map<String, List<Category>> groups, String kind) { return List.copyOf(groups.getOrDefault(kind, List.of())); }
    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getLong("id"), rs.getLong("version_id"), rs.getString("title"), rs.getString("summary"),
                rs.getString("experience_stage"), rs.getString("source_type"), rs.getTimestamp("published_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),new Counts(rs.getLong("effective_coins"), rs.getLong("effective_likes"), rs.getString("count_origin")));
    }
    private record Row(long id, long versionId, String title, String textSummary, String stage, String sourceType,
            java.time.Instant publishedAt, java.time.Instant updatedAt, Counts counts) {
        Summary summary(Categories categories) { return new Summary(id, title, textSummary, categories, stage, sourceType, publishedAt, updatedAt, counts); }
    }
}
