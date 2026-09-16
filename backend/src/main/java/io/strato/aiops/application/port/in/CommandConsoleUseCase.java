package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.command.CommandExecution;
import io.strato.aiops.application.service.CommandExecutionCoordinator;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

public interface CommandConsoleUseCase {
    /** CommandConsoleUseCase의 validate 처리 입력과 현재 상태의 유효성을 검증한다. */
    CommandValidationResult validate(UUID clusterId, String namespace, String command);
    /** CommandConsoleUseCase의 execute 처리의 핵심 작업 흐름을 실행한다. */
    CommandExecution execute(StartCommandExecutionCommand command);
    /** CommandConsoleUseCase의 getExecution 처리 결과를 조회해 반환한다. */
    CommandExecution getExecution(UUID clusterId, UUID executionId);
    /** CommandConsoleUseCase의 listExecutions 처리 결과를 조회해 반환한다. */
    List<CommandExecution> listExecutions(UUID clusterId, String namespace, int limit);
    /** CommandConsoleUseCase의 cancel 처리 조건의 충족 여부를 판단한다. */
    CommandExecution cancel(UUID clusterId, UUID executionId, String actor, String requestId);
    /** CommandConsoleUseCase의 stream 처리 계약을 정의한다. */
    SseEmitter stream(UUID clusterId, UUID executionId);
    /** CommandConsoleUseCase의 runnerAvailable 처리의 핵심 작업 흐름을 실행한다. */
    boolean runnerAvailable();
    /** CommandConsoleUseCase의 kubectlVersion 처리 계약을 정의한다. */
    String kubectlVersion();
    /** CommandConsoleUseCase의 limits 처리 계약을 정의한다. */
    CommandExecutionCoordinator.Limits limits();
    /** CommandConsoleUseCase의 executionBoundary 처리 계약을 정의한다. */
    String executionBoundary();
    /** CommandConsoleUseCase의 metricsApiAvailable 처리 계약을 정의한다. */
    boolean metricsApiAvailable(UUID clusterId);
}
