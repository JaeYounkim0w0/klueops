package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface KubectlRunnerPort {
    KubectlRunResult run(KubectlRunRequest request, KubectlOutputListener listener);
    boolean cancel(UUID executionId);
    String clientVersion();
    boolean available();
    default String executionBoundary() { return "LOCAL_PROCESS"; }
}
