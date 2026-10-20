package cn.edu.hzcu.campushub.auth;

import cn.edu.hzcu.campushub.common.ApiException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE+20)
public class AuthRequestFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final HandlerExceptionResolver exceptions;
    private final int writingJsonLimit;
    private final int commentJsonLimit;
    private final long multipartLimit;
    public AuthRequestFilter(AuthService auth,@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptions,@org.springframework.beans.factory.annotation.Value("${campushub.authoring.json-max-bytes}") int writingJsonLimit,@org.springframework.beans.factory.annotation.Value("${spring.servlet.multipart.max-request-size}") org.springframework.util.unit.DataSize multipartLimit,@org.springframework.beans.factory.annotation.Value("${campushub.comments.json-max-bytes}") int commentJsonLimit){this.auth=auth;this.exceptions=exceptions;if(writingJsonLimit<1||writingJsonLimit>32*1024*1024||multipartLimit.toBytes()<1||commentJsonLimit<1||commentJsonLimit>4*1024*1024)throw new IllegalArgumentException("Invalid request limit");this.writingJsonLimit=writingJsonLimit;this.multipartLimit=multipartLimit.toBytes();this.commentJsonLimit=commentJsonLimit;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        String path=request.getServletPath();
        boolean writingPath=path.startsWith("/api/v1/me/");
        boolean interactionPath=path.matches("/api/v1/articles/[^/]+/(like|favorite|interaction-state)(?:/.*)?");
        boolean commentPath=path.equals("/api/v1/comments")||path.startsWith("/api/v1/comments/")||path.equals("/api/v1/media/comment-images")||path.startsWith("/api/v1/media/comment-images/")||path.matches("/api/v1/articles/[^/]+/(comments|comment-images)(?:/.*)?");
        boolean readerPath=interactionPath||commentPath||path.equals("/api/v1/authors")||path.startsWith("/api/v1/authors/")||path.matches("/api/v1/articles/[^/]+/author(?:/.*)?");
        boolean authPath=path.startsWith("/api/v1/auth/")||path.equals("/api/v1/me")||writingPath||readerPath;
        if(authPath||path.startsWith("/api/v1/media/images/")){response.setHeader("Cache-Control","no-store");response.setHeader("Pragma","no-cache");}
        try{
            if(path.equals("/api/v1/me")||path.equals("/api/v1/auth/logout")||writingPath||readerPath){
                var headers=Collections.list(request.getHeaders("Authorization"));
                if(headers.size()!=1||!headers.getFirst().startsWith("Bearer "))throw AuthService.unauthorized();
                var identity=auth.authenticate(headers.getFirst().substring(7));if(writingPath||readerPath)auth.me(identity);request.setAttribute("campushub.identity",identity);
            }
            cn.edu.hzcu.campushub.common.RequestTrace.stage(cn.edu.hzcu.campushub.common.RequestTrace.Stage.VALIDATION);
            if((interactionPath||path.equals("/api/v1/me/favorites"))&&(request.getContentLengthLong()>0||request.getInputStream().read()!=-1))throw ApiException.invalid();
            if((writingPath||commentPath)&&request.getContentType()!=null&&request.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("multipart/")&&request.getContentLengthLong()>multipartLimit){
                throw new ApiException(HttpStatus.valueOf(413),"REQUEST_TOO_LARGE","图片或multipart请求超过允许大小");
            }
            if(authPath&&java.util.Set.of("POST","PUT","DELETE").contains(request.getMethod())
                    &&(request.getContentType()==null||!request.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("multipart/"))){
                int limit=commentPath?commentJsonLimit:writingPath?writingJsonLimit:16_384;
                if(request.getContentLengthLong()>limit)throw new ApiException(HttpStatus.valueOf(413),"REQUEST_TOO_LARGE","请求内容超过允许大小");
                byte[] body=request.getInputStream().readNBytes(limit+1);
                if(body.length>limit)throw new ApiException(HttpStatus.valueOf(413),"REQUEST_TOO_LARGE","请求内容超过允许大小");
                request=new HttpServletRequestWrapper(request){
                    @Override public ServletInputStream getInputStream(){
                        ByteArrayInputStream stream=new ByteArrayInputStream(body);
                        return new ServletInputStream(){public int read(){return stream.read();}public boolean isFinished(){return stream.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}};
                    }
                    @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
                };
            }
            chain.doFilter(request,response);
        }catch(RuntimeException failure){
            if(exceptions.resolveException(request,response,null,failure)==null)throw failure;
        }
    }
}
