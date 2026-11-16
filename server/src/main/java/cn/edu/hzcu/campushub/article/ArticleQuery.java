package cn.edu.hzcu.campushub.article;

import cn.edu.hzcu.campushub.common.ApiException;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public record ArticleQuery(String keyword, Long jobId, Long majorId, Long companyId, Long topicId,
        String sort, int page, int size) {
    public static ArticleQuery parse(MultiValueMap<String, String> values) {
        Set<String> allowed = Set.of("keyword", "jobId", "majorId", "companyId", "topicId", "sort", "page", "size");
        if (values.entrySet().stream().anyMatch(e -> !allowed.contains(e.getKey()) || e.getValue().size() != 1))
            throw ApiException.invalid();
        String keyword = values.getFirst("keyword");
        if (keyword != null) {
            keyword = keyword.strip();
            if (keyword.length() > 100 || keyword.codePoints().anyMatch(Character::isISOControl)) throw ApiException.invalid();
        }
        String sort = values.getFirst("sort");
        if (sort == null) sort = "recognized";
        if (!Set.of("recognized", "latest").contains(sort)) throw ApiException.invalid();
        int page = integer(values.getFirst("page"), 1, 1, 1_000_000);
        int size = integer(values.getFirst("size"), 20, 1, 100);
        return new ArticleQuery(keyword, identifier(values.getFirst("jobId")), identifier(values.getFirst("majorId")),
                identifier(values.getFirst("companyId")), identifier(values.getFirst("topicId")), sort, page, size);
    }
    public static long requiredId(String raw) {
        Long id = identifier(raw);
        if (id == null) throw ApiException.invalid();
        return id;
    }
    private static Long identifier(String raw) {
        if (raw == null) return null;
        try {
            if (!raw.matches("[0-9]{1,19}")) throw ApiException.invalid();
            long result = Long.parseLong(raw);
            if (result <= 0) throw ApiException.invalid();
            return result;
        } catch (NumberFormatException e) { throw ApiException.invalid(); }
    }
    private static int integer(String raw, int fallback, int min, int max) {
        if (raw == null) return fallback;
        long value = requiredId(raw);
        if (value < min || value > max) throw ApiException.invalid();
        return (int) value;
    }
    public long offset() { return ((long) page - 1) * size; }
}
