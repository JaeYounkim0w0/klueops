package io.strato.aiops.adapter.out.helm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenderedManifestSanitizerTest {
    private final RenderedManifestSanitizer sanitizer = new RenderedManifestSanitizer();

    /** RenderedManifestSanitizerTest의 redactsSecretAndEnvironmentValuesAcrossYamlDocuments 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void redactsSecretAndEnvironmentValuesAcrossYamlDocuments() {
        String manifest = """
                apiVersion: v1
                kind: Secret
                metadata:
                  name: credentials
                stringData:
                  password: very-secret
                ---
                apiVersion: apps/v1
                kind: Deployment
                spec:
                  template:
                    spec:
                      containers:
                        - name: app
                          env:
                            - name: TOKEN
                              value: plain-token
                """;

        String safe = sanitizer.sanitize(manifest);

        assertThat(safe).contains("***REDACTED***").doesNotContain("very-secret", "plain-token");
        assertThat(safe.split("---")).hasSize(2);
    }
}
