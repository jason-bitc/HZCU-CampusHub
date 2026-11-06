package cn.edu.hzcu.campushub.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, T data, String message, String traceId) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", data, null, RequestTrace.current());
    }
    public static ApiResponse<Void> failure(String code, String message) {
        RequestTrace.errorCode(code);
        return new ApiResponse<>(code, null, message, RequestTrace.current());
    }
}
