package cn.edu.hzcu.campushub.community;

import cn.edu.hzcu.campushub.common.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE+19)
public class AdminRequestFilter extends OncePerRequestFilter {
    private final AdminAuthService auth;private final HandlerExceptionResolver errors;private final int max;
    public AdminRequestFilter(AdminAuthService auth,@Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors,@Value("${campushub.community.json-max-bytes:2097152}") int max){if(max<1024||max>32*1024*1024)throw new IllegalArgumentException("Invalid case JSON limit");this.auth=auth;this.errors=errors;this.max=max;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String path=request.getServletPath();if(!path.equals("/api/v1/admin")&&!path.startsWith("/api/v1/admin/")){chain.doFilter(request,response);return;}
        RequestTrace.domain(RequestTrace.Domain.COMMUNITY_ADMIN);response.setHeader("Cache-Control","no-store");response.setHeader("Pragma","no-cache");
        try{
            if(!(path.equals("/api/v1/admin/auth/login")&&request.getMethod().equals("POST"))){var headers=Collections.list(request.getHeaders("Authorization"));if(headers.size()!=1||!headers.getFirst().startsWith("Bearer "))throw AdminAuthService.unauthorized();request.setAttribute("campushub.admin",auth.authenticate(headers.getFirst().substring(7)));}
            RequestTrace.stage(RequestTrace.Stage.VALIDATION);
            if(Set.of("POST","PUT","PATCH","DELETE").contains(request.getMethod())){
                int limit=path.equals("/api/v1/admin/auth/login")?4096:max;if(request.getContentLengthLong()>limit)throw large();byte[] body=request.getInputStream().readNBytes(limit+1);if(body.length>limit)throw large();
                request=new HttpServletRequestWrapper(request){@Override public ServletInputStream getInputStream(){var stream=new ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return stream.read();}public boolean isFinished(){return stream.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener l){throw new UnsupportedOperationException();}};}@Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}};
            }chain.doFilter(request,response);
        }catch(RuntimeException failure){if(errors.resolveException(request,response,null,failure)==null)throw failure;}
    }
    private static ApiException large(){return new ApiException(org.springframework.http.HttpStatus.valueOf(413),"REQUEST_TOO_LARGE","请求内容超过允许大小");}
}
