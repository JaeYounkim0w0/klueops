package io.strato.aiops.application.port.out;

import java.util.List;
import java.util.UUID;

public interface KubernetesValidationLabPort {

    /** KubernetesValidationLabPort의 preflight 처리 계약을 정의한다. */
    Preflight preflight(KubernetesConnectionCredential credential, String namespace, String scenarioId);

    /** KubernetesValidationLabPort의 apply 처리 계약을 정의한다. */
    Execution apply(KubernetesConnectionCredential credential, UUID runId, String namespace, String scenarioId);

    /** KubernetesValidationLabPort의 cleanup 처리 계약을 정의한다. */
    Cleanup cleanup(KubernetesConnectionCredential credential, UUID runId, String namespace);

    record Preflight(boolean allowed, List<String> passedChecks, List<String> blockingReasons) {
    }

    record Execution(List<String> resources, boolean signalDetected, String observedSignal, String detail) {
    }

    record Cleanup(boolean cleaned, String detail) {
    }
}
