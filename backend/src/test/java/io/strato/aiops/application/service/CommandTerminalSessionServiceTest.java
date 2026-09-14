package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.in.StartCommandExecutionCommand;
import io.strato.aiops.application.port.in.TerminalClient;
import io.strato.aiops.application.port.out.*;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.cluster.*;
import io.strato.aiops.domain.command.CommandExecution;
import io.strato.aiops.domain.command.CommandStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandTerminalSessionServiceTest {
    private final Instant now = Instant.parse("2026-09-08T00:00:00Z");
    private final Cluster cluster = new Cluster(UUID.randomUUID(), "dev", null, ClusterEnvironment.DEV,
            ClusterProvider.KIND, "local", ClusterStatus.REGISTERED, "admin", now, now);
    private final Map<UUID, CommandExecution> executions = new HashMap<>();
    private final List<String> lifecycle = new ArrayList<>();
    private final FakeTerminalPort terminalPort = new FakeTerminalPort(lifecycle);
    private final CommandTerminalSessionService service = service();

    @Test
    void runsOwnedExecTtyAndPersistsSuccessfulExit() {
        var ticket = service.create(new StartCommandExecutionCommand(cluster.id(), null, "default",
                "kubectl exec -it api-0 -c server -- /bin/sh", null, true, "operator", "request-1"));
        FakeClient client = new FakeClient(lifecycle);

        service.connect(ticket.sessionId(), "operator", client);
        service.input(ticket.sessionId(), "operator", "echo ready\r");
        service.resize(ticket.sessionId(), "operator", 132, 42);
        terminalPort.session.exit.complete(0);

        assertThat(terminalPort.request.pod()).isEqualTo("api-0");
        assertThat(terminalPort.request.container()).isEqualTo("server");
        assertThat(terminalPort.request.remoteCommand()).containsExactly("/bin/sh");
        assertThat(terminalPort.session.input).hasToString("echo ready\r");
        assertThat(terminalPort.session.columns).isEqualTo(132);
        assertThat(executions.get(ticket.executionId()).status()).isEqualTo(CommandStatus.SUCCEEDED);
        assertThat(client.statuses).contains("RUNNING", "SUCCEEDED");
        assertThat(lifecycle).endsWith("status-SUCCEEDED", "client-closed", "terminal-closed");
    }

    @Test
    void bindsTheSingleUseTicketToTheWebSocketPrincipalAndRejectsOtherFrames() {
        var ticket = service.create(new StartCommandExecutionCommand(cluster.id(), null, "default",
                "kubectl exec -it api-0 -- sh", null, true, "operator", "request-2"));
        FakeClient client = new FakeClient();

        service.connect(ticket.sessionId(), "oidc-websocket-subject", client);

        assertThatThrownBy(() -> service.input(ticket.sessionId(), "other-user", "id\r"))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.connect(ticket.sessionId(), "other-user", new FakeClient()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already connected");
        assertThatThrownBy(() -> service.create(new StartCommandExecutionCommand(cluster.id(), null, "default",
                "kubectl get pods", null, true, "operator", "request-3")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("terminal session requires");
    }

    private CommandTerminalSessionService service() {
        ClusterRepositoryPort clusterRepository = new ClusterRepositoryPort() {
            @Override public Cluster save(Cluster value) { return value; }
            @Override public Optional<Cluster> findById(UUID id) { return id.equals(cluster.id()) ? Optional.of(cluster) : Optional.empty(); }
            @Override public List<Cluster> findAll() { return List.of(cluster); }
        };
        EncryptedClusterCredential credential = new EncryptedClusterCredential(UUID.randomUUID(), cluster.id(),
                ClusterCredentialType.KUBECONFIG, "cipher", "key", "AES", "nonce", now);
        ClusterCredentialRepositoryPort credentialRepository = new ClusterCredentialRepositoryPort() {
            @Override public EncryptedClusterCredential save(EncryptedClusterCredential value) { return value; }
            @Override public Optional<EncryptedClusterCredential> findByClusterId(UUID id) { return Optional.of(credential); }
        };
        CommandExecutionRepositoryPort executionRepository = new CommandExecutionRepositoryPort() {
            @Override public CommandExecution save(CommandExecution value) { executions.put(value.id(), value); return value; }
            @Override public Optional<CommandExecution> findById(UUID id) { return Optional.ofNullable(executions.get(id)); }
            @Override public List<CommandExecution> findRecent(UUID clusterId, String namespace, int limit) { return List.copyOf(executions.values()); }
            @Override public List<CommandExecution> findIncompleteBefore(Instant cutoff, int limit) { return List.of(); }
        };
        SecretCryptoPort crypto = new SecretCryptoPort() {
            @Override public EncryptedSecret encrypt(String plaintext) { return new EncryptedSecret(plaintext, "key", "AES", "nonce"); }
            @Override public String decrypt(EncryptedSecret encryptedSecret) { return "apiVersion: v1\nkind: Config"; }
        };
        AuditLogRepositoryPort audit = new AuditLogRepositoryPort() {
            @Override public AuditLog save(AuditLog value) { return value; }
        };
        CommandExecutionAdmissionPort admission = new CommandExecutionAdmissionPort() {
            @Override public void acquire(AdmissionRequest request) { }
            @Override public void renew(UUID executionId, Instant expiresAt) { }
            @Override public void release(UUID executionId) { }
            @Override public boolean isActive(UUID executionId, Instant at) { return true; }
        };
        CommandExecutionCoordinator coordinator = new CommandExecutionCoordinator(admission, Clock.fixed(now, ZoneOffset.UTC),
                5, 3, 50, 20, 30, 120);
        AnalysisSessionRepositoryPort analysisRepository = new AnalysisSessionRepositoryPort() {
            @Override public io.strato.aiops.domain.analysis.AnalysisSession save(io.strato.aiops.domain.analysis.AnalysisSession value) { return value; }
            @Override public Optional<io.strato.aiops.domain.analysis.AnalysisSession> findById(UUID id) { return Optional.empty(); }
            @Override public List<io.strato.aiops.domain.analysis.AnalysisSession> findByIds(Set<UUID> ids) { return List.of(); }
            @Override public Optional<io.strato.aiops.domain.analysis.AnalysisSession> findByAsyncJobId(UUID id) { return Optional.empty(); }
            @Override public Optional<io.strato.aiops.domain.analysis.AnalysisSession> findRunningByScope(UUID c, UUID a, String n) { return Optional.empty(); }
            @Override public Optional<io.strato.aiops.domain.analysis.AnalysisSession> findLatestSucceededByScope(UUID c, UUID a, String n) { return Optional.empty(); }
            @Override public List<io.strato.aiops.domain.analysis.AnalysisSession> findRecent(UUID c, UUID a, String n, int limit) { return List.of(); }
            @Override public void deleteById(UUID id) { }
        };
        return new CommandTerminalSessionService(clusterRepository, credentialRepository, executionRepository, crypto,
                terminalPort, audit, new KubectlCommandTokenizer(), new TerminalCommandParser(), new CommandOutputSanitizer(),
                new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC), coordinator,
                new CommandSourceAnalysisValidator(analysisRepository), 30, 900, 65536);
    }

    private static final class FakeTerminalPort implements KubernetesTerminalPort {
        private KubernetesTerminalRequest request;
        private final FakeTerminalSession session;
        private FakeTerminalPort(List<String> lifecycle) { this.session = new FakeTerminalSession(lifecycle); }
        @Override public KubernetesTerminalSession open(KubernetesTerminalRequest request, KubernetesTerminalListener listener) {
            this.request = request;
            listener.onOutput("stdout", "connected\r\n");
            return session;
        }
    }

    private static final class FakeTerminalSession implements KubernetesTerminalSession {
        private final StringBuilder input = new StringBuilder();
        private final CompletableFuture<Integer> exit = new CompletableFuture<>();
        private final List<String> lifecycle;
        private int columns;
        private FakeTerminalSession(List<String> lifecycle) { this.lifecycle = lifecycle; }
        @Override public void input(String data) { input.append(data); }
        @Override public void resize(int columns, int rows) { this.columns = columns; }
        @Override public CompletableFuture<Integer> exitCode() { return exit; }
        @Override public void close() { lifecycle.add("terminal-closed"); }
    }

    private static final class FakeClient implements TerminalClient {
        private final List<String> statuses = new ArrayList<>();
        private final List<String> lifecycle;
        private FakeClient() { this(new ArrayList<>()); }
        private FakeClient(List<String> lifecycle) { this.lifecycle = lifecycle; }
        @Override public void output(String channel, String text) { }
        @Override public void status(String status, Integer exitCode, String message) {
            statuses.add(status);
            lifecycle.add("status-" + status);
        }
        @Override public void close() { lifecycle.add("client-closed"); }
    }
}
