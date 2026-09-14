package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.command.CommandExecution;
import io.strato.aiops.application.service.CommandExecutionCoordinator;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

public interface CommandConsoleUseCase {
    CommandValidationResult validate(UUID clusterId, String namespace, String command);
    CommandExecution execute(StartCommandExecutionCommand command);
    CommandExecution getExecution(UUID clusterId, UUID executionId);
    List<CommandExecution> listExecutions(UUID clusterId, String namespace, int limit);
    CommandExecution cancel(UUID clusterId, UUID executionId, String actor, String requestId);
    SseEmitter stream(UUID clusterId, UUID executionId);
    boolean runnerAvailable();
    String kubectlVersion();
    CommandExecutionCoordinator.Limits limits();
    String executionBoundary();
    boolean metricsApiAvailable(UUID clusterId);
}
