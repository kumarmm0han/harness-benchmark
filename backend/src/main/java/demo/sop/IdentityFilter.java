package demo.sop;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IdentityFilter extends OncePerRequestFilter {
    private final ObjectMapper json;private final String origin;
    public IdentityFilter(ObjectMapper json,@Value("${app.ui-origin}") String origin){this.json=json;this.origin=origin;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        String path=request.getRequestURI();
        if(!path.startsWith("/api/")){chain.doFilter(request,response);return;}
        String suppliedOrigin=request.getHeader("Origin");
        if(suppliedOrigin!=null){
            if(!origin.equals(suppliedOrigin)){reject(response,403,"ORIGIN_FORBIDDEN","Origin is not permitted.");return;}
            response.setHeader("Access-Control-Allow-Origin",origin);response.setHeader("Vary","Origin");
            response.setHeader("Access-Control-Allow-Headers","Content-Type, X-Demo-User");response.setHeader("Access-Control-Allow-Methods","GET, PUT, POST, OPTIONS");
            if(request.getMethod().equals("OPTIONS")){response.setStatus(204);return;}
        }
        String user=request.getHeader("X-Demo-User");
        if(!List.of("demo-author","demo-consumer").contains(user==null?"":user)){reject(response,401,"UNAUTHORIZED","Select a known demo identity.");return;}
        boolean readCurrent=request.getMethod().equals("GET")&&(path.equals("/api/v1/sops")||path.matches("/api/v1/sops/[^/]+"));
        if(user.equals("demo-consumer")&&!readCurrent){reject(response,403,"FORBIDDEN","This operation requires demo-author.");return;}
        chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response,int status,String code,String message)throws IOException{response.setStatus(status);response.setContentType("application/json");json.writeValue(response.getOutputStream(),new Errors.Body(code,message,List.of()));}
}
