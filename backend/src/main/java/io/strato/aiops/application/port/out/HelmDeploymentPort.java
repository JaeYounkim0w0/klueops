package io.strato.aiops.application.port.out;

import java.time.Duration;
import java.util.List;

public interface HelmDeploymentPort {

    HelmExecutionResult execute(List<String> arguments, Duration timeout);

    record HelmExecutionResult(int exitCode, String stdout, String stderr) {
    }
}

