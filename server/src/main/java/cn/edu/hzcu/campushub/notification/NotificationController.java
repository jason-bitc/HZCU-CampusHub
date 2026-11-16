package cn.edu.hzcu.campushub.notification;

import cn.edu.hzcu.campushub.article.ArticleQuery;
import cn.edu.hzcu.campushub.auth.AuthModels.Identity;
import cn.edu.hzcu.campushub.common.ApiException;
import cn.edu.hzcu.campushub.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<NotificationService.Page> list(@RequestAttribute("campushub.identity") Identity identity,
            @RequestParam MultiValueMap<String, String> params, HttpServletRequest request) throws IOException {
        if (!Set.of("page", "size").containsAll(params.keySet())) throw ApiException.invalid();
        emptyBody(request);
        var page = ArticleQuery.parse(params);
        return ApiResponse.success(service.list(identity.me().userId(), page.page(), page.size()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<NotificationService.ReadReceipt> read(@RequestAttribute("campushub.identity") Identity identity,
            @PathVariable String id, @RequestParam MultiValueMap<String, String> params, HttpServletRequest request)
            throws IOException {
        if (!params.isEmpty()) throw ApiException.invalid();
        emptyBody(request);
        return ApiResponse.success(service.markRead(identity.me().userId(), ArticleQuery.requiredId(id)));
    }

    private static void emptyBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1) throw ApiException.invalid();
    }
}
