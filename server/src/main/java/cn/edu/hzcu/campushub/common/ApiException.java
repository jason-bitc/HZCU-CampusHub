package cn.edu.hzcu.campushub.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public ApiException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "查询参数不合法，请检查接口文档");
    }
    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "内容不存在或不可用");
    }
}
