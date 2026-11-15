package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.article.ArticleQuery;
import cn.edu.hzcu.campushub.authoring.WritingInput;
import cn.edu.hzcu.campushub.coin.CoinInput;
import cn.edu.hzcu.campushub.common.ApiException;
import cn.edu.hzcu.campushub.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users/{id}/certification")
public class CertificationModerationController {
    private final CertificationModerationService service;

    public CertificationModerationController(CertificationModerationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<CertificationModerationService.Status> status(
            @RequestAttribute("campushub.admin") AdminAuthService.Identity admin, @PathVariable String id,
            HttpServletRequest request) throws IOException {
        if (request.getQueryString() != null || request.getContentLengthLong() > 0
                || request.getInputStream().read() != -1) throw ApiException.invalid();
        return ApiResponse.success(service.status(admin.adminId(), ArticleQuery.requiredId(id)));
    }

    @PostMapping(value = "/revocation", consumes = "application/json")
    public ApiResponse<CertificationModerationService.Receipt> revoke(
            @RequestAttribute("campushub.admin") AdminAuthService.Identity admin, @PathVariable String id,
            HttpServletRequest request, @RequestBody String body) {
        if (request.getQueryString() != null) throw ApiException.invalid();
        var input = CoinInput.parse(body, "expectedRevision", "requestId", "reason");
        String reason = WritingInput.text(input, "reason", 2000).strip();
        if (reason.isEmpty()) throw ApiException.invalid();
        return ApiResponse.success(service.revoke(admin, ArticleQuery.requiredId(id),
                WritingInput.revision(input), CoinInput.key(input), reason));
    }
}
