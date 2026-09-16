package io.strato.aiops.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** OpenApiConfig의 klueOpsOpenApi 처리에 필요한 업무 로직을 수행한다. */
    @Bean
    public OpenAPI klueOpsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("KlueOps API")
                        .version("0.1.0")
                        .description("Evidence-guided Kubernetes Operations API"));
    }
}
