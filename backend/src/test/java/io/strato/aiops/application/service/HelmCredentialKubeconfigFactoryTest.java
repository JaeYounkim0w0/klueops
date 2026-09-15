package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelmCredentialKubeconfigFactoryTest {
    private final HelmCredentialKubeconfigFactory factory = new HelmCredentialKubeconfigFactory(new ObjectMapper());

    @Test
    void keepsExistingKubeconfigUnchanged() {
        assertThat(factory.create(ClusterCredentialType.KUBECONFIG, "apiVersion: v1"))
                .isEqualTo("apiVersion: v1");
    }

    @Test
    void convertsServiceAccountPayloadWithoutWritingExternalReferences() {
        String result = factory.create(ClusterCredentialType.SERVICE_ACCOUNT_TOKEN, """
                {"apiServerUrl":"https://cluster.example","caCertificate":"pem-data","token":"jwt-token"}
                """);

        assertThat(result).contains("server: 'https://cluster.example'", "token: 'jwt-token'",
                "certificate-authority-data: pem-data", "current-context: target");
    }

    @Test
    void rejectsYamlLineBreakInjection() {
        assertThatThrownBy(() -> factory.create(ClusterCredentialType.SERVICE_ACCOUNT_TOKEN,
                "{\"apiServerUrl\":\"https://cluster.example\",\"token\":\"line1\\nline2\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line break");
    }
}
