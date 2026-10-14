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
    private final long multipartLimit;
    public AuthRequestFilter(AuthService auth,@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptions,@org.springframework.beans.factory.annotation.Value("${campushub.authoring.json-max-bytes}") int writingJsonLimit,@org.springframework.beans.factory.annotation.Value("${spring.servlet.multipart.max-request-size}") org.springframework.util.unit.DataSize multipartLimit){this.auth=auth;this.exceptions=exceptions;if(writingJsonLimit<1||writingJsonLimit>32*1024*1024||multipartLimit.toBytes()<1)throw new IllegalArgumentException("Invalid request limit");this.writingJsonLimit=writingJsonLimit;this.multipartLimit=multipartLimit.toBytes();}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        String path=request.getServletPath();
        boolean writingPath=path.startsWith("/api/v1/me/");
        boolean authPath=path.startsWith("/api/v1/auth/")||path.equals("/api/v1/me")||writingPath;
        if(authPath||path.startsWith("/api/v1/media/images/")){response.setHeader("Cache-Control","no-store");response.setHeader("Pragma","no-cache");}
        try{
            if(path.equals("/api/v1/me")||path.equals("/api/v1/auth/logout")||writingPath){
                var headers=Collections.list(request.getHeaders("Authorization"));
                if(headers.size()!=1||!headers.getFirst().startsWith("Bearer "))throw AuthService.unauthorized();
                var identity=auth.authenticate(headers.getFirst().substring(7));if(writingPath)auth.me(identity);request.setAttribute("campushub.identity",identity);
            }
            if(writingPath&&request.getContentType()!=null&&request.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("multipart/")&&request.getContentLengthLong()>multipartLimit){
                throw new ApiException(HttpStatus.valueOf(413),"REQUEST_TOO_LARGE","图片或multipart请求超过允许大小");
            }
            if(authPath&&java.util.Set.of("POST","PUT","DELETE").contains(request.getMethod())
                    &&(request.getContentType()==null||!request.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("multipart/"))){
                int limit=writingPath?writingJsonLimit:16_384;
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
