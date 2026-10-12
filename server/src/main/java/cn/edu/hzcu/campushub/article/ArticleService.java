package cn.edu.hzcu.campushub.article;

import static cn.edu.hzcu.campushub.article.ArticleModels.*;
import cn.edu.hzcu.campushub.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class ArticleService {
    private final ArticleRepository repository;
    public ArticleService(ArticleRepository repository) { this.repository = repository; }
    public Catalog catalog() { return repository.catalog(); }
    public Page list(ArticleQuery query) { return repository.list(query); }
    public Detail detail(long id) { return repository.detail(id).orElseThrow(ApiException::notFound); }
}
