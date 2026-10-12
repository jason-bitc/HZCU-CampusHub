package cn.edu.hzcu.campushub.auth;

import static cn.edu.hzcu.campushub.auth.AuthInput.*;
import static cn.edu.hzcu.campushub.auth.AuthModels.*;
import cn.edu.hzcu.campushub.common.ApiResponse;
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
    @PostMapping("/auth/password-reset-codes") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CodeRequest> resetCodes(@RequestBody JsonNode body){fields(body,"email");return ApiResponse.success(service.requestCode(value(body,"email"),Purpose.PASSWORD_RESET));}
    @PostMapping("/auth/password-resets")
    public ApiResponse<Map<String,Boolean>> reset(@RequestBody JsonNode body){fields(body,"email","password","challengeId","code");service.resetPassword(value(body,"email"),value(body,"password"),value(body,"challengeId"),value(body,"code"));return ApiResponse.success(Map.of("passwordReset",true));}
}
