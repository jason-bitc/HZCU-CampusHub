package cn.edu.hzcu.campushub.common;

import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class HealthEnvelopeAdvice implements ResponseBodyAdvice<Object> {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(HealthEnvelopeAdvice.class);
    @Override public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) {
        return true;
    }
    @Override public Object beforeBodyWrite(Object body, MethodParameter type, MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (!request.getURI().getPath().equals("/actuator/health") || body instanceof ApiResponse<?>) return body;
        // Actuator has already performed its real application/database checks and selected HTTP status.
        if (response instanceof org.springframework.http.server.ServletServerHttpResponse servlet
                && servlet.getServletResponse().getStatus() >= 400) {
            LOG.warn("health check reported service unavailable");
            return ApiResponse.failure("SERVICE_UNAVAILABLE", "服务暂时不可用，请稍后重试");
        }
        return ApiResponse.success(Map.of("status", "UP"));
    }
}
