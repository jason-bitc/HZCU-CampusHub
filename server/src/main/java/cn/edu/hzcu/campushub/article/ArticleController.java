package cn.edu.hzcu.campushub.article;

import static cn.edu.hzcu.campushub.article.ArticleModels.*;
import cn.edu.hzcu.campushub.auth.AuthModels.Identity;
import cn.edu.hzcu.campushub.auth.AuthService;
import cn.edu.hzcu.campushub.common.ApiException;
import cn.edu.hzcu.campushub.common.ApiResponse;
import java.util.List;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ArticleController {
    private final ArticleService service;
    public ArticleController(ArticleService service) { this.service = service; }
    @GetMapping("/catalog") public ApiResponse<Catalog> catalog() { return ApiResponse.success(service.catalog()); }
    @GetMapping("/articles")
    public ApiResponse<Page> list(@RequestParam MultiValueMap<String, String> params,
            @RequestAttribute(value = "campushub.identity", required = false) Identity identity) {
        var filters = new LinkedMultiValueMap<>(params);
        var sameMajor = filters.remove("sameMajor");
        var query = ArticleQuery.parse(filters);
        if (sameMajor == null || sameMajor.equals(List.of("false"))) {
            return ApiResponse.success(service.list(query));
        }
        if (!sameMajor.equals(List.of("true")) || query.majorId() != null) throw ApiException.invalid();
        if (identity == null) throw AuthService.unauthorized();
        return ApiResponse.success(service.listSameMajor(query, identity.me().userId()));
    }
    @GetMapping("/articles/{id}") public ApiResponse<Detail> detail(@PathVariable String id) {
        return ApiResponse.success(service.detail(ArticleQuery.requiredId(id)));
    }
}
