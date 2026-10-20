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
    public enum Stage { AUTHENTICATION,AUTHORIZATION,VALIDATION,TARGET,RECEIPT,ATTACHMENT,STORAGE,PERSIST,AUDIT,COMPLETE,GENERAL }
    public record Context(Stage stage,Long articleId,Long commentId,Long expectedRevision,Long actualRevision){
        public String fields(){return "stage="+stage+(articleId==null?"":" articleId="+articleId)+(commentId==null?"":" commentId="+commentId)+(expectedRevision==null?"":" expectedRevision="+expectedRevision)+(actualRevision==null?"":" actualRevision="+actualRevision);}
    }
    private static final ThreadLocal<Context> CONTEXT=new ThreadLocal<>();
    public static String current() { return MDC.get("traceId"); }
    public static Context context(){var c=CONTEXT.get();return c==null?new Context(Stage.GENERAL,null,null,null,null):c;}
    public static void stage(Stage stage){if(current()!=null){var c=context();CONTEXT.set(new Context(stage,c.articleId(),c.commentId(),c.expectedRevision(),c.actualRevision()));}}
    public static void article(long id){if(current()!=null&&id>0){var c=context();CONTEXT.set(new Context(c.stage(),id,c.commentId(),c.expectedRevision(),c.actualRevision()));}}
    public static void comment(long id){if(current()!=null&&id>0){var c=context();CONTEXT.set(new Context(c.stage(),c.articleId(),id,c.expectedRevision(),c.actualRevision()));}}
    public static void revisions(long expected,long actual){if(current()!=null&&expected>=0&&actual>=0){var c=context();CONTEXT.set(new Context(c.stage(),c.articleId(),c.commentId(),expected,actual));}}
    public static String safeCode(String code){return code!=null&&code.matches("[A-Z][A-Z0-9_]{1,47}")?code:"INTERNAL_ERROR";}
    public static void errorCode(String code){if(current()!=null)MDC.put("errorCode",safeCode(code));}
    public static String module(String action){
        if(action.startsWith("COMMENT"))return "COMMENT";if(action.startsWith("AUTHOR_")||action.equals("ARTICLE_AUTHOR")||action.equals("CONTACT_SETTINGS"))return "AUTHOR";
        if(action.startsWith("OWN_ARTICLES")||action.equals("OWN_PROFILE"))return "AUTHORING";if(action.equals("AUTH")||action.equals("OWN_IDENTITY"))return "AUTH";
        if(action.equals("IMAGE"))return "MEDIA";if(action.equals("HEALTH"))return "HEALTH";if(action.equals("PUBLIC_ARTICLES")||action.equals("CATALOG"))return "READING";return "OTHER";
    }
    public static String action(String path){return action(path,"GET");}
    public static String action(String path,String method){
        if(path.matches("/api/v1/articles/[^/]+/comments(?:/.*)?"))return method.equals("POST")?"COMMENT_CREATE":"COMMENT_LIST";
        if(path.matches("/api/v1/articles/[^/]+/comment-images(?:/.*)?"))return "COMMENT_IMAGE_UPLOAD";
        if(path.startsWith("/api/v1/media/comment-images/"))return "COMMENT_IMAGE_READ";
        if(path.matches("/api/v1/comments/[^/]+/replies(?:/.*)?"))return "COMMENT_REPLIES";
        if(path.startsWith("/api/v1/comments/"))return "COMMENT_DELETE";
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
        MDC.put("action",action(request.getServletPath(),request.getMethod()));CONTEXT.set(new Context(Stage.AUTHENTICATION,null,null,null,null));
        response.setHeader("X-Trace-Id", traceId);
        long started = System.nanoTime();
        try { chain.doFilter(request, response); }
        finally {
            String method=java.util.Set.of("GET","POST","PUT","DELETE","PATCH","HEAD","OPTIONS").contains(request.getMethod())?request.getMethod():"OTHER";
            int status=response.getStatus();String result=status==409?"CONFLICT":status==401||status==403||status==404?"DENIED":status>=400?"FAILED":"OK";
            String action=action(request.getServletPath(),method);if(status<400)stage(Stage.COMPLETE);
            String code=MDC.get("errorCode");if(code==null)code=status<400?"OK":"UNCLASSIFIED_ERROR";
            LOG.info("request completed action={} method={} status={} result={} durationMs={} module={} code={} context={}",action,method,status,result,(System.nanoTime()-started)/1_000_000,module(action),safeCode(code),context());
            MDC.remove("traceId");
            MDC.remove("action");MDC.remove("errorCode");CONTEXT.remove();
        }
    }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
}
