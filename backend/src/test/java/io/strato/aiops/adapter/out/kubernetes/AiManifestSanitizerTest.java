package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiManifestSanitizerTest {

    private final AiManifestSanitizer sanitizer = new AiManifestSanitizer(new ObjectMapper());

    @Test
    void redactsConfigValuesAnnotationsAndLiteralEnvironmentValues() {
        String sanitized = sanitizer.sanitize("""
                {"kind":"ConfigMap","metadata":{"name":"settings","annotations":{"token":"secret-value"}},
                 "data":{"PASSWORD":"plain-secret","MODE":"prod"},
                 "spec":{"template":{"spec":{"containers":[{"name":"api","env":[
                   {"name":"API_KEY","value":"key-123"},
                   {"name":"FROM_SECRET","valueFrom":{"secretKeyRef":{"name":"credentials","key":"password"}}}
                 ]}]}}}}
                """);

        assertThat(sanitized)
                .doesNotContain("secret-value", "plain-secret", "key-123")
                .contains("***REDACTED***", "credentials", "password", "API_KEY");
    }
}
