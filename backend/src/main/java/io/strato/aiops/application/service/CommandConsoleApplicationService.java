package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.in.CommandConsoleUseCase;
import io.strato.aiops.application.port.in.CommandValidationResult;
import io.strato.aiops.application.port.in.StartCommandExecutionCommand;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.CommandExecutionRepositoryPort;
import io.strato.aiops.application.port.out.KubectlRunRequest;
import io.strato.aiops.application.port.out.KubectlRunResult;
import io.strato.aiops.application.port.out.KubectlRunnerPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesClusterPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.command.CommandExecution;
import io.strato.aiops.domain.command.CommandExecutionMode;
import io.strato.aiops.domain.command.CommandStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class CommandConsoleApplicationService implements CommandConsoleUseCase {
    private final ClusterRepositoryPort clusterRepository;
    private final ClusterCredentialRepositoryPort credentialRepository;
    private final CommandExecutionRepositoryPort executionRepository;
    private final SecretCryptoPort secretCrypto;
    private final KubectlRunnerPort runner;
    private final KubectlCommandTokenizer tokenizer;
    private final CommandOutputSanitizer sanitizer;
    private final CommandEventStream eventStream;
    private final AuditLogRepositoryPort auditRepository;
    private final TaskExecutor executor;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration defaultTimeout;
    private final Duration streamTimeout;
    private final int maximumOutputBytes;
    private final CommandExecutionCoordinator coordinator;
    private final CommandSourceAnalysisValidator sourceAnalysisValidator;
    private final CommandOperationVerifier operationVerifier;
    private final KubernetesClusterPort kubernetesClusterPort;

    public CommandConsoleApplicationService(
            ClusterRepositoryPort clusterRepository,
            ClusterCredentialRepositoryPort credentialRepository,
            CommandExecutionRepositoryPort executionRepository,
            SecretCryptoPort secretCrypto,
            KubectlRunnerPort runner,
            KubectlCommandTokenizer tokenizer,
            CommandOutputSanitizer sanitizer,
            CommandEventStream eventStream,
            AuditLogRepositoryPort auditRepository,
            @Qualifier("commandConsoleExecutor") TaskExecutor executor,
            ObjectMapper objectMapper,
            Clock clock,
            CommandExecutionCoordinator coordinator,
            CommandSourceAnalysisValidator sourceAnalysisValidator,
            CommandOperationVerifier operationVerifier,
            KubernetesClusterPort kubernetesClusterPort,
            @Value("${aiops.command-console.timeout-seconds:60}") long timeoutSeconds,
            @Value("${aiops.command-console.stream-timeout-seconds:900}") long streamTimeoutSeconds,
            @Value("${aiops.command-console.maximum-output-bytes:1048576}") int maximumOutputBytes
    ) {
        this.clusterRepository = clusterRepository;
        this.credentialRepository = credentialRepository;
        this.executionRepository = executionRepository;
        this.secretCrypto = secretCrypto;
        this.runner = runner;
        this.tokenizer = tokenizer;
        this.sanitizer = sanitizer;
        this.eventStream = eventStream;
        this.auditRepository = auditRepository;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.defaultTimeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.streamTimeout = Duration.ofSeconds(Math.max(30, streamTimeoutSeconds));
        this.maximumOutputBytes = Math.max(64 * 1024, maximumOutputBytes);
        this.coordinator = coordinator;
        this.sourceAnalysisValidator = sourceAnalysisValidator;
        this.operationVerifier = operationVerifier;
        this.kubernetesClusterPort = kubernetesClusterPort;
    }

    @Override
    public CommandValidationResult validate(UUID clusterId, String namespace, String command) {
        requireCluster(clusterId);
        return tokenizer.validate(command, normalizeNamespace(namespace));
    }

    @Override
    public CommandExecution execute(StartCommandExecutionCommand command) {
        requireCluster(command.clusterId());
        sourceAnalysisValidator.validate(command.clusterId(), command.sourceAnalysisId());
        CommandValidationResult validation = tokenizer.validate(command.command(), normalizeNamespace(command.namespace()));
        if (validation.requiresConfirmation() && !command.confirmed()) {
            throw new CommandConfirmationRequiredException(validation.safety().name(), validation.targetSummary());
        }
        if (validation.interactive()) {
            throw new InteractiveCommandRequiredException();
        }
        KubernetesConnectionCredential credential = credential(command.clusterId());
        Instant now = clock.instant();
        CommandExecution execution = CommandExecution.queued(
                command.clusterId(), command.sourceAnalysisId(), validation.namespace(), validation.normalizedCommand(), json(validation.arguments()),
                validation.safety(), command.actor(), command.requestId(), now);
        Duration executionTtl = (isLongRunning(validation.arguments()) ? streamTimeout : defaultTimeout).plusSeconds(60);
        coordinator.acquire(execution.id(), command.clusterId(), command.actor(), CommandExecutionMode.COMMAND, executionTtl);
        try {
            execution = executionRepository.save(execution);
        } catch (RuntimeException exception) {
            coordinator.release(execution.id());
            throw exception;
        }
        audit("COMMAND_EXECUTION_STARTED", execution, command.actor(), command.requestId());
        CommandExecution queued = execution;
        try {
            executor.execute(() -> run(queued, validation, command.manifest(), credential));
        } catch (RuntimeException exception) {
            coordinator.release(execution.id());
            CommandExecution failed = execution.completed(CommandStatus.FAILED, "",
                    "command executor capacity unavailable", -1, elapsed(execution), false, clock.instant());
            executionRepository.save(failed);
            throw exception;
        }
        return execution;
    }

    @Override
    public CommandExecution getExecution(UUID clusterId, UUID executionId) {
        CommandExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new NoSuchElementException("Command execution not found: " + executionId));
        if (!execution.clusterId().equals(clusterId)) throw new NoSuchElementException("Command execution not found: " + executionId);
        return execution;
    }

    @Override
    public List<CommandExecution> listExecutions(UUID clusterId, String namespace, int limit) {
        requireCluster(clusterId);
        return executionRepository.findRecent(clusterId, normalizeNamespace(namespace), limit);
    }

    @Override
    public CommandExecution cancel(UUID clusterId, UUID executionId, String actor, String requestId) {
        CommandExecution execution = getExecution(clusterId, executionId);
        if (execution.completedAt() != null) return execution;
        boolean canceled = runner.cancel(executionId);
        if (!canceled && execution.status() != CommandStatus.QUEUED) return execution;
        CommandExecution completed = execution.completed(CommandStatus.CANCELED, execution.stdoutText(),
                execution.stderrText(), -1, elapsed(execution), execution.truncated(), clock.instant());
        completed = executionRepository.save(completed);
        eventStream.status(completed);
        audit("COMMAND_EXECUTION_CANCELED", completed, actor, requestId);
        coordinator.release(executionId);
        return completed;
    }

    @Override
    public SseEmitter stream(UUID clusterId, UUID executionId) {
        return eventStream.subscribe(getExecution(clusterId, executionId));
    }

    @Override
    public boolean runnerAvailable() {
        return runner.available();
    }

    @Override
    public String kubectlVersion() {
        return runner.clientVersion();
    }

    @Override
    public CommandExecutionCoordinator.Limits limits() {
        return coordinator.limits();
    }

    @Override
    public String executionBoundary() {
        return runner.executionBoundary();
    }

    @Override
    public boolean metricsApiAvailable(UUID clusterId) {
        requireCluster(clusterId);
        return kubernetesClusterPort.metricsApiAvailable(credential(clusterId));
    }

    private void run(CommandExecution queued, CommandValidationResult validation, String manifest,
                     KubernetesConnectionCredential credential) {
        CommandExecution latest = executionRepository.findById(queued.id()).orElse(queued);
        if (latest.status() == CommandStatus.CANCELED || latest.completedAt() != null) {
            coordinator.release(queued.id());
            return;
        }
        CommandExecution running = executionRepository.save(latest.running(clock.instant()));
        eventStream.status(running);
        CommandOperationVerifier.ProbeContext before = null;
        try {
            if (operationVerifier.required(validation.safety())) {
                try {
                    before = operationVerifier.captureBefore(validation, manifest, credential);
                } catch (RuntimeException exception) {
                    before = CommandOperationVerifier.ProbeContext.unsupported(
                            "사전 Kubernetes 상태 조회에 실패했습니다: " + sanitizer.sanitize(exception.getMessage()));
                }
            }
            Duration timeout = isLongRunning(validation.arguments()) ? streamTimeout : defaultTimeout;
            KubectlRunResult result = runner.run(new KubectlRunRequest(running.id(), credential, validation.namespace(),
                    validation.arguments(), manifest, timeout, maximumOutputBytes),
                    (channel, text) -> eventStream.output(running.id(), channel, sanitizer.sanitize(text)));
            CommandStatus status = result.canceled() ? CommandStatus.CANCELED
                    : result.timedOut() ? CommandStatus.TIMED_OUT
                    : result.exitCode() == 0 ? CommandStatus.SUCCEEDED : CommandStatus.FAILED;
            CommandExecution completed = running.completed(status, sanitizer.sanitize(result.stdout()),
                    sanitizer.sanitize(result.stderr()), result.exitCode(), result.durationMs(), result.truncated(), clock.instant());
            completed = executionRepository.save(completed);
            if (before != null) {
                CommandOperationVerifier.Verification verification;
                try {
                    verification = operationVerifier.verify(before, credential, result.exitCode());
                } catch (RuntimeException exception) {
                    verification = new CommandOperationVerifier.Verification(
                            io.strato.aiops.domain.command.CommandVerificationStatus.VERIFICATION_FAILED,
                            "사후 Kubernetes 상태 조회에 실패했습니다: " + sanitizer.sanitize(exception.getMessage()),
                            before.probe().snapshot(), "", before.rollbackCommand());
                }
                completed = executionRepository.save(completed.verified(verification.status(), verification.summary(),
                        verification.beforeSnapshot(), verification.afterSnapshot(), verification.rollbackCommand(), clock.instant()));
                audit("COMMAND_EXECUTION_VERIFIED_" + verification.status().name(), completed,
                        completed.createdBy(), completed.requestId());
            }
            eventStream.status(completed);
            audit("COMMAND_EXECUTION_" + status.name(), completed, completed.createdBy(), completed.requestId());
        } catch (RuntimeException exception) {
            CommandExecution failed = running.completed(CommandStatus.FAILED, "", sanitizer.sanitize(exception.getMessage()),
                    -1, elapsed(running), false, clock.instant());
            failed = executionRepository.save(failed);
            eventStream.status(failed);
            audit("COMMAND_EXECUTION_FAILED", failed, failed.createdBy(), failed.requestId());
        } finally {
            coordinator.release(queued.id());
        }
    }

    private boolean isLongRunning(List<String> arguments) {
        return arguments.contains("--watch") || arguments.contains("-w") || arguments.contains("--follow")
                || arguments.contains("-f") && arguments.contains("logs") || arguments.contains("wait")
                || arguments.contains("port-forward");
    }

    private KubernetesConnectionCredential credential(UUID clusterId) {
        EncryptedClusterCredential stored = credentialRepository.findByClusterId(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found: " + clusterId));
        String payload = secretCrypto.decrypt(new EncryptedSecret(stored.encryptedPayload(), stored.keyId(),
                stored.algorithm(), stored.nonce()));
        return new KubernetesConnectionCredential(stored.credentialType(), payload);
    }

    private void requireCluster(UUID clusterId) {
        clusterRepository.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
    }

    private String normalizeNamespace(String namespace) {
        return namespace == null || namespace.isBlank() || "__ALL__".equals(namespace) ? null : namespace;
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize kubectl arguments", exception);
        }
    }

    private long elapsed(CommandExecution execution) {
        Instant started = execution.startedAt() == null ? execution.createdAt() : execution.startedAt();
        return Duration.between(started, clock.instant()).toMillis();
    }

    private void audit(String action, CommandExecution execution, String actor, String requestId) {
        auditRepository.save(AuditLog.create(action, "COMMAND_EXECUTION", execution.id().toString(), actor, requestId));
    }
}
