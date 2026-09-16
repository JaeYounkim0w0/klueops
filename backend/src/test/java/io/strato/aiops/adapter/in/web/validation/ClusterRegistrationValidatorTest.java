package io.strato.aiops.adapter.in.web.validation;

import io.strato.aiops.adapter.in.web.dto.RegisterClusterRequest;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClusterRegistrationValidatorTest {

    private final ClusterRegistrationValidator validator = new ClusterRegistrationValidator();

    /** ClusterRegistrationValidatorTest의 acceptsKubeconfigCredential 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void acceptsKubeconfigCredential() {
        validator.validate(baseRequest(
                ClusterCredentialType.KUBECONFIG,
                "apiVersion: v1\nkind: Config\nclusters: []",
                null
        ));
    }

    /** ClusterRegistrationValidatorTest의 rejectsExecPluginKubeconfig 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsExecPluginKubeconfig() {
        RegisterClusterRequest request = baseRequest(
                ClusterCredentialType.KUBECONFIG,
                "apiVersion: v1\nusers:\n- user:\n    exec:\n      command: aws",
                null
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exec plugin kubeconfig is not supported");
    }

    /** ClusterRegistrationValidatorTest의 acceptsServiceAccountTokenCredential 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void acceptsServiceAccountTokenCredential() {
        validator.validate(baseRequest(
                ClusterCredentialType.SERVICE_ACCOUNT_TOKEN,
                null,
                new RegisterClusterRequest.ServiceAccountCredentialRequest(
                        "https://127.0.0.1:6443",
                        "-----BEGIN CERTIFICATE-----",
                        "token"
                )
        ));
    }

    /** ClusterRegistrationValidatorTest의 rejectsMissingServiceAccountTokenCredential 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsMissingServiceAccountTokenCredential() {
        RegisterClusterRequest request = baseRequest(
                ClusterCredentialType.SERVICE_ACCOUNT_TOKEN,
                null,
                null
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceAccount credential is required");
    }

    /** ClusterRegistrationValidatorTest의 baseRequest 처리에 필요한 업무 로직을 수행한다. */
    private RegisterClusterRequest baseRequest(
            ClusterCredentialType credentialType,
            String kubeconfig,
            RegisterClusterRequest.ServiceAccountCredentialRequest serviceAccount
    ) {
        return new RegisterClusterRequest(
                "dev-cluster",
                "dev cluster",
                ClusterEnvironment.DEV,
                ClusterProvider.KIND,
                "local",
                credentialType,
                kubeconfig,
                serviceAccount,
                new RegisterClusterRequest.NamespaceAccessRequest(false, java.util.List.of("default"), "default"),
                new RegisterClusterRequest.SyncSettingsRequest(true, 300)
        );
    }
}

