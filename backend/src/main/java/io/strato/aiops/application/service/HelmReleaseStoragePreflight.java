package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.KubernetesAccessReviewResult;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesMutationPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class HelmReleaseStoragePreflight {
    private static final List<String> REQUIRED_SECRET_VERBS = List.of("get", "list", "create");

    private final KubernetesMutationPort kubernetesMutationPort;

    public HelmReleaseStoragePreflight(KubernetesMutationPort kubernetesMutationPort) {
        this.kubernetesMutationPort = kubernetesMutationPort;
    }

    public void require(KubernetesConnectionCredential credential, String namespace) {
        List<String> denied = new ArrayList<>();
        for (String verb : REQUIRED_SECRET_VERBS) {
            KubernetesAccessReviewResult result = kubernetesMutationPort.canI(
                    credential, namespace, verb, "", "secrets", null, null);
            if (!result.allowed()) denied.add(verb);
        }
        if (!denied.isEmpty()) {
            // Helm 3 release metadata는 대상 Namespace의 Secret에 저장되므로 실행 전에 최소 권한을 확인한다.
            throw new IllegalArgumentException("Target Namespace is not ready for Helm release storage. "
                    + "The registered Cluster credential needs get/list/create on secrets in namespace '"
                    + namespace + "'. Denied verbs: " + String.join(", ", denied));
        }
    }
}
