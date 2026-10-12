package cn.edu.hzcu.campushub.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestTrace extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestTrace.class);
    public static String current() { return MDC.get("traceId"); }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = (String) request.getAttribute("campushub.traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            request.setAttribute("campushub.traceId", traceId);
        }
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);
        long started = System.nanoTime();
        try { chain.doFilter(request, response); }
        finally {
            LOG.info("request completed method={} status={} durationMs={}", request.getMethod(),
                    response.getStatus(), (System.nanoTime() - started) / 1_000_000);
            MDC.remove("traceId");
        }
    }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
}
