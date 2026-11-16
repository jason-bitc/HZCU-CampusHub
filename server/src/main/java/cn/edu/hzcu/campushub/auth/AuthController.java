package cn.edu.hzcu.campushub.auth;

import static cn.edu.hzcu.campushub.auth.AuthInput.*;
import static cn.edu.hzcu.campushub.auth.AuthModels.*;
import cn.edu.hzcu.campushub.common.ApiResponse;
import cn.edu.hzcu.campushub.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service){this.service=service;}
    @PostMapping("/auth/registration-codes") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CodeRequest> registrationCodes(@RequestBody JsonNode body){fields(body,"email");return ApiResponse.success(service.requestCode(value(body,"email"),Purpose.REGISTER));}
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Me> register(@RequestBody JsonNode body){fields(body,"email","password","challengeId","code");return ApiResponse.success(service.register(value(body,"email"),value(body,"password"),value(body,"challengeId"),value(body,"code")));}
    @PostMapping("/auth/login")
    public ApiResponse<Login> login(@RequestBody JsonNode body){fields(body,"email","password");return ApiResponse.success(service.login(value(body,"email"),value(body,"password")));}
    @PostMapping("/auth/logout")
    public ApiResponse<Map<String,Boolean>> logout(@RequestAttribute("campushub.identity") Identity identity){service.logout(identity);return ApiResponse.success(Map.of("loggedOut",true));}
    @GetMapping("/me")
    public ApiResponse<Me> me(@RequestAttribute("campushub.identity") Identity identity){return ApiResponse.success(service.me(identity));}
    @GetMapping("/me/certification")
    public ApiResponse<CertificationStatus> certification(@RequestAttribute("campushub.identity") Identity identity,
            HttpServletRequest request) throws IOException {
        requireNoParameters(request);
        requireNoBody(request);
        return ApiResponse.success(service.certification(identity));
    }
    @PostMapping("/me/certification/reverification-codes") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CodeRequest> reverificationCode(@RequestAttribute("campushub.identity") Identity identity,
            HttpServletRequest request) throws IOException {
        requireNoParameters(request);
        requireNoBody(request);
        return ApiResponse.success(service.requestReverification(identity));
    }
    @PostMapping(value="/me/certification/reverification", consumes="application/json")
    public ApiResponse<Map<String, Boolean>> reverify(@RequestAttribute("campushub.identity") Identity identity,
            @RequestBody String raw, HttpServletRequest request) {
        requireNoParameters(request);
        var body = cn.edu.hzcu.campushub.coin.CoinInput.parse(raw, "challengeId", "code");
        fields(body, "challengeId", "code");
        service.reverify(identity, value(body, "challengeId"), value(body, "code"));
        return ApiResponse.success(Map.of("reverified", true, "signInRequired", true));
    }
    private static void requireNoParameters(HttpServletRequest request) {
        if (request.getQueryString() != null) throw ApiException.invalid();
    }
    private static void requireNoBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1) throw ApiException.invalid();
    }
    @PostMapping("/auth/password-reset-codes") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CodeRequest> resetCodes(@RequestBody JsonNode body){fields(body,"email");return ApiResponse.success(service.requestCode(value(body,"email"),Purpose.PASSWORD_RESET));}
    @PostMapping("/auth/password-resets")
    public ApiResponse<Map<String,Boolean>> reset(@RequestBody JsonNode body){fields(body,"email","password","challengeId","code");service.resetPassword(value(body,"email"),value(body,"password"),value(body,"challengeId"),value(body,"code"));return ApiResponse.success(Map.of("passwordReset",true));}
}
