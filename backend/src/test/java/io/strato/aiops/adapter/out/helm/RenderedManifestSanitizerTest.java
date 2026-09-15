package io.strato.aiops.adapter.out.helm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenderedManifestSanitizerTest {
    private final RenderedManifestSanitizer sanitizer = new RenderedManifestSanitizer();

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
