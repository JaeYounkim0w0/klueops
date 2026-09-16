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

    /** SecureWebMvcConfig 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public SecureWebMvcConfig(ApiAuthorizationInterceptor authorizationInterceptor) {
        this.authorizationInterceptor = authorizationInterceptor;
    }

    /** SecureWebMvcConfig의 addInterceptors 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor).addPathPatterns("/api/**");
    }
}
