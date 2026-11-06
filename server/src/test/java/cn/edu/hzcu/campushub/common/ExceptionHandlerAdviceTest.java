package cn.edu.hzcu.campushub.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.slf4j.MDC;
import static org.assertj.core.api.Assertions.*;

class ExceptionHandlerAdviceTest {
    @Test void connectionFailureBeforeAndDuringQueryIsUnavailableWithoutSensitiveDetails() {
        MDC.put("traceId","test-trace");
        try {
            var handler = new ExceptionHandlerAdvice();
            for (Exception error : new Exception[]{new CannotCreateTransactionException("sensitive-vendor-detail"),
                    new DataAccessResourceFailureException("sensitive-sql-detail")}) {
                var reply = handler.unavailable(error);
                assertThat(reply.getStatusCode().value()).isEqualTo(503);
                assertThat(reply.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
                assertThat(reply.getBody().traceId()).isEqualTo("test-trace");
                assertThat(reply.getBody().message()).doesNotContain("sensitive");
                assertThat(reply.getBody().data()).isNull();
            }
        } finally { MDC.remove("traceId"); }
    }
}
