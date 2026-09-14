package io.strato.aiops.adapter.in.web.validation;

import io.strato.aiops.adapter.in.web.dto.RegisterClusterRequest;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.stereotype.Component;

@Component
public class ClusterRegistrationValidator {

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

    private void requireKubeconfig(RegisterClusterRequest request) {
        if (request.kubeconfig() == null || request.kubeconfig().isBlank()) {
            throw new IllegalArgumentException("kubeconfig is required when credentialType is KUBECONFIG");
        }
    }

    private void rejectExecPluginKubeconfig(String kubeconfig) {
        if (kubeconfig.contains("exec:")) {
            throw new IllegalArgumentException("exec plugin kubeconfig is not supported in MVP. Use ServiceAccount token credential instead.");
        }
    }

    private void requireServiceAccount(RegisterClusterRequest request) {
        if (request.serviceAccount() == null) {
            throw new IllegalArgumentException("serviceAccount credential is required when credentialType is SERVICE_ACCOUNT_TOKEN");
        }
    }
}

