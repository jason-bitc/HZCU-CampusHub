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
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE+10)
public class RequestTrace extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestTrace.class);
    public static String current() { return MDC.get("traceId"); }
    public static String action(String path){
        if(path.matches("/api/v1/articles/[^/]+/author(?:/.*)?"))return "ARTICLE_AUTHOR";
        if(path.startsWith("/api/v1/authors/")){
            if(path.endsWith("/contact"))return "AUTHOR_CONTACT";
            if(path.endsWith("/articles"))return "AUTHOR_ARTICLES";return "AUTHOR_HOME";
        }
        if(path.equals("/api/v1/me/contact-settings"))return "CONTACT_SETTINGS";
        if(path.equals("/api/v1/me/profile"))return "OWN_PROFILE";
        if(path.startsWith("/api/v1/me/articles"))return "OWN_ARTICLES";
        if(path.startsWith("/api/v1/media/images/"))return "IMAGE";
        if(path.startsWith("/api/v1/auth/"))return "AUTH";
        if(path.equals("/api/v1/me"))return "OWN_IDENTITY";
        if(path.startsWith("/api/v1/articles"))return "PUBLIC_ARTICLES";
        if(path.equals("/api/v1/catalog"))return "CATALOG";
        if(path.equals("/actuator/health"))return "HEALTH";return "OTHER";
    }
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
            String method=java.util.Set.of("GET","POST","PUT","DELETE","PATCH","HEAD","OPTIONS").contains(request.getMethod())?request.getMethod():"OTHER";
            int status=response.getStatus();String result=status==409?"CONFLICT":status==401||status==403||status==404?"DENIED":status>=400?"FAILED":"OK";
            LOG.info("request completed action={} method={} status={} result={} durationMs={}",action(request.getServletPath()),method,status,result,(System.nanoTime()-started)/1_000_000);
            MDC.remove("traceId");
        }
    }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
}
