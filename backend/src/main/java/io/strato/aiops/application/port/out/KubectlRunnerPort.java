package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface KubectlRunnerPort {
    /** KubectlRunnerPort의 run 처리의 핵심 작업 흐름을 실행한다. */
    KubectlRunResult run(KubectlRunRequest request, KubectlOutputListener listener);
    /** KubectlRunnerPort의 cancel 처리 조건의 충족 여부를 판단한다. */
    boolean cancel(UUID executionId);
    /** KubectlRunnerPort의 clientVersion 처리 계약을 정의한다. */
    String clientVersion();
    /** KubectlRunnerPort의 available 처리 계약을 정의한다. */
    boolean available();
    /** KubectlRunnerPort의 executionBoundary 처리에 필요한 업무 로직을 수행한다. */
    default String executionBoundary() { return "LOCAL_PROCESS"; }
}
