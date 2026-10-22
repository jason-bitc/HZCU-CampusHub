package cn.edu.hzcu.campushub.coin;

import cn.edu.hzcu.campushub.article.ArticleQuery;
import cn.edu.hzcu.campushub.auth.AuthModels.Identity;
import cn.edu.hzcu.campushub.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import org.springframework.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CoinController {
    private final CoinService service;
    public CoinController(CoinService service){this.service=service;}
    private static void params(MultiValueMap<String,String> p){if(!p.isEmpty())throw ApiException.invalid();}
    private static void empty(MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{params(p);if(r.getInputStream().read()!=-1)throw ApiException.invalid();}
    @GetMapping("/me/coins") public ApiResponse<CoinModels.Balance> balance(@RequestAttribute("campushub.identity") Identity me,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{empty(p,r);return ApiResponse.success(service.balance(me.me().userId()));}
    @GetMapping("/me/rewards") public ApiResponse<CoinModels.Rewards> rewards(@RequestAttribute("campushub.identity") Identity me,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{empty(p,r);return ApiResponse.success(service.rewards(me.me().userId()));}
    @GetMapping("/me/coin-ledger") public ApiResponse<CoinModels.Ledger> ledger(@RequestAttribute("campushub.identity") Identity me,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{
        if(p.keySet().stream().anyMatch(k->!Set.of("page","size","snapshot").contains(k))||p.values().stream().anyMatch(v->v.size()!=1)||r.getInputStream().read()!=-1)throw ApiException.invalid();
        var pagination=new LinkedMultiValueMap<String,String>();for(String k:Set.of("page","size"))if(p.containsKey(k))pagination.put(k,p.get(k));var q=ArticleQuery.parse(pagination);
        Long snapshot=null;if(p.containsKey("snapshot")){String raw=p.getFirst("snapshot");try{if(!raw.matches("[0-9]{1,19}"))throw ApiException.invalid();snapshot=Long.parseLong(raw);}catch(NumberFormatException invalid){throw ApiException.invalid();}}
        return ApiResponse.success(service.ledger(me.me().userId(),q.page(),q.size(),snapshot));
    }
    @PostMapping(value="/me/rewards/login",consumes="application/json") public ApiResponse<CoinModels.Receipt> login(@RequestAttribute("campushub.identity") Identity me,@RequestParam MultiValueMap<String,String> p,@RequestBody String raw){params(p);var n=CoinInput.parse(raw,"requestId");return ApiResponse.success(service.login(me.me().userId(),CoinInput.key(n)));}
    @PostMapping("/articles/{id}/reading-session") public ApiResponse<CoinModels.Reading> start(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{empty(p,r);return ApiResponse.success(service.start(me.me().userId(),ArticleQuery.requiredId(id)));}
    @PostMapping(value="/me/rewards/reading",consumes="application/json") public ApiResponse<CoinModels.Receipt> reading(@RequestAttribute("campushub.identity") Identity me,@RequestParam MultiValueMap<String,String> p,@RequestBody String raw){params(p);var n=CoinInput.parse(raw,"sessionId","requestId");return ApiResponse.success(service.reading(me.me().userId(),CoinInput.session(n),CoinInput.key(n)));}
    @GetMapping("/articles/{id}/coin-state") public ApiResponse<CoinModels.CoinState> state(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p,HttpServletRequest r)throws IOException{empty(p,r);return ApiResponse.success(service.state(me.me().userId(),ArticleQuery.requiredId(id)));}
    @PostMapping(value="/articles/{id}/coins",consumes="application/json") public ApiResponse<CoinModels.Receipt> spend(@RequestAttribute("campushub.identity") Identity me,@PathVariable String id,@RequestParam MultiValueMap<String,String> p,@RequestBody String raw){params(p);var n=CoinInput.parse(raw,"amount","requestId");return ApiResponse.success(service.spend(me.me().userId(),ArticleQuery.requiredId(id),CoinInput.amount(n),CoinInput.key(n)));}
}
