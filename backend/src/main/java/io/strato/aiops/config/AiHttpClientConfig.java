package io.strato.aiops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiHttpClientConfig {

    @Bean
    RestClientCustomizer aiRestClientTimeoutCustomizer(
            @Value("${aiops.ai.connect-timeout-ms:10000}") long connectTimeoutMs,
            @Value("${aiops.ai.timeout-ms:300000}") long readTimeoutMs
    ) {
        Duration connectTimeout = Duration.ofMillis(connectTimeoutMs);
        Duration readTimeout = Duration.ofMillis(readTimeoutMs);
        return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                ClientHttpRequestFactorySettings.defaults()
                        .withTimeouts(connectTimeout, readTimeout)
        ));
    }
}
