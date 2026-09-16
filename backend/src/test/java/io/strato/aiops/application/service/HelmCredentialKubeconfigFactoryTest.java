package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelmCredentialKubeconfigFactoryTest {
    private final HelmCredentialKubeconfigFactory factory = new HelmCredentialKubeconfigFactory(new ObjectMapper());

    /** HelmCredentialKubeconfigFactoryTest의 keepsExistingKubeconfigUnchanged 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void keepsExistingKubeconfigUnchanged() {
        assertThat(factory.create(ClusterCredentialType.KUBECONFIG, "apiVersion: v1"))
                .isEqualTo("apiVersion: v1");
    }

    /** HelmCredentialKubeconfigFactoryTest의 convertsServiceAccountPayloadWithoutWritingExternalReferences 처리 데이터를 필요한 표현으로 변환한다. */
    @Test
    void convertsServiceAccountPayloadWithoutWritingExternalReferences() {
        String result = factory.create(ClusterCredentialType.SERVICE_ACCOUNT_TOKEN, """
                {"apiServerUrl":"https://cluster.example","caCertificate":"pem-data","token":"jwt-token"}
                """);

        assertThat(result).contains("server: 'https://cluster.example'", "token: 'jwt-token'",
                "certificate-authority-data: pem-data", "current-context: target");
    }

    /** HelmCredentialKubeconfigFactoryTest의 rejectsYamlLineBreakInjection 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsYamlLineBreakInjection() {
        assertThatThrownBy(() -> factory.create(ClusterCredentialType.SERVICE_ACCOUNT_TOKEN,
                "{\"apiServerUrl\":\"https://cluster.example\",\"token\":\"line1\\nline2\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line break");
    }
}
