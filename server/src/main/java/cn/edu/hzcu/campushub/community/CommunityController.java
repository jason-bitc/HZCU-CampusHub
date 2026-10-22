package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.article.ArticleQuery;
import cn.edu.hzcu.campushub.auth.*;
import cn.edu.hzcu.campushub.authoring.*;
import cn.edu.hzcu.campushub.coin.CoinInput;
import cn.edu.hzcu.campushub.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class CommunityController {
    private final CommunityService service;private final AdminAuthService auth;private final CommunityInput input;
    public CommunityController(CommunityService service,AdminAuthService auth,CommunityInput input){this.service=service;this.auth=auth;this.input=input;}
    private static void params(MultiValueMap<String,String> p){if(!p.isEmpty())throw ApiException.invalid();}
    private static void empty(MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{params(p);if(r.getInputStream().read()!=-1)throw ApiException.invalid();}
    private static ResponseEntity<byte[]> binary(WritingService.BinaryImage b){return ResponseEntity.ok().contentType(MediaType.parseMediaType(b.mime())).contentLength(b.bytes().length).header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline").body(b.bytes());}
    @PostMapping(value="/me/cases",consumes="application/json") public ApiResponse<CommunityModels.Receipt> submit(@RequestAttribute("campushub.identity") AuthModels.Identity me,@RequestParam MultiValueMap<String,String> p,@RequestBody String raw){params(p);return ApiResponse.success(service.submit(me.me().userId(),input.submission(raw)));}
    @GetMapping("/me/cases") public ApiResponse<CommunityModels.Page> ownCases(@RequestAttribute("campushub.identity") AuthModels.Identity me,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{if(r.getInputStream().read()!=-1)throw ApiException.invalid();return ApiResponse.success(service.page(me.me().userId(),false,CommunityPage.parse(p,false)));}
    @GetMapping("/me/cases/{id}") public ApiResponse<Map<String,Object>> ownDetail(@RequestAttribute("campushub.identity") AuthModels.Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{empty(p,r);return ApiResponse.success(service.detail(me.me().userId(),ArticleQuery.requiredId(id),false));}
    @PostMapping(value="/me/case-images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ApiResponse<Map<String,Object>> upload(@RequestAttribute("campushub.identity") AuthModels.Identity me,@RequestParam String requestId,@RequestPart("file") MultipartFile file,HttpServletRequest r)throws Exception{if(!r.getParameterMap().keySet().equals(Set.of("requestId"))||r.getParameterValues("requestId").length!=1||r.getParts().size()!=2)throw ApiException.invalid();return ApiResponse.success(service.upload(me.me().userId(),WritingInput.requestId(requestId),file));}
    @GetMapping("/media/case-images/{id}") public ResponseEntity<byte[]> image(@RequestAttribute("campushub.identity") AuthModels.Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{empty(p,r);return binary(service.image(me.me().userId(),id));}
    @PostMapping(value="/admin/auth/login",consumes="application/json") public ApiResponse<AdminAuthService.Login> login(@RequestParam MultiValueMap<String,String> p,@RequestBody String raw){params(p);var n=CoinInput.parse(raw,"login","password");return ApiResponse.success(auth.login(WritingInput.text(n,"login",64),WritingInput.text(n,"password",64)));}
    @PostMapping("/admin/auth/logout") public ApiResponse<Map<String,Boolean>> logout(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{empty(p,r);auth.logout(me);return ApiResponse.success(Map.of("loggedOut",true));}
    @GetMapping("/admin/me") public ApiResponse<Map<String,Object>> me(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{empty(p,r);return ApiResponse.success(Map.of("adminId",me.adminId(),"actorDomain","COMMUNITY_ADMIN","capabilities",List.of("CASE_READ","CASE_HANDLE")));}
    @GetMapping("/admin/cases") public ApiResponse<CommunityModels.Page> cases(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{if(r.getInputStream().read()!=-1)throw ApiException.invalid();return ApiResponse.success(service.page(me.adminId(),true,CommunityPage.parse(p,true)));}
    @GetMapping("/admin/cases/{id}") public ApiResponse<Map<String,Object>> detail(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{empty(p,r);return ApiResponse.success(service.detail(me.adminId(),ArticleQuery.requiredId(id),true));}
    @GetMapping("/admin/cases/{id}/context") public ApiResponse<Map<String,Object>> context(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{empty(p,r);return ApiResponse.success(service.context(me.adminId(),ArticleQuery.requiredId(id)));}
    @GetMapping("/admin/cases/{id}/media/{image}") public ResponseEntity<byte[]> media(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String id,@PathVariable String image,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{empty(p,r);return binary(service.adminImage(me.adminId(),ArticleQuery.requiredId(id),image));}
    @PostMapping(value="/admin/cases/{id}/claim",consumes="application/json") public ApiResponse<CommunityModels.Receipt> claim(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p,@RequestBody String raw){params(p);return ApiResponse.success(service.manage(me.adminId(),ArticleQuery.requiredId(id),input.command("CASE_CLAIM",raw)));}
    @PostMapping(value="/admin/cases/{id}/release",consumes="application/json") public ApiResponse<CommunityModels.Receipt> release(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p,@RequestBody String raw){params(p);return ApiResponse.success(service.manage(me.adminId(),ArticleQuery.requiredId(id),input.command("CASE_RELEASE",raw)));}
    @PostMapping(value="/admin/cases/{id}/decisions",consumes="application/json") public ApiResponse<CommunityModels.Receipt> decision(@RequestAttribute("campushub.admin") AdminAuthService.Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p,@RequestBody String raw){params(p);return ApiResponse.success(service.manage(me.adminId(),ArticleQuery.requiredId(id),input.command("CASE_DECISION",raw)));}
}
