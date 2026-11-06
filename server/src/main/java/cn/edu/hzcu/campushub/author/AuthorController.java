package cn.edu.hzcu.campushub.author;

import cn.edu.hzcu.campushub.article.*;
import cn.edu.hzcu.campushub.auth.AuthModels.Identity;
import cn.edu.hzcu.campushub.common.*;
import java.util.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class AuthorController {
    private final AuthorService service;
    public AuthorController(AuthorService service){this.service=service;}
    private static void empty(MultiValueMap<String,String> params){if(!params.isEmpty())throw ApiException.invalid();}
    @GetMapping("/articles/{id}/author") public ApiResponse<AuthorModels.Entry> entry(@PathVariable String id,@RequestParam MultiValueMap<String,String> params){empty(params);return ApiResponse.success(service.entry(ArticleQuery.requiredId(id)));}
    @GetMapping("/authors/{id}") public ApiResponse<AuthorModels.Home> home(@PathVariable String id,@RequestParam MultiValueMap<String,String> params){empty(params);return ApiResponse.success(service.home(ArticleQuery.requiredId(id)));}
    @GetMapping("/authors/{id}/articles") public ApiResponse<ArticleModels.Page> articles(@PathVariable String id,@RequestParam MultiValueMap<String,String> params){
        if(params.keySet().stream().anyMatch(k->!Set.of("page","size").contains(k)))throw ApiException.invalid();var query=ArticleQuery.parse(params);
        return ApiResponse.success(service.articles(ArticleQuery.requiredId(id),query.page(),query.size()));
    }
    @GetMapping("/authors/{id}/contact") public ApiResponse<Map<String,Object>> contact(@PathVariable String id,@RequestParam MultiValueMap<String,String> params){empty(params);return ApiResponse.success(service.contact(ArticleQuery.requiredId(id)));}
    @GetMapping("/me/contact-settings") public ApiResponse<AuthorModels.Contact> own(@RequestAttribute("campushub.identity") Identity identity,@RequestParam MultiValueMap<String,String> params){empty(params);return ApiResponse.success(service.ownContact(identity.me().userId()));}
    @PutMapping("/me/contact-settings") public ApiResponse<AuthorModels.Contact> update(@RequestAttribute("campushub.identity") Identity identity,@RequestParam MultiValueMap<String,String> params,@RequestBody JsonNode body){empty(params);return ApiResponse.success(service.updateContact(identity.me().userId(),ContactInput.parse(body)));}
}
