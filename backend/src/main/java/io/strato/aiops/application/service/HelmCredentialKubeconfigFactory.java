package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class HelmCredentialKubeconfigFactory {
    private final ObjectMapper objectMapper;

    /** HelmCredentialKubeconfigFactory 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public HelmCredentialKubeconfigFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** HelmCredentialKubeconfigFactory의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public String create(ClusterCredentialType type, String payload) {
        if (type == ClusterCredentialType.KUBECONFIG) return payload;
        try {
            ServiceAccountPayload serviceAccount = objectMapper.readValue(payload, ServiceAccountPayload.class);
            requireValue(serviceAccount.apiServerUrl(), "ServiceAccount server URL is required");
            requireValue(serviceAccount.token(), "ServiceAccount token is required");
            String caData = serviceAccount.caCertificate() != null
                    && serviceAccount.caCertificate().contains("BEGIN CERTIFICATE")
                    ? Base64.getEncoder().encodeToString(serviceAccount.caCertificate()
                    .getBytes(StandardCharsets.UTF_8)) : serviceAccount.caCertificate();
            // 외부 파일을 참조하지 않는 최소 kubeconfig로 변환해 임시 작업공간 밖에 Secret이 남지 않게 한다.
            return """
                    apiVersion: v1
                    kind: Config
                    clusters:
                      - name: target
                        cluster:
                          server: '%s'
                          certificate-authority-data: %s
                    users:
                      - name: klueops
                        user:
                          token: '%s'
                    contexts:
                      - name: target
                        context:
                          cluster: target
                          user: klueops
                    current-context: target
                    """.formatted(yamlScalar(serviceAccount.apiServerUrl()), caData == null ? "" : caData,
                    yamlScalar(serviceAccount.token()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid ServiceAccount credential payload", exception);
        }
    }

    /** HelmCredentialKubeconfigFactory의 requireValue 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireValue(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    /** HelmCredentialKubeconfigFactory의 yamlScalar 처리에 필요한 업무 로직을 수행한다. */
    private String yamlScalar(String value) {
        if (value.contains("\n") || value.contains("\r"))
            throw new IllegalArgumentException("ServiceAccount credential contains an invalid line break");
        return value.replace("'", "''");
    }

    private record ServiceAccountPayload(String apiServerUrl, String caCertificate, String token) { }
}
