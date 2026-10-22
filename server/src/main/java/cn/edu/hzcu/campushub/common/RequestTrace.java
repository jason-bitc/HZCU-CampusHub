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
    private static final Logger DEBUG=LoggerFactory.getLogger("cn.edu.hzcu.campushub.audit.SafeDebug");
    public enum Stage { AUTHENTICATION,AUTHORIZATION,VALIDATION,TARGET,RECEIPT,ATTACHMENT,STORAGE,PERSIST,AUDIT,READ,MAIL_SEND,CODE_VERIFICATION,TRANSACTION,COMMIT,ROLLBACK,TRANSACTION_UNKNOWN,COMPLETE,GENERAL }
    public record Context(Stage stage,Long articleId,Long commentId,Long expectedRevision,Long actualRevision,Long actorId,Long authorId){
        public Context(Stage stage,Long articleId,Long commentId,Long expectedRevision,Long actualRevision){this(stage,articleId,commentId,expectedRevision,actualRevision,null,null);}
        public Context at(Stage next){return new Context(next,articleId,commentId,expectedRevision,actualRevision,actorId,authorId);}
        public String fields(){return "stage="+stage+(articleId==null?"":" articleId="+articleId)+(commentId==null?"":" commentId="+commentId)+(expectedRevision==null?"":" expectedRevision="+expectedRevision)+(actualRevision==null?"":" actualRevision="+actualRevision)+(actorId==null?"":" actorId="+actorId)+(authorId==null?"":" authorId="+authorId);}
    }
    private static final ThreadLocal<Context> CONTEXT=new ThreadLocal<>();
    private static final ThreadLocal<Throwable> CAUSE=new ThreadLocal<>();
    public record Snapshot(String trace,String action,Context context){}
    public static Snapshot snapshot(){return new Snapshot(current(),MDC.get("action"),context());}
    public static void with(Snapshot captured,Runnable action){
        var previous=MDC.getCopyOfContextMap();var context=CONTEXT.get();var cause=CAUSE.get();
        try{
            clear();if(captured.trace()!=null&&captured.trace().matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))MDC.put("traceId",captured.trace());
            MDC.put("action",captured.action()==null?"OTHER":captured.action());CONTEXT.set(captured.context());action.run();
        }finally{MDC.clear();if(previous!=null)MDC.setContextMap(previous);if(context==null)CONTEXT.remove();else CONTEXT.set(context);if(cause==null)CAUSE.remove();else CAUSE.set(cause);}
    }
    public static void cause(Throwable failure){if(current()!=null)CAUSE.set(failure);}
    public static Throwable causeOr(Throwable failure){return CAUSE.get()==null?failure:CAUSE.get();}
    public static void clear(){MDC.remove("traceId");MDC.remove("action");MDC.remove("errorCode");CONTEXT.remove();CAUSE.remove();}
    private static void debug(){String action=MDC.get("action");if(current()!=null&&action!=null)DEBUG.debug("request stage action={} module={} context={}",action,module(action),context());}
    public static String current() { return MDC.get("traceId"); }
    public static Context context(){var c=CONTEXT.get();return c==null?new Context(Stage.GENERAL,null,null,null,null):c;}
    public static void stage(Stage stage){if(current()!=null){CONTEXT.set(context().at(stage));debug();}}
    public static void article(long id){if(current()!=null&&id>0){var c=context();CONTEXT.set(new Context(c.stage(),id,c.commentId(),c.expectedRevision(),c.actualRevision(),c.actorId(),c.authorId()));debug();}}
    public static void comment(long id){if(current()!=null&&id>0){var c=context();CONTEXT.set(new Context(c.stage(),c.articleId(),id,c.expectedRevision(),c.actualRevision(),c.actorId(),c.authorId()));debug();}}
    public static void actor(long id){if(current()!=null&&id>0){var c=context();CONTEXT.set(new Context(c.stage(),c.articleId(),c.commentId(),c.expectedRevision(),c.actualRevision(),id,c.authorId()));debug();}}
    public static void author(long id){if(current()!=null&&id>0){var c=context();CONTEXT.set(new Context(c.stage(),c.articleId(),c.commentId(),c.expectedRevision(),c.actualRevision(),c.actorId(),id));debug();}}
    public static void revisions(long expected,long actual){if(current()!=null&&expected>=0&&actual>=0){var c=context();CONTEXT.set(new Context(c.stage(),c.articleId(),c.commentId(),expected,actual,c.actorId(),c.authorId()));debug();}}
    public static <T> T write(org.springframework.transaction.support.TransactionTemplate template,org.springframework.transaction.support.TransactionCallback<T> action){
        return template.execute(status->{
            stage(Stage.TRANSACTION);final Snapshot[] captured={snapshot()};
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                @Override public void afterCompletion(int outcome){with(captured[0],()->stage(outcome==STATUS_COMMITTED?Stage.COMMIT:outcome==STATUS_ROLLED_BACK?Stage.ROLLBACK:Stage.TRANSACTION_UNKNOWN));}
            });
            try{return action.doInTransaction(status);}finally{captured[0]=snapshot();}
        });
    }
    public static String safeCode(String code){return code!=null&&code.matches("[A-Z][A-Z0-9_]{1,47}")?code:"INTERNAL_ERROR";}
    public static void errorCode(String code){if(current()!=null)MDC.put("errorCode",safeCode(code));}
    public static String module(String action){
        if(java.util.Set.of("COIN_BALANCE","COIN_LEDGER","REWARDS_READ","LOGIN_REWARD","READING_START","READING_REWARD","COIN_STATE","ARTICLE_COIN").contains(action))return "COIN";
        if(action.startsWith("LIKE_")||action.startsWith("FAVORITE")||action.equals("INTERACTION_READ"))return "INTERACTION";
        if(action.startsWith("AUTH_")||action.equals("IDENTITY_READ"))return "AUTH";
        if(action.startsWith("PROFILE_")||action.startsWith("DRAFT_")||action.startsWith("WRITING_")||action.equals("ARTICLE_IMAGE_UPLOAD"))return "AUTHORING";
        if(action.startsWith("CONTACT_"))return "AUTHOR";
        if(action.equals("ARTICLE_LIST")||action.equals("ARTICLE_DETAIL"))return "READING";
        if(action.startsWith("COMMENT"))return "COMMENT";if(action.startsWith("AUTHOR_")||action.equals("ARTICLE_AUTHOR")||action.equals("CONTACT_SETTINGS"))return "AUTHOR";
        if(action.startsWith("OWN_ARTICLES")||action.equals("OWN_PROFILE"))return "AUTHORING";if(action.equals("AUTH")||action.equals("OWN_IDENTITY"))return "AUTH";
        if(action.equals("IMAGE"))return "MEDIA";if(action.equals("HEALTH"))return "HEALTH";if(action.equals("PUBLIC_ARTICLES")||action.equals("CATALOG"))return "READING";return "OTHER";
    }
    public static String action(String path){return action(path,"GET");}
    public static String action(String path,String method){
        if(path.equals("/api/v1/me/coins"))return "COIN_BALANCE";
        if(path.equals("/api/v1/me/coin-ledger"))return "COIN_LEDGER";
        if(path.equals("/api/v1/me/rewards"))return "REWARDS_READ";
        if(path.equals("/api/v1/me/rewards/login"))return "LOGIN_REWARD";
        if(path.equals("/api/v1/me/rewards/reading"))return "READING_REWARD";
        if(path.matches("/api/v1/articles/[^/]+/reading-session"))return "READING_START";
        if(path.matches("/api/v1/articles/[^/]+/coin-state"))return "COIN_STATE";
        if(path.matches("/api/v1/articles/[^/]+/coins"))return "ARTICLE_COIN";
        if(path.equals("/api/v1/me/favorites"))return "FAVORITES_LIST";
        if(path.matches("/api/v1/articles/[^/]+/like"))return method.equals("DELETE")?"LIKE_CANCEL":"LIKE_SET";
        if(path.matches("/api/v1/articles/[^/]+/favorite"))return method.equals("DELETE")?"FAVORITE_CANCEL":"FAVORITE_SET";
        if(path.matches("/api/v1/articles/[^/]+/interaction-state"))return "INTERACTION_READ";
        if(path.startsWith("/api/v1/auth/"))return switch(path){
            case "/api/v1/auth/registration-codes"->"AUTH_REGISTER_CODE";case "/api/v1/auth/register"->"AUTH_REGISTER";
            case "/api/v1/auth/login"->"AUTH_LOGIN";case "/api/v1/auth/logout"->"AUTH_LOGOUT";
            case "/api/v1/auth/password-reset-codes"->"AUTH_RESET_CODE";case "/api/v1/auth/password-resets"->"AUTH_PASSWORD_RESET";default->"OTHER";};
        if(path.equals("/api/v1/me"))return "IDENTITY_READ";
        if(path.equals("/api/v1/me/profile"))return method.equals("PUT")?"PROFILE_UPDATE":"PROFILE_READ";
        if(path.equals("/api/v1/me/contact-settings"))return method.equals("PUT")?"CONTACT_UPDATE":"CONTACT_READ";
        if(path.equals("/api/v1/me/articles"))return method.equals("POST")?"DRAFT_CREATE":"DRAFT_LIST";
        if(path.matches("/api/v1/me/articles/[^/]+/draft"))return method.equals("PUT")?"DRAFT_SAVE":"DRAFT_READ";
        if(path.matches("/api/v1/me/articles/[^/]+/images"))return "ARTICLE_IMAGE_UPLOAD";
        if(path.matches("/api/v1/me/articles/[^/]+/publish"))return "WRITING_PUBLISH";
        if(path.matches("/api/v1/me/articles/[^/]+/unpublish"))return "WRITING_UNPUBLISH";
        if(path.matches("/api/v1/me/articles/[^/]+/restore"))return "WRITING_RESTORE";
        if(path.matches("/api/v1/me/articles/[^/]+")&&method.equals("DELETE"))return "WRITING_DELETE";
        if(path.equals("/api/v1/articles"))return "ARTICLE_LIST";
        if(path.matches("/api/v1/articles/[^/]+"))return "ARTICLE_DETAIL";
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
        clear();MDC.put("traceId", traceId);
        MDC.put("action",action(request.getServletPath(),request.getMethod()));CONTEXT.set(new Context(Stage.AUTHENTICATION,null,null,null,null));
        debug();
        response.setHeader("X-Trace-Id", traceId);
        long started = System.nanoTime();
        try { chain.doFilter(request, response); }
        finally {
            String method=java.util.Set.of("GET","POST","PUT","DELETE","PATCH","HEAD","OPTIONS").contains(request.getMethod())?request.getMethod():"OTHER";
            int status=response.getStatus();String result=status==409?"CONFLICT":status==401||status==403||status==404?"DENIED":status>=400?"FAILED":"OK";
            String action=action(request.getServletPath(),method);if(status<400)stage(Stage.COMPLETE);
            String code=MDC.get("errorCode");if(code==null)code=status<400?"OK":"UNCLASSIFIED_ERROR";
            try{LOG.info("request completed action={} method={} status={} result={} durationMs={} module={} code={} context={}",action,method,status,result,(System.nanoTime()-started)/1_000_000,module(action),safeCode(code),context());}finally{clear();}
        }
    }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
}
