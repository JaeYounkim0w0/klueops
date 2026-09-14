package io.strato.aiops.config;

import io.strato.aiops.adapter.in.web.security.ApiAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("security-oidc | security-oidc-test")
public class SecureWebMvcConfig implements WebMvcConfigurer {
    private final ApiAuthorizationInterceptor authorizationInterceptor;

    public SecureWebMvcConfig(ApiAuthorizationInterceptor authorizationInterceptor) {
        this.authorizationInterceptor = authorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor).addPathPatterns("/api/**");
    }
}
