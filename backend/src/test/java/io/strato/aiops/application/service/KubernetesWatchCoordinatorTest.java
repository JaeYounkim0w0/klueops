package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AnalysisAssuranceRepositoryPort;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesWatchPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.operations.OperationsModels.RegressionRun;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignalGroup;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesWatchCoordinatorTest {

    @Test
    void startsWatchDeduplicatesSignalsAndClosesFailedRegistration() {
        Cluster cluster = Cluster.register("watch-test", "test", ClusterEnvironment.DEV, ClusterProvider.KIND,
                "local", "test");
        EncryptedSecret encrypted = new EncryptedSecret("cipher", "key", "AES/GCM", "nonce");
        EncryptedClusterCredential credential = EncryptedClusterCredential.create(cluster.id(),
                ClusterCredentialType.KUBECONFIG, encrypted);
        FakeWatchPort watchPort = new FakeWatchPort();
        FakeAssuranceRepository repository = new FakeAssuranceRepository();
        KubernetesWatchCoordinator coordinator = new KubernetesWatchCoordinator(
                new FakeClusterRepository(cluster), new FakeCredentialRepository(credential),
                new PlaintextCrypto(), watchPort, repository, true, 1000, 1);

        coordinator.reconcileWatches();
        awaitState(coordinator, "CONNECTED");
        assertThat(coordinator.statuses()).singleElement()
                .satisfies(status -> assertThat(status.state()).isEqualTo("CONNECTED"));

        var signal = new KubernetesWatchPort.CollectedWatchSignal("default", "Pod", "api", "MODIFIED",
                "CrashLoopBackOff", "Running", "phase=Running, restarts=7", Instant.now());
        watchPort.listener.onSignal(signal);
        watchPort.listener.onSignal(signal);
        assertThat(repository.signals).hasSize(1);
        assertThat(coordinator.statuses().get(0).lastSignalAt()).isNotNull();

        watchPort.listener.onClosed("connection reset");
        assertThat(watchPort.closed).isTrue();
        assertThat(coordinator.statuses()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo("DEGRADED");
            assertThat(status.lastError()).contains("connection reset");
        });
    }

    @Test
    void disabledWatchNeverOpensClusterConnection() {
        Cluster cluster = Cluster.register("watch-disabled", "test", ClusterEnvironment.DEV, ClusterProvider.KIND,
                "local", "test");
        FakeWatchPort watchPort = new FakeWatchPort();
        KubernetesWatchCoordinator coordinator = new KubernetesWatchCoordinator(
                new FakeClusterRepository(cluster), new FakeCredentialRepository(null), new PlaintextCrypto(),
                watchPort, new FakeAssuranceRepository(), false, 1000, 1);

        coordinator.reconcileWatches();

        assertThat(watchPort.openCount).isZero();
        assertThat(coordinator.statuses().get(0).state()).isEqualTo("DISABLED");
    }

    @Test
    void exposesFailedStateWhenWatchStartupExceedsTimeout() {
        Cluster cluster = Cluster.register("watch-timeout", "test", ClusterEnvironment.DEV, ClusterProvider.KIND,
                "local", "test");
        EncryptedSecret encrypted = new EncryptedSecret("cipher", "key", "AES/GCM", "nonce");
        EncryptedClusterCredential credential = EncryptedClusterCredential.create(cluster.id(),
                ClusterCredentialType.KUBECONFIG, encrypted);
        SlowWatchPort watchPort = new SlowWatchPort();
        KubernetesWatchCoordinator coordinator = new KubernetesWatchCoordinator(
                new FakeClusterRepository(cluster), new FakeCredentialRepository(credential),
                new PlaintextCrypto(), watchPort, new FakeAssuranceRepository(), true, 1000, 1);

        try {
            coordinator.reconcileWatches();
            awaitState(coordinator, "FAILED");

            assertThat(coordinator.statuses()).singleElement().satisfies(status ->
                    assertThat(status.lastError()).contains("timed out after 1000ms"));
        } finally {
            watchPort.release.countDown();
            coordinator.shutdown();
        }
    }

    @Test
    void fallsBackToPollingAfterConfiguredWatchFailureThreshold() {
        Cluster cluster = Cluster.register("watch-polling", "test", ClusterEnvironment.DEV, ClusterProvider.KIND,
                "local", "test");
        EncryptedSecret encrypted = new EncryptedSecret("cipher", "key", "AES/GCM", "nonce");
        EncryptedClusterCredential credential = EncryptedClusterCredential.create(cluster.id(),
                ClusterCredentialType.KUBECONFIG, encrypted);
        KubernetesWatchCoordinator coordinator = new KubernetesWatchCoordinator(
                new FakeClusterRepository(cluster), new FakeCredentialRepository(credential),
                new PlaintextCrypto(), new FailingWatchPort(), new FakeAssuranceRepository(),
                true, 1000, 1, 1);

        try {
            coordinator.reconcileWatches();
            awaitState(coordinator, "POLLING");
            assertThat(coordinator.statuses()).singleElement().satisfies(status -> {
                assertThat(status.consecutiveFailures()).isEqualTo(1);
                assertThat(status.lastError()).contains("watch unavailable");
                assertThat(status.nextRetryAt()).isNotNull();
            });
        } finally {
            coordinator.shutdown();
        }
    }

    private static void awaitState(KubernetesWatchCoordinator coordinator, String expected) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (expected.equals(coordinator.statuses().get(0).state())) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while awaiting watch state", exception);
            }
        }
        throw new AssertionError("Watch did not reach state " + expected + ": " + coordinator.statuses());
    }

    private static final class FakeClusterRepository implements ClusterRepositoryPort {
        private final Cluster cluster;

        private FakeClusterRepository(Cluster cluster) {
            this.cluster = cluster;
        }

        @Override
        public Cluster save(Cluster value) {
            return value;
        }

        @Override
        public Optional<Cluster> findById(UUID clusterId) {
            return cluster.id().equals(clusterId) ? Optional.of(cluster) : Optional.empty();
        }

        @Override
        public List<Cluster> findAll() {
            return List.of(cluster);
        }
    }

    private static final class FakeCredentialRepository implements ClusterCredentialRepositoryPort {
        private final EncryptedClusterCredential credential;

        private FakeCredentialRepository(EncryptedClusterCredential credential) {
            this.credential = credential;
        }

        @Override
        public EncryptedClusterCredential save(EncryptedClusterCredential value) {
            return value;
        }

        @Override
        public Optional<EncryptedClusterCredential> findByClusterId(UUID clusterId) {
            return Optional.ofNullable(credential);
        }
    }

    private static final class PlaintextCrypto implements SecretCryptoPort {
        @Override
        public EncryptedSecret encrypt(String plaintext) {
            return new EncryptedSecret(plaintext, "key", "NONE", "nonce");
        }

        @Override
        public String decrypt(EncryptedSecret encryptedSecret) {
            return "apiVersion: v1";
        }
    }

    private static final class FakeWatchPort implements KubernetesWatchPort {
        private WatchListener listener;
        private int openCount;
        private boolean closed;

        @Override
        public WatchRegistration watch(UUID clusterId, KubernetesConnectionCredential credential,
                                       WatchListener listener) {
            this.listener = listener;
            openCount++;
            return () -> closed = true;
        }
    }

    private static final class SlowWatchPort implements KubernetesWatchPort {
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public WatchRegistration watch(UUID clusterId, KubernetesConnectionCredential credential,
                                       WatchListener listener) {
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Watch startup interrupted", exception);
            }
            return () -> {
            };
        }
    }

    private static final class FailingWatchPort implements KubernetesWatchPort {
        @Override
        public WatchRegistration watch(UUID clusterId, KubernetesConnectionCredential credential,
                                       WatchListener listener) {
            throw new IllegalStateException("watch unavailable");
        }
    }

    private static final class FakeAssuranceRepository implements AnalysisAssuranceRepositoryPort {
        private final List<WatchSignal> signals = new ArrayList<>();
        private final List<WatchSignalGroup> groups = new ArrayList<>();

        @Override
        public void saveWatchSignal(WatchSignal signal) {
            signals.add(signal);
        }

        @Override
        public List<WatchSignal> findWatchSignals(UUID clusterId, String namespace, int limit) {
            return List.copyOf(signals);
        }

        @Override
        public WatchSignalGroup saveWatchSignalGroup(WatchSignalGroup group) {
            groups.removeIf(item -> item.id().equals(group.id()));
            groups.add(group);
            return group;
        }

        @Override
        public Optional<WatchSignalGroup> findWatchSignalGroup(UUID groupId) {
            return groups.stream().filter(item -> item.id().equals(groupId)).findFirst();
        }

        @Override
        public Optional<WatchSignalGroup> findWatchSignalGroupByFingerprint(String fingerprint) {
            return groups.stream().filter(item -> item.fingerprint().equals(fingerprint)).findFirst();
        }

        @Override
        public List<WatchSignalGroup> findWatchSignalGroups(UUID clusterId, String namespace, String state, int limit) {
            return List.copyOf(groups);
        }

        @Override
        public RegressionRun saveRegressionRun(RegressionRun run) {
            return run;
        }

        @Override
        public Optional<RegressionRun> findRegressionRun(UUID runId) {
            return Optional.empty();
        }

        @Override
        public List<RegressionRun> findRegressionRuns(int limit) {
            return List.of();
        }
    }
}
