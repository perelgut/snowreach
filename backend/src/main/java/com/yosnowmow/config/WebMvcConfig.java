package com.yosnowmow.config;

import com.yosnowmow.security.RbacInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration — registers application-level HandlerInterceptors.
 *
 * RbacInterceptor is applied to all paths; it is a no-op on methods
 * that do not carry @RequiresRole so there is no performance concern.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RbacInterceptor rbacInterceptor;

    public WebMvcConfig(RbacInterceptor rbacInterceptor) {
        this.rbacInterceptor = rbacInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rbacInterceptor);
    }
}
