package io.strato.aiops.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI klueOpsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("KlueOps API")
                        .version("0.1.0")
                        .description("Evidence-guided Kubernetes Operations API"));
    }
}
