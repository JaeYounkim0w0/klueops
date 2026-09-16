package io.strato.aiops.application.port.out;

import java.time.Instant;
import java.util.List;

public record KubernetesConnectionTestResult(
        boolean reachable,
        String kubernetesVersion,
        List<String> namespaces,
        String message,
        Instant checkedAt
) {
    /** KubernetesConnectionTestResult의 success 처리에 필요한 업무 로직을 수행한다. */
    public static KubernetesConnectionTestResult success(String kubernetesVersion, List<String> namespaces) {
        return new KubernetesConnectionTestResult(true, kubernetesVersion, List.copyOf(namespaces), "Kubernetes API connection succeeded", Instant.now());
    }

    /** KubernetesConnectionTestResult의 failure 처리에 필요한 업무 로직을 수행한다. */
    public static KubernetesConnectionTestResult failure(String message) {
        return new KubernetesConnectionTestResult(false, null, List.of(), message, Instant.now());
    }
}
