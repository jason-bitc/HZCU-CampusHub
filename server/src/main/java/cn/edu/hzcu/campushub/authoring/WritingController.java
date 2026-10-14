package cn.edu.hzcu.campushub.authoring;

import static cn.edu.hzcu.campushub.authoring.WritingModels.*;
import static cn.edu.hzcu.campushub.authoring.WritingInput.*;
import cn.edu.hzcu.campushub.article.ArticleQuery;
import cn.edu.hzcu.campushub.auth.AuthModels.Identity;
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
public class WritingController {
    private final WritingService service;
    public WritingController(WritingService service){this.service=service;}
    @GetMapping("/me/profile") public ApiResponse<Profile> profile(@RequestAttribute("campushub.identity") Identity identity){return ApiResponse.success(service.profile(identity.me().userId()));}
    @PutMapping("/me/profile") public ApiResponse<Profile> profile(@RequestAttribute("campushub.identity") Identity identity,@RequestBody JsonNode body){return ApiResponse.success(service.updateProfile(identity.me().userId(),WritingInput.profile(body)));}
    @PostMapping("/me/articles") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Created> create(@RequestAttribute("campushub.identity") Identity identity,@RequestBody JsonNode body){fields(body,"requestId");return ApiResponse.success(service.create(identity.me().userId(),requestId(body)));}
    @GetMapping("/me/articles") public ApiResponse<OwnedPage> list(@RequestAttribute("campushub.identity") Identity identity,@RequestParam MultiValueMap<String,String> params){
        if(params.keySet().stream().anyMatch(k->!Set.of("page","size").contains(k)))throw ApiException.invalid();var q=ArticleQuery.parse(params);return ApiResponse.success(service.list(identity.me().userId(),q.page(),q.size()));
    }
    @GetMapping("/me/articles/{id}/draft") public ApiResponse<Draft> draft(@RequestAttribute("campushub.identity") Identity identity,@PathVariable String id){return ApiResponse.success(service.draft(identity.me().userId(),ArticleQuery.requiredId(id)));}
    @PutMapping("/me/articles/{id}/draft") public ApiResponse<Draft> save(@RequestAttribute("campushub.identity") Identity identity,@PathVariable String id,@RequestBody JsonNode body){return ApiResponse.success(service.save(identity.me().userId(),ArticleQuery.requiredId(id),revision(body),content(body)));}
    @PostMapping("/me/articles/{id}/publish") public ApiResponse<Published> publish(@RequestAttribute("campushub.identity") Identity identity,@PathVariable String id,@RequestBody JsonNode body){fields(body,"expectedRevision","requestId");return ApiResponse.success(service.publish(identity.me().userId(),ArticleQuery.requiredId(id),revision(body),requestId(body)));}
    @PostMapping({"/me/articles/{id}/unpublish","/me/articles/{id}/restore"})
    public ApiResponse<StateResult> state(@RequestAttribute("campushub.identity") Identity identity,@PathVariable String id,@RequestBody JsonNode body,HttpServletRequest request){fields(body,"expectedRevision");return ApiResponse.success(service.changeState(identity.me().userId(),ArticleQuery.requiredId(id),revision(body),request.getServletPath().endsWith("/restore")?"restore":"unpublish"));}
    @DeleteMapping("/me/articles/{id}") public ApiResponse<StateResult> delete(@RequestAttribute("campushub.identity") Identity identity,@PathVariable String id,@RequestParam MultiValueMap<String,String> params){
        if(!params.keySet().equals(Set.of("expectedRevision"))||params.get("expectedRevision").size()!=1)throw ApiException.invalid();String rev=params.getFirst("expectedRevision");if(rev==null||!rev.matches("[0-9]{1,18}"))throw ApiException.invalid();return ApiResponse.success(service.changeState(identity.me().userId(),ArticleQuery.requiredId(id),Long.parseLong(rev),"delete"));
    }
    @PostMapping(value="/me/articles/{id}/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Image> upload(@RequestAttribute("campushub.identity") Identity identity,@PathVariable String id,@RequestParam String requestId,@RequestPart("file") MultipartFile file,HttpServletRequest request)throws Exception{
        if(!request.getParameterMap().keySet().equals(Set.of("requestId"))||request.getParameterValues("requestId").length!=1
                ||request.getParts().size()!=2)throw ApiException.invalid();return ApiResponse.success(service.upload(identity.me().userId(),ArticleQuery.requiredId(id),WritingInput.requestId(requestId),file));
    }
    @GetMapping("/media/images/{id}") public ResponseEntity<byte[]> image(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String auth){
        var image=service.image(id,auth);return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mime())).contentLength(image.bytes().length)
                .header("Cache-Control","no-store, max-age=0").header("Pragma","no-cache").header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline").body(image.bytes());
    }
}
