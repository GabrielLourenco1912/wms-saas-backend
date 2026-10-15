package dev.lourencogabriel.wmssaas.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/** RNF010. Metadata only: credentials, request bodies and session tokens are never logged. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+10)
public class OperationalRequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOG=LoggerFactory.getLogger(OperationalRequestLoggingFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String requested=request.getHeader("X-Request-ID");
        String id=requested!=null&&requested.matches("[A-Za-z0-9_-]{1,64}")?requested:UUID.randomUUID().toString();
        response.setHeader("X-Request-ID",id);MDC.put("requestId",id);long start=System.nanoTime();
        try{chain.doFilter(request,response);}
        finally{
            if(response.getStatus()>=400)LOG.warn("wms_request_failure requestId={} method={} path={} status={} durationMs={}",id,request.getMethod(),request.getRequestURI(),response.getStatus(),(System.nanoTime()-start)/1_000_000);
            MDC.remove("requestId");
        }
    }
}
