package cn.edu.hzcu.campushub.comment;

import cn.edu.hzcu.campushub.article.ArticleQuery;
import cn.edu.hzcu.campushub.auth.AuthModels.Identity;
import cn.edu.hzcu.campushub.authoring.*;
import cn.edu.hzcu.campushub.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class CommentController {
    private final CommentService service;
    private final CommentInput input;
    public CommentController(CommentService service,CommentInput input){this.service=service;this.input=input;}
    private static void empty(MultiValueMap<String,String> params){if(!params.isEmpty())throw ApiException.invalid();}
    private static ArticleQuery page(MultiValueMap<String,String> params){if(params.keySet().stream().anyMatch(k->!Set.of("page","size").contains(k)))throw ApiException.invalid();return ArticleQuery.parse(params);}
    @GetMapping("/articles/{id}/comments") public ApiResponse<CommentModels.Page> roots(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> params){var query=page(params);return ApiResponse.success(service.roots(me.me().userId(),ArticleQuery.requiredId(id),query.page(),query.size()));}
    @GetMapping("/comments/{id}/replies") public ApiResponse<CommentModels.Replies> replies(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> params){var query=page(params);return ApiResponse.success(service.replies(me.me().userId(),ArticleQuery.requiredId(id),query.page(),query.size()));}
    @PostMapping("/articles/{id}/comments") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentModels.Receipt> create(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> params,@RequestBody JsonNode body){empty(params);return ApiResponse.success(service.create(me.me().userId(),ArticleQuery.requiredId(id),input.parse(body)));}
    @DeleteMapping("/comments/{id}") public ApiResponse<CommentModels.Deleted> delete(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> params){empty(params);return ApiResponse.success(service.delete(me.me().userId(),ArticleQuery.requiredId(id)));}
    @PostMapping(value="/articles/{id}/comment-images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<WritingModels.Image> upload(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam String requestId,@RequestPart("file") MultipartFile file,HttpServletRequest request)throws Exception{
        if(!request.getParameterMap().keySet().equals(Set.of("requestId"))||request.getParameterValues("requestId").length!=1||request.getParts().size()!=2)throw ApiException.invalid();
        return ApiResponse.success(service.upload(me.me().userId(),ArticleQuery.requiredId(id),WritingInput.requestId(requestId),file));
    }
    @GetMapping("/media/comment-images/{id}") public ResponseEntity<byte[]> image(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> params){
        empty(params);var image=service.image(me.me().userId(),id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mime())).contentLength(image.bytes().length).header("Cache-Control","no-store, max-age=0").header("Pragma","no-cache").header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline").body(image.bytes());
    }
}
