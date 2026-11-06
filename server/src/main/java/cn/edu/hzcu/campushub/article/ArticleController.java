package cn.edu.hzcu.campushub.article;

import static cn.edu.hzcu.campushub.article.ArticleModels.*;
import cn.edu.hzcu.campushub.common.ApiResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ArticleController {
    private final ArticleService service;
    public ArticleController(ArticleService service) { this.service = service; }
    @GetMapping("/catalog") public ApiResponse<Catalog> catalog() { return ApiResponse.success(service.catalog()); }
    @GetMapping("/articles") public ApiResponse<Page> list(@RequestParam MultiValueMap<String, String> params) {
        return ApiResponse.success(service.list(ArticleQuery.parse(params)));
    }
    @GetMapping("/articles/{id}") public ApiResponse<Detail> detail(@PathVariable String id) {
        return ApiResponse.success(service.detail(ArticleQuery.requiredId(id)));
    }
}
