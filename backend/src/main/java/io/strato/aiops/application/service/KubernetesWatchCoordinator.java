package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AnalysisAssuranceRepositoryPort;
import io.strato.aiops.application.port.in.WatchSignalTriageUseCase;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesWatchPort;
import io.strato.aiops.application.port.out.OperationsEvolutionRepositoryPort;
import io.strato.aiops.application.port.out.RuntimeLeasePort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.operations.OperationsModels.WatchRuntimeStatus;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.WatchContinuity;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class KubernetesWatchCoordinator {

    private final ClusterRepositoryPort clusterRepository;
    private final ClusterCredentialRepositoryPort credentialRepository;
    private final SecretCryptoPort secretCryptoPort;
    private final KubernetesWatchPort watchPort;
    private final AnalysisAssuranceRepositoryPort assuranceRepository;
    private final WatchSignalTriageUseCase triageUseCase;
    private final OperationsEvolutionRepositoryPort evolutionRepository;
    private final OperationsEventStream eventStream;
    private final RuntimeLeasePort runtimeLeasePort;
    private final boolean enabled;
    private final long startupTimeoutMs;
    private final long retryBaseMs;
    private final long retryMaxMs;
    private final long renewalMs;
    private final int pollingFailureThreshold;
    private final int pollingRecoveryThreshold;
    private final long pollingIntervalMs;
    private final ExecutorService startupExecutor;
    private final Map<UUID, KubernetesWatchPort.WatchRegistration> registrations = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingStarts = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingPolls = new ConcurrentHashMap<>();
    private final Map<UUID, Long> attemptSequences = new ConcurrentHashMap<>();
    private final Map<UUID, WatchRuntimeStatus> statuses = new ConcurrentHashMap<>();
    private final Map<String, Instant> recentlyPersisted = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> successfulPolls = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> lastPolledSignals = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> nextRetries = new ConcurrentHashMap<>();
    private final Set<UUID> pollingClusters = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pausedClusters = ConcurrentHashMap.newKeySet();

    /** KubernetesWatchCoordinator 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    @Autowired
    public KubernetesWatchCoordinator(
            ClusterRepositoryPort clusterRepository,
            ClusterCredentialRepositoryPort credentialRepository,
            SecretCryptoPort secretCryptoPort,
            KubernetesWatchPort watchPort,
            AnalysisAssuranceRepositoryPort assuranceRepository,
            WatchSignalTriageUseCase triageUseCase,
            @Value("${aiops.kubernetes.watch.enabled:true}") boolean enabled,
            @Value("${aiops.kubernetes.watch.startup-timeout-ms:15000}") long startupTimeoutMs,
            @Value("${aiops.kubernetes.watch.startup-parallelism:2}") int startupParallelism,
            @Value("${aiops.kubernetes.watch.retry-base-ms:5000}") long retryBaseMs,
            @Value("${aiops.kubernetes.watch.retry-max-ms:120000}") long retryMaxMs,
            @Value("${aiops.kubernetes.watch.renewal-ms:600000}") long renewalMs,
            @Value("${aiops.kubernetes.watch.polling-failure-threshold:3}") int pollingFailureThreshold,
            @Value("${aiops.kubernetes.watch.polling-recovery-threshold:3}") int pollingRecoveryThreshold,
            @Value("${aiops.kubernetes.watch.polling-interval-ms:30000}") long pollingIntervalMs,
            OperationsEvolutionRepositoryPort evolutionRepository,
            OperationsEventStream eventStream,
            RuntimeLeasePort runtimeLeasePort
    ) {
        this.clusterRepository = clusterRepository;
        this.credentialRepository = credentialRepository;
        this.secretCryptoPort = secretCryptoPort;
        this.watchPort = watchPort;
        this.assuranceRepository = assuranceRepository;
        this.triageUseCase = triageUseCase;
        this.evolutionRepository = evolutionRepository;
        this.eventStream = eventStream;
        this.runtimeLeasePort = runtimeLeasePort;
        this.enabled = enabled;
        this.startupTimeoutMs = Math.max(1000, startupTimeoutMs);
        this.retryBaseMs = Math.max(1000, retryBaseMs);
        this.retryMaxMs = Math.max(this.retryBaseMs, retryMaxMs);
        this.renewalMs = Math.max(60000, renewalMs);
        this.pollingFailureThreshold = Math.max(1, pollingFailureThreshold);
        this.pollingRecoveryThreshold = Math.max(1, pollingRecoveryThreshold);
        this.pollingIntervalMs = Math.max(5000, pollingIntervalMs);
        AtomicInteger threadNumber = new AtomicInteger();
        this.startupExecutor = Executors.newFixedThreadPool(Math.max(1, startupParallelism), runnable -> {
            Thread thread = new Thread(runnable, "kubernetes-watch-startup-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** KubernetesWatchCoordinator 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    KubernetesWatchCoordinator(
            ClusterRepositoryPort clusterRepository,
            ClusterCredentialRepositoryPort credentialRepository,
            SecretCryptoPort secretCryptoPort,
            KubernetesWatchPort watchPort,
            AnalysisAssuranceRepositoryPort assuranceRepository,
            boolean enabled,
            long startupTimeoutMs,
            int startupParallelism
    ) {
        this(clusterRepository, credentialRepository, secretCryptoPort, watchPort, assuranceRepository,
                signal -> { }, enabled, startupTimeoutMs, startupParallelism, 5000, 120000, 600000,
                3, 3, 30000, null, null, RuntimeLeasePort.localOnly());
    }

    /** KubernetesWatchCoordinator 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    KubernetesWatchCoordinator(
            ClusterRepositoryPort clusterRepository,
            ClusterCredentialRepositoryPort credentialRepository,
            SecretCryptoPort secretCryptoPort,
            KubernetesWatchPort watchPort,
            AnalysisAssuranceRepositoryPort assuranceRepository,
            boolean enabled,
            long startupTimeoutMs,
            int startupParallelism,
            int pollingFailureThreshold
    ) {
        this(clusterRepository, credentialRepository, secretCryptoPort, watchPort, assuranceRepository,
                signal -> { }, enabled, startupTimeoutMs, startupParallelism, 1000, 120000, 600000,
                pollingFailureThreshold, 3, 30000, null, null, RuntimeLeasePort.localOnly());
    }

    /** KubernetesWatchCoordinator의 reconcileWatches 처리에 필요한 업무 로직을 수행한다. */
    @Scheduled(initialDelayString = "${aiops.kubernetes.watch.initial-delay-ms:15000}",
            fixedDelayString = "${aiops.kubernetes.watch.reconcile-delay-ms:30000}")
    public void reconcileWatches() {
        List<Cluster> clusters = clusterRepository.findAll();
        Set<UUID> activeIds = clusters.stream().map(Cluster::id).collect(java.util.stream.Collectors.toSet());
        registrations.keySet().stream().filter(id -> !activeIds.contains(id)).toList().forEach(id -> {
            close(id);
            runtimeLeasePort.release(watchLeaseKey(id));
        });
        for (Cluster cluster : clusters) {
            if (!enabled) {
                statuses.put(cluster.id(), new WatchRuntimeStatus(cluster.id(), cluster.name(), "DISABLED",
                        null, null, 0, null, Instant.now(), null, null, 0, false));
            } else if (!runtimeLeasePort.acquireOrRenew(watchLeaseKey(cluster.id()), Duration.ofSeconds(90))) {
                close(cluster.id());
                statuses.put(cluster.id(), runtime(cluster, "STANDBY", statuses.get(cluster.id()), null, false));
            } else if (pausedClusters.contains(cluster.id())) {
                WatchRuntimeStatus current = statuses.get(cluster.id());
                statuses.put(cluster.id(), runtime(cluster, "PAUSED", current, null, true));
            } else if (pollingClusters.contains(cluster.id())) {
                if (!pendingPolls.containsKey(cluster.id()) && retryReady(cluster.id())) {
                    poll(cluster);
                }
            } else if (shouldRenew(cluster.id())) {
                close(cluster.id());
                start(cluster);
            } else if (!registrations.containsKey(cluster.id()) && !pendingStarts.containsKey(cluster.id())
                    && retryReady(cluster.id())) {
                start(cluster);
            }
        }
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(5));
        recentlyPersisted.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    /** KubernetesWatchCoordinator의 statuses 처리에 필요한 업무 로직을 수행한다. */
    public List<WatchRuntimeStatus> statuses() {
        List<WatchRuntimeStatus> result = new ArrayList<>();
        for (Cluster cluster : clusterRepository.findAll()) {
            result.add(statuses.getOrDefault(cluster.id(), new WatchRuntimeStatus(cluster.id(), cluster.name(),
                    enabled ? "STARTING" : "DISABLED", null, null, 0, null, Instant.now(),
                    null, nextRetries.get(cluster.id()), consecutiveFailures.getOrDefault(cluster.id(), 0),
                    pausedClusters.contains(cluster.id()))));
        }
        result.sort(Comparator.comparing(WatchRuntimeStatus::clusterName));
        return List.copyOf(result);
    }

    /** KubernetesWatchCoordinator의 signals 처리에 필요한 업무 로직을 수행한다. */
    public List<WatchSignal> signals(UUID clusterId, String namespace, int limit) {
        return assuranceRepository.findWatchSignals(clusterId, namespace, limit);
    }

    /** KubernetesWatchCoordinator의 restart 처리에 필요한 업무 로직을 수행한다. */
    public WatchRuntimeStatus restart(UUID clusterId) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Cluster not found: " + clusterId));
        if (!enabled) {
            WatchRuntimeStatus status = new WatchRuntimeStatus(cluster.id(), cluster.name(), "DISABLED",
                    null, null, 0, null, Instant.now(), null, null, 0, false);
            statuses.put(cluster.id(), status);
            return status;
        }
        pausedClusters.remove(clusterId);
        pollingClusters.remove(clusterId);
        successfulPolls.remove(clusterId);
        lastPolledSignals.remove(clusterId);
        consecutiveFailures.remove(clusterId);
        nextRetries.remove(clusterId);
        close(clusterId);
        start(cluster);
        return statuses.get(clusterId);
    }

    /** KubernetesWatchCoordinator의 pause 처리에 필요한 업무 로직을 수행한다. */
    public WatchRuntimeStatus pause(UUID clusterId) {
        Cluster cluster = requireCluster(clusterId);
        pausedClusters.add(clusterId);
        pollingClusters.remove(clusterId);
        successfulPolls.remove(clusterId);
        lastPolledSignals.remove(clusterId);
        close(clusterId);
        nextRetries.remove(clusterId);
        WatchRuntimeStatus status = runtime(cluster, "PAUSED", statuses.get(clusterId), null, true);
        statuses.put(clusterId, status);
        return status;
    }

    /** KubernetesWatchCoordinator의 resume 처리에 필요한 업무 로직을 수행한다. */
    public WatchRuntimeStatus resume(UUID clusterId) {
        Cluster cluster = requireCluster(clusterId);
        pausedClusters.remove(clusterId);
        pollingClusters.remove(clusterId);
        successfulPolls.remove(clusterId);
        lastPolledSignals.remove(clusterId);
        consecutiveFailures.remove(clusterId);
        nextRetries.remove(clusterId);
        close(clusterId);
        start(cluster);
        return statuses.get(clusterId);
    }

    /** KubernetesWatchCoordinator의 start 처리에 필요한 업무 로직을 수행한다. */
    private void start(Cluster cluster) {
        WatchRuntimeStatus previous = statuses.get(cluster.id());
        int reconnects = previous == null ? 0 : previous.reconnectCount() + 1;
        long attempt = attemptSequences.merge(cluster.id(), 1L, Long::sum);
        statuses.put(cluster.id(), new WatchRuntimeStatus(cluster.id(), cluster.name(), "CONNECTING",
                null, previous == null ? null : previous.lastSignalAt(), reconnects, null, Instant.now(),
                previous == null ? null : previous.lastHeartbeatAt(), null,
                consecutiveFailures.getOrDefault(cluster.id(), 0), false));
        CompletableFuture<Void> pending = CompletableFuture.runAsync(
                () -> startBlocking(cluster, previous, reconnects, attempt), startupExecutor);
        pendingStarts.put(cluster.id(), pending);
        pending.orTimeout(startupTimeoutMs, TimeUnit.MILLISECONDS).whenComplete((ignored, throwable) -> {
            pendingStarts.remove(cluster.id(), pending);
            if (throwable == null || !isCurrentAttempt(cluster.id(), attempt)) return;
            attemptSequences.compute(cluster.id(), (ignoredId, current) -> current == null ? 1L : current + 1L);
            Throwable cause = unwrap(throwable);
            String message = cause instanceof TimeoutException
                    ? "Kubernetes watch connection timed out after " + startupTimeoutMs + "ms"
                    : clean(cause.getMessage());
            registerFailure(cluster, previous, reconnects, message, "FAILED");
        });
    }

    /** KubernetesWatchCoordinator의 startBlocking 처리에 필요한 업무 로직을 수행한다. */
    private void startBlocking(Cluster cluster, WatchRuntimeStatus previous, int reconnects, long attempt) {
        try {
            AtomicBoolean closedDuringStart = new AtomicBoolean(false);
            AtomicReference<String> closeMessage = new AtomicReference<>();
            EncryptedClusterCredential stored = credentialRepository.findByClusterId(cluster.id())
                    .orElseThrow(() -> new IllegalStateException("Cluster credential not found"));
            String payload = secretCryptoPort.decrypt(new EncryptedSecret(stored.encryptedPayload(), stored.keyId(),
                    stored.algorithm(), stored.nonce()));
            WatchContinuity continuity = evolutionRepository == null ? null
                    : evolutionRepository.findWatchContinuity(cluster.id()).orElse(null);
            KubernetesWatchPort.WatchCursor cursor = continuity == null ? KubernetesWatchPort.WatchCursor.empty()
                    : new KubernetesWatchPort.WatchCursor(
                    continuity.podResourceVersion(), continuity.eventResourceVersion());
            KubernetesWatchPort.WatchRegistration registration = watchPort.watch(cluster.id(),
                    new KubernetesConnectionCredential(stored.credentialType(), payload), cursor,
                    new KubernetesWatchPort.WatchListener() {
                        /** 익명 구현체의 onSignal 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
                        @Override
                        public void onSignal(KubernetesWatchPort.CollectedWatchSignal signal) {
                            persist(cluster, signal);
                        }

                        /** 익명 구현체의 onHeartbeat 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
                        @Override
                        public void onHeartbeat(Instant observedAt) {
                            heartbeat(cluster, observedAt);
                        }

                        /** 익명 구현체의 onCheckpoint 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
                        @Override
                        public void onCheckpoint(String podResourceVersion, String eventResourceVersion,
                                                 Instant reconciledAt, int gapSignalCount) {
                            checkpoint(cluster, podResourceVersion, eventResourceVersion, reconciledAt, gapSignalCount);
                        }

                        /** 익명 구현체의 onClosed 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
                        @Override
                        public void onClosed(String message) {
                            if (!isCurrentAttempt(cluster.id(), attempt)) return;
                            KubernetesWatchPort.WatchRegistration active = registrations.remove(cluster.id());
                            if (active == null) {
                                closedDuringStart.set(true);
                                closeMessage.set(message);
                            } else {
                                try {
                                    active.close();
                                } catch (RuntimeException ignored) {
                                    // The failed watch may already be closed by Fabric8.
                                }
                            }
                            WatchRuntimeStatus current = statuses.get(cluster.id());
                            registerFailure(cluster, current, current == null ? reconnects : current.reconnectCount(),
                                    clean(message), "DEGRADED");
                        }
                    });
            if (closedDuringStart.get()) {
                registration.close();
                throw new IllegalStateException(closeMessage.get() == null
                        ? "Kubernetes watch closed during startup" : closeMessage.get());
            }
            if (!isCurrentAttempt(cluster.id(), attempt)) {
                registration.close();
                return;
            }
            registrations.put(cluster.id(), registration);
            pollingClusters.remove(cluster.id());
            successfulPolls.remove(cluster.id());
            lastPolledSignals.remove(cluster.id());
            consecutiveFailures.remove(cluster.id());
            nextRetries.remove(cluster.id());
            statuses.put(cluster.id(), new WatchRuntimeStatus(cluster.id(), cluster.name(), "CONNECTED",
                    Instant.now(), previous == null ? null : previous.lastSignalAt(), reconnects, null, Instant.now(),
                    Instant.now(), null, 0, false));
            publishStatus(cluster.id());
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    /** KubernetesWatchCoordinator의 persist 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void persist(Cluster cluster, KubernetesWatchPort.CollectedWatchSignal signal) {
        persist(cluster, signal, "CONNECTED");
    }

    /** KubernetesWatchCoordinator의 persist 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void persist(Cluster cluster, KubernetesWatchPort.CollectedWatchSignal signal, String state) {
        String fingerprint = signalFingerprint(cluster, signal);
        Instant now = Instant.now();
        Instant previous = recentlyPersisted.put(fingerprint, now);
        if (previous != null && previous.isAfter(now.minusSeconds(10))) return;
        WatchSignal stored = new WatchSignal(UUID.randomUUID(), cluster.id(), cluster.name(), signal.namespace(),
                text(signal.resourceKind()), text(signal.resourceName()), text(signal.action()), signal.reason(),
                signal.status(), clean(signal.summary()), signal.observedAt() == null ? now : signal.observedAt());
        assuranceRepository.saveWatchSignal(stored);
        triageUseCase.ingestWatchSignal(stored);
        WatchRuntimeStatus current = statuses.get(cluster.id());
        statuses.put(cluster.id(), new WatchRuntimeStatus(cluster.id(), cluster.name(), state,
                current == null ? now : current.connectedAt(), stored.observedAt(),
                current == null ? 0 : current.reconnectCount(), null, now, now, null, 0, false));
        if (eventStream != null) eventStream.publish("watch-signal", stored);
    }

    /** KubernetesWatchCoordinator의 heartbeat 처리에 필요한 업무 로직을 수행한다. */
    private void heartbeat(Cluster cluster, Instant observedAt) {
        WatchRuntimeStatus current = statuses.get(cluster.id());
        Instant now = observedAt == null ? Instant.now() : observedAt;
        statuses.put(cluster.id(), new WatchRuntimeStatus(cluster.id(), cluster.name(),
                current == null ? "CONNECTED" : current.state(), current == null ? now : current.connectedAt(),
                current == null ? null : current.lastSignalAt(), current == null ? 0 : current.reconnectCount(),
                current == null ? null : current.lastError(), Instant.now(), now, null, 0, false));
        publishStatus(cluster.id());
    }

    /** KubernetesWatchCoordinator의 registerFailure 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void registerFailure(Cluster cluster, WatchRuntimeStatus previous, int reconnects,
                                 String message, String state) {
        int failures = consecutiveFailures.merge(cluster.id(), 1, Integer::sum);
        boolean polling = failures >= pollingFailureThreshold;
        if (polling) {
            pollingClusters.add(cluster.id());
        }
        long exponent = 1L << Math.min(10, Math.max(0, failures - 1));
        long delay = polling ? pollingIntervalMs : Math.min(retryMaxMs, retryBaseMs * exponent);
        long jitter = Math.floorMod(cluster.id().getLeastSignificantBits(), Math.max(1, delay / 5));
        Instant retryAt = Instant.now().plusMillis(delay + jitter);
        nextRetries.put(cluster.id(), retryAt);
        statuses.put(cluster.id(), new WatchRuntimeStatus(cluster.id(), cluster.name(), polling ? "POLLING" : state,
                previous == null ? null : previous.connectedAt(), previous == null ? null : previous.lastSignalAt(),
                reconnects, message, Instant.now(), previous == null ? null : previous.lastHeartbeatAt(),
                retryAt, failures, false));
        if (evolutionRepository != null) {
            WatchContinuity current = evolutionRepository.findWatchContinuity(cluster.id()).orElse(null);
            evolutionRepository.saveWatchContinuity(new WatchContinuity(cluster.id(),
                    current == null ? null : current.podResourceVersion(),
                    current == null ? null : current.eventResourceVersion(), polling ? "POLLING" : "DEGRADED",
                    current == null ? 0 : current.gapSignalCount(),
                    current == null ? null : current.lastReconciledAt(), message, Instant.now()));
        }
        publishStatus(cluster.id());
    }

    /** KubernetesWatchCoordinator의 poll 처리에 필요한 업무 로직을 수행한다. */
    private void poll(Cluster cluster) {
        WatchRuntimeStatus previous = statuses.get(cluster.id());
        statuses.put(cluster.id(), runtime(cluster, "POLLING", previous,
                previous == null ? null : previous.lastError(), false));
        CompletableFuture<Void> pending = CompletableFuture.runAsync(() -> pollBlocking(cluster), startupExecutor);
        pendingPolls.put(cluster.id(), pending);
        pending.orTimeout(startupTimeoutMs, TimeUnit.MILLISECONDS).whenComplete((ignored, throwable) -> {
            pendingPolls.remove(cluster.id(), pending);
            if (throwable == null) {
                return;
            }
            successfulPolls.remove(cluster.id());
            Throwable cause = unwrap(throwable);
            String message = cause instanceof TimeoutException
                    ? "Kubernetes fallback polling timed out after " + startupTimeoutMs + "ms"
                    : clean(cause.getMessage());
            WatchRuntimeStatus current = statuses.get(cluster.id());
            registerFailure(cluster, current, current == null ? 0 : current.reconnectCount(), message, "POLLING");
        });
    }

    /** KubernetesWatchCoordinator의 pollBlocking 처리에 필요한 업무 로직을 수행한다. */
    private void pollBlocking(Cluster cluster) {
        EncryptedClusterCredential stored = credentialRepository.findByClusterId(cluster.id())
                .orElseThrow(() -> new IllegalStateException("Cluster credential not found"));
        String payload = secretCryptoPort.decrypt(new EncryptedSecret(stored.encryptedPayload(), stored.keyId(),
                stored.algorithm(), stored.nonce()));
        WatchContinuity continuity = evolutionRepository == null ? null
                : evolutionRepository.findWatchContinuity(cluster.id()).orElse(null);
        KubernetesWatchPort.PollResult result = watchPort.poll(cluster.id(),
                new KubernetesConnectionCredential(stored.credentialType(), payload),
                continuity == null ? KubernetesWatchPort.WatchCursor.empty()
                        : new KubernetesWatchPort.WatchCursor(
                        continuity.podResourceVersion(), continuity.eventResourceVersion()));
        Set<String> previousSignals = lastPolledSignals.getOrDefault(cluster.id(), Set.of());
        Set<String> currentSignals = result.signals().stream()
                .map(signal -> signalFingerprint(cluster, signal))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        result.signals().stream()
                .filter(signal -> !previousSignals.contains(signalFingerprint(cluster, signal)))
                .forEach(signal -> persist(cluster, signal, "POLLING"));
        lastPolledSignals.put(cluster.id(), currentSignals);
        checkpoint(cluster, result.podResourceVersion(), result.eventResourceVersion(),
                result.collectedAt(), result.signals().size());
        int successes = successfulPolls.merge(cluster.id(), 1, Integer::sum);
        nextRetries.put(cluster.id(), Instant.now().plusMillis(pollingIntervalMs));
        WatchRuntimeStatus current = statuses.get(cluster.id());
        String state = successes >= pollingRecoveryThreshold ? "RECOVERING" : "POLLING";
        statuses.put(cluster.id(), new WatchRuntimeStatus(cluster.id(), cluster.name(), state,
                current == null ? null : current.connectedAt(), current == null ? null : current.lastSignalAt(),
                current == null ? 0 : current.reconnectCount(), null, Instant.now(), Instant.now(),
                nextRetries.get(cluster.id()), consecutiveFailures.getOrDefault(cluster.id(), 0), false));
        if (successes >= pollingRecoveryThreshold) {
            pollingClusters.remove(cluster.id());
            successfulPolls.remove(cluster.id());
        }
        publishStatus(cluster.id());
    }

    /** KubernetesWatchCoordinator의 signalFingerprint 처리에 필요한 업무 로직을 수행한다. */
    private String signalFingerprint(Cluster cluster, KubernetesWatchPort.CollectedWatchSignal signal) {
        return String.join("|", cluster.id().toString(), text(signal.namespace()),
                text(signal.resourceKind()), text(signal.resourceName()), text(signal.action()), text(signal.reason()),
                text(signal.status()), text(signal.summary()));
    }

    /** KubernetesWatchCoordinator의 retryReady 처리에 필요한 업무 로직을 수행한다. */
    private boolean retryReady(UUID clusterId) {
        Instant next = nextRetries.get(clusterId);
        return next == null || !next.isAfter(Instant.now());
    }

    /** KubernetesWatchCoordinator의 shouldRenew 처리 조건의 충족 여부를 판단한다. */
    private boolean shouldRenew(UUID clusterId) {
        WatchRuntimeStatus status = statuses.get(clusterId);
        return registrations.containsKey(clusterId) && status != null && status.connectedAt() != null
                && status.connectedAt().isBefore(Instant.now().minusMillis(renewalMs));
    }

    /** KubernetesWatchCoordinator의 checkpoint 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void checkpoint(Cluster cluster, String podResourceVersion, String eventResourceVersion,
                            Instant reconciledAt, int gapSignalCount) {
        if (evolutionRepository == null) return;
        WatchContinuity current = evolutionRepository.findWatchContinuity(cluster.id()).orElse(null);
        WatchContinuity saved = evolutionRepository.saveWatchContinuity(new WatchContinuity(cluster.id(),
                podResourceVersion == null && current != null ? current.podResourceVersion() : podResourceVersion,
                eventResourceVersion == null && current != null ? current.eventResourceVersion() : eventResourceVersion,
                reconciledAt == null ? "STREAMING" : "RECONCILED",
                gapSignalCount == 0 && current != null ? current.gapSignalCount() : gapSignalCount,
                reconciledAt == null && current != null ? current.lastReconciledAt() : reconciledAt,
                null, Instant.now()));
        if (eventStream != null && reconciledAt != null) eventStream.publish("watch-continuity", saved);
    }

    /** KubernetesWatchCoordinator의 publishStatus 처리 결과를 지정된 대상에 전달한다. */
    private void publishStatus(UUID clusterId) {
        if (eventStream != null && statuses.containsKey(clusterId)) {
            eventStream.publish("watch-status", statuses.get(clusterId));
        }
    }

    /** KubernetesWatchCoordinator의 runtime 처리의 핵심 작업 흐름을 실행한다. */
    private WatchRuntimeStatus runtime(Cluster cluster, String state, WatchRuntimeStatus previous,
                                       String error, boolean paused) {
        return new WatchRuntimeStatus(cluster.id(), cluster.name(), state,
                previous == null ? null : previous.connectedAt(), previous == null ? null : previous.lastSignalAt(),
                previous == null ? 0 : previous.reconnectCount(), error, Instant.now(),
                previous == null ? null : previous.lastHeartbeatAt(), nextRetries.get(cluster.id()),
                consecutiveFailures.getOrDefault(cluster.id(), 0), paused);
    }

    /** KubernetesWatchCoordinator의 requireCluster 처리 입력과 현재 상태의 유효성을 검증한다. */
    private Cluster requireCluster(UUID clusterId) {
        return clusterRepository.findById(clusterId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Cluster not found: " + clusterId));
    }

    /** KubernetesWatchCoordinator의 close 처리 대상과 관련 상태를 안전하게 정리한다. */
    private void close(UUID clusterId) {
        attemptSequences.merge(clusterId, 1L, Long::sum);
        CompletableFuture<Void> pending = pendingStarts.remove(clusterId);
        if (pending != null) pending.cancel(true);
        CompletableFuture<Void> pendingPoll = pendingPolls.remove(clusterId);
        if (pendingPoll != null) pendingPoll.cancel(true);
        KubernetesWatchPort.WatchRegistration registration = registrations.remove(clusterId);
        if (registration != null) {
            try {
                registration.close();
            } catch (RuntimeException ignored) {
                // Closing is best-effort; the next reconcile creates a clean registration.
            }
        }
    }

    /** KubernetesWatchCoordinator의 shutdown 처리에 필요한 업무 로직을 수행한다. */
    @PreDestroy
    public void shutdown() {
        pendingStarts.values().forEach(pending -> pending.cancel(true));
        pendingStarts.clear();
        pendingPolls.values().forEach(pending -> pending.cancel(true));
        pendingPolls.clear();
        registrations.keySet().stream().toList().forEach(this::close);
        if (enabled) {
            clusterRepository.findAll().forEach(cluster -> runtimeLeasePort.release(watchLeaseKey(cluster.id())));
        }
        startupExecutor.shutdownNow();
    }

    /** KubernetesWatchCoordinator의 watchLeaseKey 처리에 필요한 업무 로직을 수행한다. */
    private String watchLeaseKey(UUID clusterId) {
        return "kubernetes-watch:" + clusterId;
    }

    /** KubernetesWatchCoordinator의 isCurrentAttempt 처리 조건의 충족 여부를 판단한다. */
    private boolean isCurrentAttempt(UUID clusterId, long attempt) {
        return attemptSequences.getOrDefault(clusterId, 0L) == attempt;
    }

    /** KubernetesWatchCoordinator의 unwrap 처리에 필요한 업무 로직을 수행한다. */
    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }

    /** KubernetesWatchCoordinator의 clean 처리에 필요한 업무 로직을 수행한다. */
    private String clean(String value) {
        if (value == null) return null;
        return value.length() > 1800 ? value.substring(0, 1800) : value;
    }

    /** KubernetesWatchCoordinator의 text 처리에 필요한 업무 로직을 수행한다. */
    private String text(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
