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
    public AuthRequestFilter(AuthService auth,@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptions){this.auth=auth;this.exceptions=exceptions;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        String path=request.getServletPath();
        boolean authPath=path.startsWith("/api/v1/auth/")||path.equals("/api/v1/me");
        if(authPath){response.setHeader("Cache-Control","no-store");response.setHeader("Pragma","no-cache");}
        try{
            if(path.equals("/api/v1/me")||path.equals("/api/v1/auth/logout")){
                var headers=Collections.list(request.getHeaders("Authorization"));
                if(headers.size()!=1||!headers.getFirst().startsWith("Bearer "))throw AuthService.unauthorized();
                request.setAttribute("campushub.identity",auth.authenticate(headers.getFirst().substring(7)));
            }
            if(authPath&&request.getMethod().equals("POST")){
                byte[] body=request.getInputStream().readNBytes(16_385);
                if(body.length>16_384)throw new ApiException(HttpStatus.valueOf(413),"REQUEST_TOO_LARGE","请求内容超过允许大小");
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
