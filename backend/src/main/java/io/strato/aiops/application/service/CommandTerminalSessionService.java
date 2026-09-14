package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.in.CommandTerminalUseCase;
import io.strato.aiops.application.port.in.CommandValidationResult;
import io.strato.aiops.application.port.in.StartCommandExecutionCommand;
import io.strato.aiops.application.port.in.TerminalClient;
import io.strato.aiops.application.port.in.TerminalSessionTicket;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.CommandExecutionRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesTerminalPort;
import io.strato.aiops.application.port.out.KubernetesTerminalRequest;
import io.strato.aiops.application.port.out.KubernetesTerminalSession;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.command.CommandExecution;
import io.strato.aiops.domain.command.CommandExecutionMode;
import io.strato.aiops.domain.command.CommandStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CommandTerminalSessionService implements CommandTerminalUseCase {
    private final ClusterRepositoryPort clusters;
    private final ClusterCredentialRepositoryPort credentials;
    private final CommandExecutionRepositoryPort executions;
    private final SecretCryptoPort secretCrypto;
    private final KubernetesTerminalPort terminalPort;
    private final AuditLogRepositoryPort audits;
    private final KubectlCommandTokenizer tokenizer;
    private final TerminalCommandParser parser;
    private final CommandOutputSanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration ticketTtl;
    private final Duration idleTimeout;
    private final int maximumOutputBytes;
    private final Map<UUID, SessionState> sessions = new ConcurrentHashMap<>();
    private final CommandExecutionCoordinator coordinator;
    private final CommandSourceAnalysisValidator sourceAnalysisValidator;

    public CommandTerminalSessionService(ClusterRepositoryPort clusters, ClusterCredentialRepositoryPort credentials,
            CommandExecutionRepositoryPort executions, SecretCryptoPort secretCrypto, KubernetesTerminalPort terminalPort,
            AuditLogRepositoryPort audits, KubectlCommandTokenizer tokenizer, TerminalCommandParser parser,
            CommandOutputSanitizer sanitizer, ObjectMapper objectMapper, Clock clock,
            CommandExecutionCoordinator coordinator,
            CommandSourceAnalysisValidator sourceAnalysisValidator,
            @Value("${aiops.command-console.terminal-ticket-seconds:30}") long ticketSeconds,
            @Value("${aiops.command-console.terminal-idle-seconds:900}") long idleSeconds,
            @Value("${aiops.command-console.maximum-output-bytes:1048576}") int maximumOutputBytes) {
        this.clusters = clusters;
        this.credentials = credentials;
        this.executions = executions;
        this.secretCrypto = secretCrypto;
        this.terminalPort = terminalPort;
        this.audits = audits;
        this.tokenizer = tokenizer;
        this.parser = parser;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ticketTtl = Duration.ofSeconds(Math.max(10, ticketSeconds));
        this.idleTimeout = Duration.ofSeconds(Math.max(60, idleSeconds));
        this.maximumOutputBytes = Math.max(64 * 1024, maximumOutputBytes);
        this.coordinator = coordinator;
        this.sourceAnalysisValidator = sourceAnalysisValidator;
    }

    @Override
    public TerminalSessionTicket create(StartCommandExecutionCommand command) {
        clusters.findById(command.clusterId()).orElseThrow(() -> new NoSuchElementException("Cluster not found: " + command.clusterId()));
        sourceAnalysisValidator.validate(command.clusterId(), command.sourceAnalysisId());
        CommandValidationResult validation = tokenizer.validate(command.command(), normalizeNamespace(command.namespace()));
        if (!validation.interactive()) throw new IllegalArgumentException("terminal session requires kubectl exec or attach with stdin/tty");
        if (!command.confirmed()) throw new CommandConfirmationRequiredException(validation.safety().name(), validation.targetSummary());
        TerminalCommandSpec spec = parser.parse(validation.arguments());
        Instant now = clock.instant();
        CommandExecution execution = CommandExecution.queued(command.clusterId(), command.sourceAnalysisId(), validation.namespace(),
                validation.normalizedCommand(), json(validation.arguments()), validation.safety(), command.actor(), command.requestId(), now);
        coordinator.acquire(execution.id(), command.clusterId(), command.actor(), CommandExecutionMode.TERMINAL,
                ticketTtl.plus(idleTimeout).plusSeconds(60));
        try {
            execution = executions.save(execution);
        } catch (RuntimeException exception) {
            coordinator.release(execution.id());
            throw exception;
        }
        UUID sessionId = UUID.randomUUID();
        Instant expiresAt = now.plus(ticketTtl);
        sessions.put(sessionId, new SessionState(sessionId, execution, spec, command.actor(), expiresAt, now, maximumOutputBytes));
        audit("COMMAND_TERMINAL_CREATED", execution, command.actor(), command.requestId());
        return new TerminalSessionTicket(sessionId, execution.id(), "/ws/command-sessions/" + sessionId, expiresAt);
    }

    @Override
    public void connect(UUID sessionId, String actor, TerminalClient client) {
        SessionState state = requireTicket(sessionId);
        synchronized (state) {
            if (clock.instant().isAfter(state.expiresAt)) throw new IllegalStateException("terminal session ticket expired");
            if (!state.connected.compareAndSet(false, true)) throw new IllegalStateException("terminal session is already connected");
            // The random, short-lived session id is the single-use ticket. Bind later frames to
            // the WebSocket principal because OIDC can represent REST and upgrade principals differently.
            state.socketActor = actor;
            state.client = client;
            state.lastActivity = clock.instant();
            coordinator.renew(state.execution.id(), idleTimeout.plusSeconds(60));
            CommandExecution running = executions.save(state.execution.running(clock.instant()));
            state.execution = running;
            try {
                state.terminal = terminalPort.open(new KubernetesTerminalRequest(sessionId, credential(running.clusterId()),
                        running.namespace(), state.spec.verb(), state.spec.pod(), state.spec.container(), state.spec.remoteCommand()),
                        (channel, text) -> output(state, channel, text));
                client.status("RUNNING", null, null);
                audit("COMMAND_TERMINAL_CONNECTED", running, state.actor, running.requestId());
                state.terminal.exitCode().whenComplete((code, failure) -> complete(state,
                        failure == null && code != null && code == 0 ? CommandStatus.SUCCEEDED : CommandStatus.FAILED,
                        code == null ? -1 : code, failure == null ? null : failure.getMessage()));
            } catch (RuntimeException exception) {
                complete(state, CommandStatus.FAILED, -1, exception.getMessage());
                throw exception;
            }
        }
    }

    @Override public void input(UUID sessionId, String actor, String data) {
        SessionState state = requireActive(sessionId, actor);
        state.lastActivity = clock.instant();
        coordinator.renew(state.execution.id(), idleTimeout.plusSeconds(60));
        state.terminal.input(data);
    }

    @Override public void resize(UUID sessionId, String actor, int columns, int rows) {
        SessionState state = requireActive(sessionId, actor);
        state.lastActivity = clock.instant();
        coordinator.renew(state.execution.id(), idleTimeout.plusSeconds(60));
        state.terminal.resize(Math.max(20, Math.min(columns, 400)), Math.max(5, Math.min(rows, 200)));
    }

    @Override public void disconnect(UUID sessionId, String actor) {
        SessionState state = sessions.get(sessionId);
        if (state == null || !Objects.equals(state.socketActor, actor) || state.completed.get()) return;
        complete(state, CommandStatus.CANCELED, -1, "browser terminal disconnected");
    }

    @Scheduled(fixedDelay = 30_000)
    void expireSessions() {
        Instant now = clock.instant();
        sessions.values().forEach(state -> {
            if (!state.connected.get() && now.isAfter(state.expiresAt)) complete(state, CommandStatus.CANCELED, -1, "terminal ticket expired");
            else if (state.connected.get() && now.isAfter(state.lastActivity.plus(idleTimeout))) {
                complete(state, CommandStatus.TIMED_OUT, -1, "terminal idle timeout");
            }
        });
    }

    private void output(SessionState state, String channel, String text) {
        if (state.completed.get()) return;
        String safe = sanitizer.sanitize(text);
        state.append(channel, safe);
        state.lastActivity = clock.instant();
        coordinator.renew(state.execution.id(), idleTimeout.plusSeconds(60));
        TerminalClient client = state.client;
        if (client != null) client.output(channel, safe);
    }

    private void complete(SessionState state, CommandStatus status, int exitCode, String error) {
        if (!state.completed.compareAndSet(false, true)) return;
        try {
            String stderr = state.stderr.toString();
            if (error != null && !error.isBlank()) stderr += (stderr.isBlank() ? "" : "\n") + sanitizer.sanitize(error);
            long duration = Duration.between(state.execution.startedAt() == null ? state.execution.createdAt() : state.execution.startedAt(), clock.instant()).toMillis();
            state.execution = executions.save(state.execution.completed(status, state.stdout.toString(), stderr, exitCode,
                    duration, state.truncated, clock.instant()));
            audit("COMMAND_TERMINAL_" + status.name(), state.execution, state.actor, state.execution.requestId());
            if (state.client != null) state.client.status(status.name(), exitCode, error);
        } finally {
            // Notify and close the browser socket before closing Fabric8's watch. ExecWatch#close
            // may interrupt its completion thread, which can otherwise turn a successful shell exit
            // into an abnormal WebSocket close before the final status frame is delivered.
            if (state.client != null) state.client.close();
            closeTerminal(state);
            sessions.remove(state.sessionId, state);
            coordinator.release(state.execution.id());
        }
    }

    private void closeTerminal(SessionState state) {
        KubernetesTerminalSession terminal = state.terminal;
        state.terminal = null;
        if (terminal != null) {
            try { terminal.close(); } catch (RuntimeException ignored) { }
        }
    }

    private SessionState requireTicket(UUID sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) throw new NoSuchElementException("Terminal session not found: " + sessionId);
        return state;
    }

    private SessionState requireActive(UUID sessionId, String actor) {
        SessionState state = requireTicket(sessionId);
        if (!Objects.equals(state.socketActor, actor)) throw new NoSuchElementException("Terminal session not found: " + sessionId);
        if (state.terminal == null || state.completed.get()) throw new IllegalStateException("terminal session is not active");
        return state;
    }

    private KubernetesConnectionCredential credential(UUID clusterId) {
        EncryptedClusterCredential stored = credentials.findByClusterId(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found: " + clusterId));
        String payload = secretCrypto.decrypt(new EncryptedSecret(stored.encryptedPayload(), stored.keyId(), stored.algorithm(), stored.nonce()));
        return new KubernetesConnectionCredential(stored.credentialType(), payload);
    }

    private String json(java.util.List<String> values) {
        try { return objectMapper.writeValueAsString(values); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Failed to serialize kubectl arguments", exception); }
    }

    private String normalizeNamespace(String namespace) {
        return namespace == null || namespace.isBlank() || "__ALL__".equals(namespace) ? "default" : namespace;
    }

    private void audit(String action, CommandExecution execution, String actor, String requestId) {
        audits.save(AuditLog.create(action, "COMMAND_EXECUTION", execution.id().toString(), actor, requestId));
    }

    private static final class SessionState {
        private final UUID sessionId;
        private volatile CommandExecution execution;
        private final TerminalCommandSpec spec;
        private final String actor;
        private final Instant expiresAt;
        private volatile Instant lastActivity;
        private final int outputLimit;
        private final AtomicBoolean connected = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();
        private final StringBuilder stdout = new StringBuilder();
        private final StringBuilder stderr = new StringBuilder();
        private volatile boolean truncated;
        private volatile KubernetesTerminalSession terminal;
        private volatile TerminalClient client;
        private volatile String socketActor;

        private SessionState(UUID sessionId, CommandExecution execution, TerminalCommandSpec spec, String actor,
                Instant expiresAt, Instant lastActivity, int outputLimit) {
            this.sessionId = sessionId; this.execution = execution; this.spec = spec; this.actor = actor;
            this.expiresAt = expiresAt; this.lastActivity = lastActivity; this.outputLimit = outputLimit;
        }

        private synchronized void append(String channel, String text) {
            StringBuilder target = "stderr".equals(channel) ? stderr : stdout;
            int current = stdout.length() + stderr.length();
            if (current >= outputLimit) { truncated = true; return; }
            int remaining = outputLimit - current;
            target.append(text, 0, Math.min(text.length(), remaining));
            if (text.length() > remaining) truncated = true;
        }
    }
}
