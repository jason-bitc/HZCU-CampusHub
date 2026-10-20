package cn.edu.hzcu.campushub.article;

import static cn.edu.hzcu.campushub.article.ArticleModels.*;
import cn.edu.hzcu.campushub.common.ApiException;
import cn.edu.hzcu.campushub.common.RequestTrace;
import static cn.edu.hzcu.campushub.common.RequestTrace.stage;
import static cn.edu.hzcu.campushub.common.RequestTrace.Stage.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class ArticleService {
    private final ArticleRepository repository;
    public ArticleService(ArticleRepository repository) { this.repository = repository; }
    public Catalog catalog() { stage(READ);return repository.catalog(); }
    public Page list(ArticleQuery query) { stage(READ);return repository.list(query); }
    public Detail detail(long id) { stage(TARGET);var detail=repository.detail(id).orElseThrow(ApiException::notFound);RequestTrace.article(detail.id());return detail; }
}
