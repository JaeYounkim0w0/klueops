package io.strato.aiops.adapter.out.helm;

import io.strato.aiops.application.port.out.HelmDeploymentPort;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class ProcessBuilderHelmDeploymentAdapter implements HelmDeploymentPort {

    @Override
    public HelmExecutionResult execute(List<String> arguments, Duration timeout) {
        throw new UnsupportedOperationException("Helm CLI execution will be implemented after validation rules are in place.");
    }
}

