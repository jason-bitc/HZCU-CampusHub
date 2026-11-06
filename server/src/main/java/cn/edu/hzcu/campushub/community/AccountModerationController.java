package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.auth.AuthModels;
import cn.edu.hzcu.campushub.article.ArticleQuery;
import cn.edu.hzcu.campushub.authoring.WritingInput;
import cn.edu.hzcu.campushub.coin.CoinInput;
import cn.edu.hzcu.campushub.common.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountModerationController {
    private final AccountModerationService service;
    public AccountModerationController(AccountModerationService service){this.service=service;}
    private static String reason(tools.jackson.databind.JsonNode n,String key){String value=WritingInput.text(n,key,2000);if(value.isBlank())throw ApiException.invalid();return value;}
    private static void empty(Map<String,String> p){if(!p.isEmpty())throw ApiException.invalid();}
    @GetMapping("/admin/users") public ApiResponse<?> users(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@RequestParam org.springframework.util.MultiValueMap<String,String> p){var page=CommunityPage.parse(p,false);if(p.keySet().stream().anyMatch(k->!Set.of("page","size").contains(k)))throw ApiException.invalid();return ApiResponse.success(service.users(me.adminId(),page.page(),page.size()));}
    @GetMapping("/admin/users/{id}/restrictions") public ApiResponse<?> restrictions(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String id,@RequestParam Map<String,String> p){empty(p);return ApiResponse.success(Map.of("items",service.restrictions(ArticleQuery.requiredId(id))));}
    @GetMapping("/me/restrictions") public ApiResponse<?> own(@RequestAttribute("campushub.identity") AuthModels.Identity me,@RequestParam Map<String,String> p){empty(p);return ApiResponse.success(Map.of("items",service.restrictions(me.me().userId())));}
    @PostMapping(value="/admin/users/{id}/restrictions",consumes="application/json") public ApiResponse<?> set(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String id,@RequestParam Map<String,String> p,@RequestBody String raw){empty(p);var n=CoinInput.parse(raw,"scope","enabled","expectedRevision","requestId","reason");if(!n.path("enabled").isBoolean())throw ApiException.invalid();return ApiResponse.success(service.set(me.adminId(),ArticleQuery.requiredId(id),WritingInput.text(n,"scope",16),n.path("enabled").asBoolean(),WritingInput.number(n.path("expectedRevision")),CoinInput.key(n),reason(n,"reason")));}
    @PostMapping(value="/me/appeals",consumes="application/json") public ApiResponse<?> appeal(@RequestAttribute("campushub.identity") AuthModels.Identity me,@RequestParam Map<String,String> p,@RequestBody String raw){empty(p);var n=CoinInput.parse(raw,"restrictionId","requestId","reason");long id=WritingInput.number(n.path("restrictionId"));if(id<1)throw ApiException.invalid();return ApiResponse.success(service.appeal(me.me().userId(),id,CoinInput.key(n),reason(n,"reason")));}
    @GetMapping("/me/appeals") public ApiResponse<?> appeals(@RequestAttribute("campushub.identity") AuthModels.Identity me,@RequestParam org.springframework.util.MultiValueMap<String,String> p){var page=CommunityPage.parse(p,false);if(p.keySet().stream().anyMatch(k->!Set.of("page","size").contains(k)))throw ApiException.invalid();return ApiResponse.success(service.appeals(me.me().userId(),page.page(),page.size()));}
    @GetMapping("/admin/appeals") public ApiResponse<?> queue(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@RequestParam org.springframework.util.MultiValueMap<String,String> p){var page=CommunityPage.parse(p,false);if(p.keySet().stream().anyMatch(k->!Set.of("page","size").contains(k)))throw ApiException.invalid();return ApiResponse.success(service.appeals(null,page.page(),page.size()));}
    @PostMapping(value="/admin/appeals/{id}/decisions",consumes="application/json") public ApiResponse<?> decide(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String id,@RequestParam Map<String,String> p,@RequestBody String raw){empty(p);var n=CoinInput.parse(raw,"outcome","expectedRevision","requestId","reply");return ApiResponse.success(service.decide(me.adminId(),ArticleQuery.requiredId(id),WritingInput.number(n.path("expectedRevision")),WritingInput.text(n,"outcome",16),CoinInput.key(n),reason(n,"reply")));}
}
