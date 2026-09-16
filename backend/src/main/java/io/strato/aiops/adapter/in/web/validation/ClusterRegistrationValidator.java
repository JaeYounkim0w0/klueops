package io.strato.aiops.adapter.in.web.validation;

import io.strato.aiops.adapter.in.web.dto.RegisterClusterRequest;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.stereotype.Component;

@Component
public class ClusterRegistrationValidator {

    /** ClusterRegistrationValidator의 validate 처리 입력과 현재 상태의 유효성을 검증한다. */
    public void validate(RegisterClusterRequest request) {
        if (request.credentialType() == ClusterCredentialType.KUBECONFIG) {
            requireKubeconfig(request);
            rejectExecPluginKubeconfig(request.kubeconfig());
            return;
        }

        if (request.credentialType() == ClusterCredentialType.SERVICE_ACCOUNT_TOKEN) {
            requireServiceAccount(request);
            return;
        }

        throw new IllegalArgumentException("Unsupported cluster credential type: " + request.credentialType());
    }

    /** ClusterRegistrationValidator의 requireKubeconfig 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireKubeconfig(RegisterClusterRequest request) {
        if (request.kubeconfig() == null || request.kubeconfig().isBlank()) {
            throw new IllegalArgumentException("kubeconfig is required when credentialType is KUBECONFIG");
        }
    }

    /** ClusterRegistrationValidator의 rejectExecPluginKubeconfig 처리에 필요한 업무 로직을 수행한다. */
    private void rejectExecPluginKubeconfig(String kubeconfig) {
        if (kubeconfig.contains("exec:")) {
            throw new IllegalArgumentException("exec plugin kubeconfig is not supported in MVP. Use ServiceAccount token credential instead.");
        }
    }

    /** ClusterRegistrationValidator의 requireServiceAccount 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireServiceAccount(RegisterClusterRequest request) {
        if (request.serviceAccount() == null) {
            throw new IllegalArgumentException("serviceAccount credential is required when credentialType is SERVICE_ACCOUNT_TOKEN");
        }
    }
}

