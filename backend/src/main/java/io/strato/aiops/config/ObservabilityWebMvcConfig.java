package io.strato.aiops.config;

import io.strato.aiops.adapter.in.web.OperationalTelemetryInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ObservabilityWebMvcConfig implements WebMvcConfigurer {

    private final OperationalTelemetryInterceptor interceptor;

    /** ObservabilityWebMvcConfig 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ObservabilityWebMvcConfig(OperationalTelemetryInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    /** ObservabilityWebMvcConfig의 addInterceptors 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**")
                .excludePathPatterns("/api/operations/telemetry");
    }
}
