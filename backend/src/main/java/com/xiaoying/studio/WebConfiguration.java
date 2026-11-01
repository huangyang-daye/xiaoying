package com.xiaoying.studio;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final Set<String> origins;
    public WebConfiguration(@Value("${app.allowed-origins}") String origins) { this.origins=Arrays.stream(origins.split(",")).map(String::trim).collect(Collectors.toSet()); }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins(origins.toArray(String[]::new)).allowedMethods("GET","POST","OPTIONS").allowCredentials(true);
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                response.setHeader("X-Content-Type-Options","nosniff");
                response.setHeader("Cache-Control","no-store");
                String origin=request.getHeader("Origin");
                if("POST".equals(request.getMethod()) && origin!=null && !origins.contains(origin))
                    throw new ApiException(403,"ORIGIN_NOT_ALLOWED","请求来源不被允许");
                return true;
            }
        });
    }
}
