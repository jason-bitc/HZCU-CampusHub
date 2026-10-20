package cn.edu.hzcu.campushub.interaction;

import cn.edu.hzcu.campushub.article.ArticleQuery;
import cn.edu.hzcu.campushub.auth.AuthModels.Identity;
import cn.edu.hzcu.campushub.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class InteractionController {
    private final InteractionService service;
    public InteractionController(InteractionService service){this.service=service;}
    private static void empty(MultiValueMap<String,String> params,HttpServletRequest request)throws IOException{if(!params.isEmpty()||request.getInputStream().read()!=-1)throw ApiException.invalid();}
    @PutMapping("/articles/{id}/like") public ApiResponse<InteractionModels.Like> like(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> params,HttpServletRequest request)throws IOException{empty(params,request);return ApiResponse.success(service.like(me.me().userId(),ArticleQuery.requiredId(id),true));}
    @DeleteMapping("/articles/{id}/like") public ApiResponse<InteractionModels.Like> unlike(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> params,HttpServletRequest request)throws IOException{empty(params,request);return ApiResponse.success(service.like(me.me().userId(),ArticleQuery.requiredId(id),false));}
    @PutMapping("/articles/{id}/favorite") public ApiResponse<InteractionModels.Favorite> favorite(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> params,HttpServletRequest request)throws IOException{empty(params,request);return ApiResponse.success(service.favorite(me.me().userId(),ArticleQuery.requiredId(id),true));}
    @DeleteMapping("/articles/{id}/favorite") public ApiResponse<InteractionModels.Favorite> unfavorite(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> params,HttpServletRequest request)throws IOException{empty(params,request);return ApiResponse.success(service.favorite(me.me().userId(),ArticleQuery.requiredId(id),false));}
    @GetMapping("/articles/{id}/interaction-state") public ApiResponse<InteractionModels.State> state(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> params,HttpServletRequest request)throws IOException{empty(params,request);return ApiResponse.success(service.state(me.me().userId(),ArticleQuery.requiredId(id)));}
    @GetMapping("/me/favorites") public ApiResponse<InteractionModels.Favorites> favorites(@RequestAttribute("campushub.identity") Identity me,@RequestParam MultiValueMap<String,String> params,HttpServletRequest request)throws IOException{if(params.keySet().stream().anyMatch(k->!Set.of("page","size").contains(k))||request.getInputStream().read()!=-1)throw ApiException.invalid();var page=ArticleQuery.parse(params);return ApiResponse.success(service.favorites(me.me().userId(),page.page(),page.size()));}
}
