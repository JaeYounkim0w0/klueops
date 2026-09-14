package io.strato.aiops.application.port.out;

import java.util.List;
import java.util.UUID;

public interface KubernetesValidationLabPort {

    Preflight preflight(KubernetesConnectionCredential credential, String namespace, String scenarioId);

    Execution apply(KubernetesConnectionCredential credential, UUID runId, String namespace, String scenarioId);

    Cleanup cleanup(KubernetesConnectionCredential credential, UUID runId, String namespace);

    record Preflight(boolean allowed, List<String> passedChecks, List<String> blockingReasons) {
    }

    record Execution(List<String> resources, boolean signalDetected, String observedSignal, String detail) {
    }

    record Cleanup(boolean cleaned, String detail) {
    }
}
