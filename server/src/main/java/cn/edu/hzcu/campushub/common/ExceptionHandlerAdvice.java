package cn.edu.hzcu.campushub.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@RestControllerAdvice
public class ExceptionHandlerAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandlerAdvice.class);
    private cn.edu.hzcu.campushub.audit.LocalDiagnostics diagnostics;
    @org.springframework.beans.factory.annotation.Autowired void diagnostics(cn.edu.hzcu.campushub.audit.LocalDiagnostics value){diagnostics=value;}
    private void diagnose(Throwable error,int status,String code){RequestTrace.errorCode(code);if(diagnostics!=null)diagnostics.failure(error,status,code);}
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> domain(ApiException e) {
        diagnose(e,e.status().value(),e.code());
        return ResponseEntity.status(e.status()).body(ApiResponse.failure(e.code(), e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Void>> invalid(Exception e) { return domain(ApiException.invalid()); }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> uploadSize(Exception e){return ResponseEntity.status(413).body(ApiResponse.failure("REQUEST_TOO_LARGE","图片或multipart请求超过允许大小"));}
    @ExceptionHandler({org.springframework.web.multipart.MultipartException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<ApiResponse<Void>> multipart(Exception e){return domain(ApiException.invalid());}
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiResponse<Void>> missing(Exception e) { return domain(ApiException.notFound()); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> method(Exception e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure("METHOD_NOT_ALLOWED", "该接口不支持此请求方法"));
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> media(Exception e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.failure("UNSUPPORTED_MEDIA_TYPE", "请使用application/json请求内容"));
    }
    @ExceptionHandler({DataAccessException.class, org.springframework.transaction.CannotCreateTransactionException.class})
    ResponseEntity<ApiResponse<Void>> unavailable(Exception e) {
        diagnose(e,503,"SERVICE_UNAVAILABLE");
        LOG.warn("database operation failed type={}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure("SERVICE_UNAVAILABLE", "服务暂时不可用，请稍后重试"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
        diagnose(e,500,"INTERNAL_ERROR");
        LOG.error("request failed type={}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("INTERNAL_ERROR", "请求处理失败，请提供追踪编号"));
    }
}
