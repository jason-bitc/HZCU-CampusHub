package cn.edu.hzcu.campushub.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("/error")
    ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = attribute instanceof Integer value ? value : 500;
        String code = status == 404 ? "NOT_FOUND" : status == 405 ? "METHOD_NOT_ALLOWED"
                : status < 500 ? "INVALID_REQUEST" : "INTERNAL_ERROR";
        return ResponseEntity.status(status).body(ApiResponse.failure(code,
                status == 404 ? "内容不存在或不可用" : "请求无法处理，请检查接口文档或提供追踪编号"));
    }
}
