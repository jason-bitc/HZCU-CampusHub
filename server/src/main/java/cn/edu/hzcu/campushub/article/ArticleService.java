package cn.edu.hzcu.campushub.article;

import static cn.edu.hzcu.campushub.article.ArticleModels.*;
import cn.edu.hzcu.campushub.authoring.WritingRepository;
import cn.edu.hzcu.campushub.common.ApiException;
import cn.edu.hzcu.campushub.common.RequestTrace;
import static cn.edu.hzcu.campushub.common.RequestTrace.stage;
import static cn.edu.hzcu.campushub.common.RequestTrace.Stage.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class ArticleService {
    private final ArticleRepository repository;
    private final WritingRepository profiles;
    public ArticleService(ArticleRepository repository, WritingRepository profiles) {
        this.repository = repository;
        this.profiles = profiles;
    }
    public Catalog catalog() { stage(READ);return repository.catalog(); }
    public Page list(ArticleQuery query) { stage(READ);return repository.list(query); }
    public Page listSameMajor(ArticleQuery query, long reader) {
        stage(READ);
        long major = profiles.enabledMajorOf(reader).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "SAME_MAJOR_UNAVAILABLE",
                        "请先完善个人资料中的专业，或手动选择专业筛选"));
        return repository.list(new ArticleQuery(query.keyword(), query.jobId(), major,
                query.companyId(), query.topicId(), query.sort(), query.page(), query.size()));
    }
    public Detail detail(long id) { stage(TARGET);var detail=repository.detail(id).orElseThrow(ApiException::notFound);RequestTrace.article(detail.id());return detail; }
}
