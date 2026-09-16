package io.strato.aiops.application.port.out;

import java.time.Duration;
import java.util.List;

public interface HelmDeploymentPort {

    /** HelmDeploymentPort의 execute 처리의 핵심 작업 흐름을 실행한다. */
    HelmExecutionResult execute(List<String> arguments, Duration timeout);

    record HelmExecutionResult(int exitCode, String stdout, String stderr) {
    }
}

