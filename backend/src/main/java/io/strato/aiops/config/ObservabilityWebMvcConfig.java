package io.strato.aiops.config;

import io.strato.aiops.adapter.in.web.OperationalTelemetryInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ObservabilityWebMvcConfig implements WebMvcConfigurer {

    private final OperationalTelemetryInterceptor interceptor;

    public ObservabilityWebMvcConfig(OperationalTelemetryInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**")
                .excludePathPatterns("/api/operations/telemetry");
    }
}
