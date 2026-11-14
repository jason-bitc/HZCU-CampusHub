package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.article.ArticleQuery;
import cn.edu.hzcu.campushub.authoring.WritingInput;
import cn.edu.hzcu.campushub.coin.CoinInput;
import cn.edu.hzcu.campushub.common.*;
import java.util.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class GovernanceController {
    private final GovernanceService service;
    private final ContributionGovernanceService contributions;

    public GovernanceController(GovernanceService service, ContributionGovernanceService contributions) {
        this.service = service;
        this.contributions = contributions;
    }
    private static String param(MultiValueMap<String,String> p,String key){var v=p.get(key);if(v==null)return null;if(v.size()!=1||v.getFirst().isBlank())throw ApiException.invalid();return v.getFirst();}
    private static int num(MultiValueMap<String,String> p,String key,int fallback,int max){String v=param(p,key);if(v==null)return fallback;try{int n=Integer.parseInt(v);if(!v.equals(""+n)||n<1||n>max)throw ApiException.invalid();return n;}catch(NumberFormatException e){throw ApiException.invalid();}}
    private static void only(MultiValueMap<String,String> p,String... keys){if(!Set.of(keys).containsAll(p.keySet()))throw ApiException.invalid();}
    @GetMapping("/overview") public ApiResponse<?> overview(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@RequestParam MultiValueMap<String,String> p){only(p);return ApiResponse.success(service.overview(me.adminId()));}
    @GetMapping("/articles") public ApiResponse<?> articles(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@RequestParam MultiValueMap<String,String> p){only(p,"page","size","state");return ApiResponse.success(service.articles(me.adminId(),param(p,"state"),num(p,"page",1,1000000),num(p,"size",20,100)));}
    @GetMapping("/articles/{id}") public ApiResponse<?> article(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p){only(p);return ApiResponse.success(service.article(me.adminId(),ArticleQuery.requiredId(id)));}
    @GetMapping("/articles/{id}/comments") public ApiResponse<?> comments(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p){only(p,"page","size");return ApiResponse.success(service.comments(me.adminId(),ArticleQuery.requiredId(id),num(p,"page",1,1000000),num(p,"size",20,100)));}
    @PostMapping(value="/{collection:articles|comments}/{id}/moderation",consumes="application/json") public ApiResponse<?> moderate(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String collection,@PathVariable String id,@RequestParam MultiValueMap<String,String> p,@RequestBody String raw){
        only(p);var n=CoinInput.parse(raw,"action","expectedRevision","requestId","reason");String reason=WritingInput.text(n,"reason",2000);if(reason.isBlank())throw ApiException.invalid();
        return ApiResponse.success(service.moderate(me.adminId(),collection.equals("articles")?"ARTICLE":"COMMENT",ArticleQuery.requiredId(id),WritingInput.text(n,"action",16),WritingInput.number(n.path("expectedRevision")),CoinInput.key(n),reason));
    }
    @GetMapping("/categories") public ApiResponse<?> categories(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@RequestParam MultiValueMap<String,String> p){only(p,"page","size","kind");return ApiResponse.success(service.categories(me.adminId(),param(p,"kind"),num(p,"page",1,1000000),num(p,"size",20,100)));}
    @GetMapping("/governance-events") public ApiResponse<?> events(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@RequestParam MultiValueMap<String,String> p){only(p,"page","size");return ApiResponse.success(service.events(me.adminId(),num(p,"page",1,1000000),num(p,"size",20,100)));}
    @GetMapping("/cases/{caseId}/contributions")
    public ApiResponse<ContributionGovernanceService.Page> contributions(
            @RequestAttribute("campushub.admin") AdminAuthService.Identity admin, @PathVariable String caseId,
            @RequestParam MultiValueMap<String, String> params) {
        only(params, "page", "size");
        return ApiResponse.success(contributions.list(admin.adminId(), ArticleQuery.requiredId(caseId),
                num(params, "page", 1, 1000000), num(params, "size", 20, 100)));
    }

    @PostMapping(value = "/cases/{caseId}/contributions/{eventId}/invalidation", consumes = "application/json")
    public ApiResponse<ContributionGovernanceService.Receipt> invalidateContribution(
            @RequestAttribute("campushub.admin") AdminAuthService.Identity admin, @PathVariable String caseId,
            @PathVariable String eventId, @RequestParam MultiValueMap<String, String> params, @RequestBody String raw) {
        only(params);
        var input = CoinInput.parse(raw, "expectedRevision", "requestId", "reason");
        String reason = WritingInput.text(input, "reason", 2000).strip();
        if (reason.isBlank()) throw ApiException.invalid();
        return ApiResponse.success(contributions.invalidate(admin.adminId(), ArticleQuery.requiredId(caseId),
                WritingInput.requestId(eventId), WritingInput.number(input.path("expectedRevision")),
                CoinInput.key(input), reason));
    }
    @PostMapping(value="/categories",consumes="application/json") public ApiResponse<?> create(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@RequestParam MultiValueMap<String,String> p,@RequestBody String raw){only(p);return save(me.adminId(),null,raw);}
    @PutMapping(value="/categories/{id}",consumes="application/json") public ApiResponse<?> update(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p,@RequestBody String raw){only(p);return save(me.adminId(),ArticleQuery.requiredId(id),raw);}
    private ApiResponse<?> save(long admin,Long id,String raw){var n=CoinInput.parse(raw,"kind","name","enabled","displayOrder","expectedRevision","requestId","reason");if(!n.path("enabled").isBoolean()||!n.path("displayOrder").isInt())throw ApiException.invalid();String name=WritingInput.text(n,"name",100).strip(),reason=WritingInput.text(n,"reason",2000);if(name.isBlank()||reason.isBlank())throw ApiException.invalid();return ApiResponse.success(service.category(admin,id,WritingInput.text(n,"kind",16),name,n.path("enabled").asBoolean(),n.path("displayOrder").asInt(),WritingInput.number(n.path("expectedRevision")),CoinInput.key(n),reason));}
}
