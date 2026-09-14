package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesValidationLabPort;
import io.strato.aiops.application.port.out.OperationsReadinessRepositoryPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.application.port.out.RuntimeLeasePort;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.operations.OperationsModels.OperationsOverview;
import io.strato.aiops.domain.operations.OperationsModels.PriorityItem;
import io.strato.aiops.domain.operations.OperationsModels.RegressionCaseResult;
import io.strato.aiops.domain.operations.OperationsModels.RegressionRun;
import io.strato.aiops.domain.operations.OperationsModels.WatchRuntimeStatus;
import io.strato.aiops.domain.operations.OperationsReadinessModels.AnalysisBenchmark;
import io.strato.aiops.domain.operations.OperationsReadinessModels.FleetQueue;
import io.strato.aiops.domain.operations.OperationsReadinessModels.FleetQueueItem;
import io.strato.aiops.domain.operations.OperationsReadinessModels.LiveValidationPolicy;
import io.strato.aiops.domain.operations.OperationsReadinessModels.LiveValidationPreview;
import io.strato.aiops.domain.operations.OperationsReadinessModels.LiveValidationRun;
import io.strato.aiops.domain.operations.OperationsReadinessModels.OutcomeAggregate;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ReliabilityTrend;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ReliabilityTrendData;
import io.strato.aiops.domain.operations.OperationsReadinessModels.RemediationLearning;
import io.strato.aiops.domain.operations.OperationsReadinessModels.RemediationRecommendation;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ShiftBriefing;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ValidationCase;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ValidationLabRun;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ValidationScenario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class OperationsReadinessService {
    private RuntimeLeasePort runtimeLeasePort = RuntimeLeasePort.localOnly();

    @org.springframework.beans.factory.annotation.Autowired
    void setRuntimeLeasePort(RuntimeLeasePort runtimeLeasePort) {
        this.runtimeLeasePort = runtimeLeasePort;
    }

    private static final List<ValidationScenario> SCENARIOS = List.of(
            scenario("failed-mount", "ConfigMap 누락과 FailedMount", "STORAGE_CONFIG",
                    "FailedMount", "Pod가 참조하는 ConfigMap이 존재하지 않음", "Pod"),
            scenario("crash-loop", "애플리케이션 반복 종료", "APPLICATION_STARTUP",
                    "CrashLoopBackOff", "컨테이너 프로세스가 반복적으로 종료됨", "Pod"),
            scenario("image-pull", "이미지 Pull 실패", "IMAGE",
                    "ImagePullBackOff", "Registry 권한, 이미지 이름 또는 tag 오류", "Pod"),
            scenario("port-mismatch", "Service와 컨테이너 Port 불일치", "TRAFFIC",
                    "targetPort mismatch", "Service targetPort와 workload containerPort가 일치하지 않음", "Service"),
            scenario("probe-failure", "Readiness Probe 실패", "PROBE",
                    "Unhealthy readiness probe failed", "Probe 경로, 포트 또는 애플리케이션 준비 시간 오류", "Pod"),
            scenario("pvc-pending", "PVC Binding 지연", "STORAGE_CONFIG",
                    "FailedBinding PVC Pending", "StorageClass 또는 PV 공급 조건이 충족되지 않음", "PersistentVolumeClaim"),
            scenario("oom-risk", "메모리 제한과 OOM 종료", "CAPACITY",
                    "OOMKilled exitCode=137", "컨테이너 메모리 제한 또는 애플리케이션 메모리 사용 문제", "Pod"),
            scenario("rollback-guard", "Rollout 실패와 Rollback Guard", "ROLLOUT",
                    "ProgressDeadlineExceeded", "새 revision이 준비 상태에 도달하지 못함", "Deployment")
    );

    private final OperationsControlPlaneService operationsService;
    private final KubernetesWatchCoordinator watchCoordinator;
    private final AnalysisRegressionService regressionService;
    private final OperationsReadinessRepositoryPort readinessRepository;
    private final KubernetesValidationLabPort validationLabPort;
    private final ClusterRepositoryPort clusterRepository;
    private final ClusterCredentialRepositoryPort credentialRepository;
    private final SecretCryptoPort secretCryptoPort;
    private final AuditLogRepositoryPort auditRepository;
    private final OperationsEventStream eventStream;
    private final boolean liveValidationEnabled;
    private final boolean allowProductionValidation;
    private final String validationNamespacePrefix;
    private final int maximumValidationTtlSeconds;
    private final String requiredConfirmation;

    public OperationsReadinessService(OperationsControlPlaneService operationsService,
                                      KubernetesWatchCoordinator watchCoordinator,
                                      AnalysisRegressionService regressionService,
                                      OperationsReadinessRepositoryPort readinessRepository,
                                      KubernetesValidationLabPort validationLabPort,
                                      ClusterRepositoryPort clusterRepository,
                                      ClusterCredentialRepositoryPort credentialRepository,
                                      SecretCryptoPort secretCryptoPort,
                                      AuditLogRepositoryPort auditRepository,
                                      OperationsEventStream eventStream,
                                      @Value("${aiops.validation-lab.live-enabled:false}") boolean liveValidationEnabled,
                                      @Value("${aiops.validation-lab.allow-production:false}") boolean allowProductionValidation,
                                      @Value("${aiops.validation-lab.namespace-prefix:aiops-validation-}") String validationNamespacePrefix,
                                      @Value("${aiops.validation-lab.maximum-ttl-seconds:900}") int maximumValidationTtlSeconds,
                                      @Value("${aiops.validation-lab.required-confirmation:RUN LIVE VALIDATION}") String requiredConfirmation) {
        this.operationsService = operationsService;
        this.watchCoordinator = watchCoordinator;
        this.regressionService = regressionService;
        this.readinessRepository = readinessRepository;
        this.validationLabPort = validationLabPort;
        this.clusterRepository = clusterRepository;
        this.credentialRepository = credentialRepository;
        this.secretCryptoPort = secretCryptoPort;
        this.auditRepository = auditRepository;
        this.eventStream = eventStream;
        this.liveValidationEnabled = liveValidationEnabled;
        this.allowProductionValidation = allowProductionValidation;
        this.validationNamespacePrefix = validationNamespacePrefix.matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?-")
                ? validationNamespacePrefix : "aiops-validation-";
        this.maximumValidationTtlSeconds = Math.max(120, Math.min(maximumValidationTtlSeconds, 3_600));
        this.requiredConfirmation = requiredConfirmation;
    }

    public FleetQueue fleetQueue() {
        OperationsOverview overview = operationsService.getOverview();
        List<FleetQueueItem> items = new ArrayList<>();
        for (WatchRuntimeStatus status : watchCoordinator.statuses()) {
            if (Set.of("CONNECTED", "STARTING").contains(status.state())) {
                continue;
            }
            int score = switch (status.state()) {
                case "FAILED" -> 98;
                case "DEGRADED" -> 92;
                case "POLLING" -> 78;
                case "RECOVERING" -> 62;
                case "PAUSED" -> 45;
                default -> 55;
            };
            items.add(new FleetQueueItem("collector:" + status.clusterId(), "COLLECTOR", status.clusterId(),
                    status.clusterName(), null, score >= 90 ? "HIGH" : "MEDIUM", score,
                    status.clusterName() + " 신호 수집 " + status.state(),
                    text(status.lastError(), "Kubernetes 신호 수집 상태를 확인하세요."),
                    status.state().equals("POLLING")
                            ? "Polling으로 근거를 보존하고 있습니다. 네트워크 경로를 점검하세요."
                            : "연결 원인을 확인하고 Watch 재시도를 검토하세요.",
                    "/settings/reliability", status.updatedAt()));
        }
        overview.priorityQueue().forEach(item -> items.add(fromPriority(item)));
        List<FleetQueueItem> sorted = items.stream()
                .sorted(Comparator.comparingInt(FleetQueueItem::score).reversed()
                        .thenComparing(FleetQueueItem::detectedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(100)
                .toList();
        return new FleetQueue(Instant.now(), sorted.size(),
                (int) sorted.stream().filter(item -> item.score() >= 80).count(),
                (int) watchCoordinator.statuses().stream()
                        .filter(status -> !Set.of("CONNECTED", "STARTING").contains(status.state())).count(),
                sorted);
    }

    public ShiftBriefing shiftBriefing() {
        OperationsOverview overview = operationsService.getOverview();
        FleetQueue queue = fleetQueue();
        String posture = overview.criticalIncidents() > 0 || queue.immediateItems() > 0 ? "ACTION_REQUIRED"
                : queue.degradedCollectors() > 0 ? "DEGRADED_VISIBILITY" : "STABLE";
        List<String> immediate = queue.items().stream().filter(item -> item.score() >= 80).limit(5)
                .map(item -> item.clusterName() + ": " + item.title() + " · " + item.nextAction()).toList();
        List<String> watches = queue.items().stream().filter(item -> item.score() < 80).limit(8)
                .map(item -> item.clusterName() + ": " + item.title()).toList();
        String beginner = immediate.isEmpty()
                ? "즉시 처리할 심각한 항목은 없습니다. 수집 상태와 관찰 중인 항목을 확인하세요."
                : "지금 먼저 확인할 항목이 " + immediate.size() + "개 있습니다. 위에서부터 순서대로 근거를 확인하세요.";
        String expert = "open=" + overview.openIncidents() + ", critical=" + overview.criticalIncidents()
                + ", runningJobs=" + overview.runningJobs() + ", failedJobs=" + overview.failedJobs()
                + ", collectorDegraded=" + queue.degradedCollectors();
        return new ShiftBriefing(Instant.now(), posture, beginner, expert, overview.openIncidents(),
                overview.criticalIncidents(), overview.runningJobs(), overview.failedJobs(),
                queue.degradedCollectors(), immediate, watches);
    }

    public List<ValidationScenario> scenarios() {
        return SCENARIOS;
    }

    public ValidationLabRun runValidation(String actor) {
        RegressionRun run = regressionService.run(actor);
        List<ValidationCase> cases = run.cases().stream().map(item -> new ValidationCase(
                item.caseId(), item.title(), item.category(), item.status(), item.score(),
                item.assertions(), item.failures(), item.durationMs())).toList();
        return new ValidationLabRun(run.id(), run.status(), run.score(), run.passedCases(), run.totalCases(),
                "VIRTUAL_SAFE", run.completedAt(), cases);
    }

    public LiveValidationPolicy liveValidationPolicy() {
        return new LiveValidationPolicy(liveValidationEnabled, "LIVE_GUARDED", validationNamespacePrefix,
                maximumValidationTtlSeconds, requiredConfirmation, List.of(
                "실제 검증은 기본 비활성화이며 서버 환경 설정으로 명시적으로 활성화해야 합니다.",
                "운영 클러스터는 별도 허용 설정 없이는 실행할 수 없습니다.",
                "격리된 일회성 namespace와 소유권 라벨이 있는 리소스만 생성합니다.",
                "Kubernetes RBAC create/delete 권한을 변경 전에 검증합니다.",
                "최대 TTL 이후 관리 namespace를 자동 정리합니다.",
                "정확한 확인 문구 없이는 실제 리소스를 생성하지 않습니다."
        ));
    }

    public LiveValidationPreview previewLiveValidation(UUID clusterId, String scenarioId, int ttlSeconds) {
        Cluster cluster = requireCluster(clusterId);
        ValidationScenario scenario = requireScenario(scenarioId);
        int boundedTtl = boundedTtl(ttlSeconds);
        String namespace = validationNamespacePrefix + "preview";
        List<String> passed = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        if (liveValidationEnabled) passed.add("Live Validation 기능이 서버에서 활성화되어 있습니다.");
        else blocked.add("Live Validation이 비활성화되어 있습니다. AIOPS_VALIDATION_LAB_LIVE_ENABLED=true가 필요합니다.");
        if (cluster.environment() != ClusterEnvironment.PROD || allowProductionValidation) {
            passed.add("대상 클러스터 환경이 Live Validation 안전 정책을 충족합니다.");
        } else {
            blocked.add("운영(PROD) 클러스터 검증은 기본 정책으로 차단됩니다.");
        }
        if (liveValidationEnabled && blocked.isEmpty()) {
            KubernetesValidationLabPort.Preflight preflight = validationLabPort.preflight(
                    connectionCredential(clusterId), namespace, scenarioId);
            passed.addAll(preflight.passedChecks());
            blocked.addAll(preflight.blockingReasons());
        }
        return new LiveValidationPreview(clusterId, cluster.name(), scenario.id(), namespace, boundedTtl,
                blocked.isEmpty(), List.copyOf(passed), List.copyOf(blocked), plannedResources(scenarioId),
                Instant.now().plusSeconds(boundedTtl));
    }

    public synchronized LiveValidationRun runLiveValidation(UUID clusterId, String scenarioId, int ttlSeconds,
                                                            String confirmation, String actor, String requestId) {
        if (!requiredConfirmation.equals(confirmation)) {
            throw new IllegalArgumentException("Confirmation text must exactly match: " + requiredConfirmation);
        }
        Cluster cluster = requireCluster(clusterId);
        requireScenario(scenarioId);
        if (!readinessRepository.findActiveLiveValidationRuns(clusterId).isEmpty()) {
            throw new IllegalStateException(
                    "An earlier live validation still requires cleanup for this cluster.");
        }
        int boundedTtl = boundedTtl(ttlSeconds);
        UUID runId = UUID.randomUUID();
        String namespace = validationNamespacePrefix + runId.toString().substring(0, 8);
        List<String> checks = new ArrayList<>();
        List<String> blocking = new ArrayList<>();
        if (!liveValidationEnabled) blocking.add("Live Validation is disabled.");
        if (cluster.environment() == ClusterEnvironment.PROD && !allowProductionValidation) {
            blocking.add("Production cluster validation is blocked by policy.");
        }
        KubernetesConnectionCredential credential = null;
        if (blocking.isEmpty()) {
            credential = connectionCredential(clusterId);
            KubernetesValidationLabPort.Preflight preflight = validationLabPort.preflight(
                    credential, namespace, scenarioId);
            checks.addAll(preflight.passedChecks());
            blocking.addAll(preflight.blockingReasons());
        }
        if (!blocking.isEmpty()) {
            throw new IllegalStateException(String.join(" ", blocking));
        }
        if (credential == null) {
            throw new IllegalStateException("Kubernetes credential was not available after preflight.");
        }
        Instant startedAt = Instant.now();
        LiveValidationRun running = readinessRepository.saveLiveValidationRun(new LiveValidationRun(
                runId, clusterId, cluster.name(), scenarioId, namespace, "RUNNING", "LIVE_GUARDED", boundedTtl,
                List.copyOf(checks), plannedResources(scenarioId), null,
                "격리 namespace에 검증 fixture를 적용하고 Kubernetes 신호를 관찰하고 있습니다.", true,
                startedAt, startedAt.plusSeconds(boundedTtl), null, actor));
        audit("LIVE_VALIDATION_STARTED", "LIVE_VALIDATION", runId.toString(), actor, requestId);
        try {
            KubernetesValidationLabPort.Execution execution = validationLabPort.apply(
                    credential, runId, namespace, scenarioId);
            LiveValidationRun completed = readinessRepository.saveLiveValidationRun(new LiveValidationRun(
                    running.id(), running.clusterId(), running.clusterName(), running.scenarioId(),
                    running.namespace(), execution.signalDetected() ? "PASSED" : "EVIDENCE_PENDING",
                    running.safetyMode(), running.ttlSeconds(), running.safetyChecks(), execution.resources(),
                    execution.observedSignal(), execution.detail(), true, running.startedAt(), running.expiresAt(),
                    Instant.now(), actor));
            audit("LIVE_VALIDATION_COMPLETED", "LIVE_VALIDATION", runId.toString(), actor, requestId);
            eventStream.publish("live-validation", completed);
            return completed;
        } catch (RuntimeException exception) {
            LiveValidationRun failed = readinessRepository.saveLiveValidationRun(new LiveValidationRun(
                    running.id(), running.clusterId(), running.clusterName(), running.scenarioId(),
                    running.namespace(), "FAILED", running.safetyMode(), running.ttlSeconds(), running.safetyChecks(),
                    running.resources(), null, sanitize(exception.getMessage()), true, running.startedAt(),
                    running.expiresAt(), Instant.now(), actor));
            eventStream.publish("live-validation", failed);
            throw exception;
        }
    }

    public LiveValidationRun cleanupLiveValidation(UUID runId, String actor, String requestId) {
        LiveValidationRun current = readinessRepository.findLiveValidationRun(runId)
                .orElseThrow(() -> new NoSuchElementException("Live validation run not found: " + runId));
        if (!current.cleanupRequired()) return current;
        KubernetesValidationLabPort.Cleanup cleanup = validationLabPort.cleanup(
                connectionCredential(current.clusterId()), current.id(), current.namespace());
        LiveValidationRun saved = readinessRepository.saveLiveValidationRun(new LiveValidationRun(
                current.id(), current.clusterId(), current.clusterName(), current.scenarioId(), current.namespace(),
                cleanup.cleaned() ? "CLEANED" : "CLEANUP_FAILED", current.safetyMode(), current.ttlSeconds(),
                current.safetyChecks(), current.resources(), current.observedSignal(), cleanup.detail(),
                !cleanup.cleaned(), current.startedAt(), current.expiresAt(), Instant.now(), actor));
        audit("LIVE_VALIDATION_CLEANUP", "LIVE_VALIDATION", runId.toString(), actor, requestId);
        eventStream.publish("live-validation", saved);
        return saved;
    }

    @Scheduled(fixedDelayString = "${aiops.validation-lab.cleanup-interval-ms:60000}")
    public void cleanupExpiredLiveValidations() {
        if (!runtimeLeasePort.acquireOrRenew("scheduler:validation-cleanup", Duration.ofSeconds(90))) return;
        for (LiveValidationRun run : readinessRepository.findExpiredLiveValidationRuns(Instant.now(), 20)) {
            try {
                cleanupLiveValidation(run.id(), "system", "validation-ttl-cleanup");
            } catch (RuntimeException ignored) {
                // The persisted CLEANUP_FAILED state and the next scheduler pass keep cleanup observable and retryable.
            }
        }
    }

    public AnalysisBenchmark runBenchmark(String actor) {
        return benchmark(regressionService.run(actor));
    }

    public AnalysisBenchmark latestBenchmark() {
        List<RegressionRun> runs = regressionService.list(1);
        return runs.isEmpty() ? null : benchmark(runs.get(0));
    }

    public RemediationLearning remediationLearning(UUID incidentId) {
        var incident = operationsService.getIncident(incidentId).incident();
        List<OutcomeAggregate> samples = readinessRepository.aggregateRemediationOutcomes(
                incident.category(), incident.resourceKind(), 20);
        List<RemediationRecommendation> recommendations = samples.stream()
                .map(this::recommendation)
                .sorted(Comparator.comparingDouble(RemediationRecommendation::successRate).reversed()
                        .thenComparing(Comparator.comparingInt(
                                RemediationRecommendation::sampleCount).reversed()))
                .toList();
        int count = samples.stream().mapToInt(OutcomeAggregate::samples).sum();
        String notice = count == 0
                ? "비슷한 조치 결과 표본이 없습니다. Runbook과 사실 근거를 먼저 사용하세요."
                : "성공률은 과거 조치 후 Kubernetes 상태 관찰 결과이며 인과관계를 보장하지 않습니다.";
        return new RemediationLearning(incidentId, incident.category(), incident.resourceKind(), count, notice,
                recommendations);
    }

    public ReliabilityTrend reliabilityTrend(UUID clusterId, String namespace, int days) {
        int window = Math.max(7, Math.min(days, 90));
        ReliabilityTrendData data = readinessRepository.queryReliabilityTrend(
                clusterId, namespace, Instant.now().minus(Duration.ofDays(window)));
        double recurrence = percentage(data.recurringIncidents(), data.incidentsDetected());
        double remediation = percentage(data.remediationSucceeded(), data.remediationSamples());
        List<WatchRuntimeStatus> statuses = watchCoordinator.statuses().stream()
                .filter(status -> clusterId == null || clusterId.equals(status.clusterId())).toList();
        double collectorCoverage = statuses.isEmpty() ? 0 : Math.round(statuses.stream()
                .mapToInt(status -> switch (status.state()) {
                    case "CONNECTED" -> 100;
                    case "POLLING", "RECOVERING" -> 80;
                    case "STARTING" -> 60;
                    case "PAUSED" -> 0;
                    default -> 25;
                }).average().orElse(0) * 10.0) / 10.0;
        return new ReliabilityTrend(Instant.now(), window, clusterId, blankToNull(namespace), "EVENT_BASED",
                data.incidentsDetected(), data.incidentsResolved(), data.recurringIncidents(), recurrence,
                data.meanTimeToAcknowledgeMinutes(), data.meanTimeToResolveMinutes(), remediation,
                collectorCoverage, data.daily(), data.scopes());
    }

    private static ValidationScenario scenario(String id, String title, String category, String signal,
                                               String rootCause, String kind) {
        return new ValidationScenario(id, title, category, signal, rootCause, kind, "VIRTUAL_SAFE");
    }

    private FleetQueueItem fromPriority(PriorityItem item) {
        return new FleetQueueItem(item.id(), item.sourceType(), item.clusterId(), text(item.clusterName(), "platform"),
                item.namespace(), item.severity(), item.score(), item.title(), item.reason(), item.nextAction(),
                item.targetPath(), item.detectedAt());
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private ValidationScenario requireScenario(String scenarioId) {
        return SCENARIOS.stream().filter(item -> item.id().equals(scenarioId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported validation scenario: " + scenarioId));
    }

    private Cluster requireCluster(UUID clusterId) {
        return clusterRepository.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
    }

    private KubernetesConnectionCredential connectionCredential(UUID clusterId) {
        EncryptedClusterCredential credential = credentialRepository.findByClusterId(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found: " + clusterId));
        String payload = secretCryptoPort.decrypt(new EncryptedSecret(credential.encryptedPayload(),
                credential.keyId(), credential.algorithm(), credential.nonce()));
        return new KubernetesConnectionCredential(credential.credentialType(), payload);
    }

    private int boundedTtl(int ttlSeconds) {
        return Math.max(120, Math.min(ttlSeconds <= 0 ? 300 : ttlSeconds, maximumValidationTtlSeconds));
    }

    private List<String> plannedResources(String scenarioId) {
        return switch (scenarioId) {
            case "port-mismatch" -> List.of("Deployment/aiops-port-mismatch", "Service/aiops-port-mismatch");
            case "pvc-pending" -> List.of("PersistentVolumeClaim/aiops-pvc-pending");
            case "rollback-guard" -> List.of("Deployment/aiops-rollback-guard");
            default -> List.of("Pod/aiops-" + scenarioId);
        };
    }

    private AnalysisBenchmark benchmark(RegressionRun run) {
        int totalAssertions = run.cases().stream()
                .mapToInt(item -> item.assertions().size() + item.failures().size()).sum();
        int failures = run.cases().stream().mapToInt(item -> item.failures().size()).sum();
        double classification = assertionRate(run.cases(), "원인 카테고리");
        double evidence = assertionRate(run.cases(), "사실 근거");
        double safety = assertionRate(run.cases(), "변경 명령");
        double falseRate = percentage(failures, totalAssertions);
        long p95 = percentile95(run.cases().stream().map(RegressionCaseResult::durationMs).sorted().toList());
        List<String> reasons = new ArrayList<>();
        if (run.score() < 95) reasons.add("전체 회귀 점수가 95점 미만입니다.");
        if (classification < 100) reasons.add("원인 분류 정확도가 100%가 아닙니다.");
        if (evidence < 100) reasons.add("필수 사실 근거 보존 검증이 실패했습니다.");
        if (safety < 100) reasons.add("명령 안전성 검증이 실패했습니다.");
        if (falseRate > 0) reasons.add("검증 assertion 실패가 존재합니다.");
        return new AnalysisBenchmark(run.id(), reasons.isEmpty() ? "PASSED" : "BLOCKED", run.score(),
                classification, evidence, safety, falseRate, p95, run.passedCases(), run.totalCases(),
                run.baselineVersion(), reasons.isEmpty() ? "RELEASE_READY" : "HOLD", List.copyOf(reasons),
                run.completedAt());
    }

    private double assertionRate(List<RegressionCaseResult> cases, String token) {
        int passed = 0;
        int failed = 0;
        for (RegressionCaseResult item : cases) {
            passed += (int) item.assertions().stream().filter(value -> value.contains(token)).count();
            failed += (int) item.failures().stream().filter(value -> value.contains(token)).count();
        }
        return percentage(passed, passed + failed);
    }

    private long percentile95(List<Long> sorted) {
        if (sorted.isEmpty()) return 0;
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    private RemediationRecommendation recommendation(OutcomeAggregate item) {
        double success = percentage(item.succeeded(), item.samples());
        String confidence = item.samples() >= 20 ? "HIGH" : item.samples() >= 5 ? "MEDIUM" : "LOW";
        String action = "GUIDED_VERIFICATION".equals(item.action())
                ? "Runbook 기반 수동 검증"
                : sanitize(item.action());
        String explanation = item.samples() < 5
                ? "표본이 적으므로 추천 순위를 참고만 하고 현재 Incident 근거를 우선하세요."
                : "동일 category/resource kind의 조치 후 관찰 결과를 집계했습니다.";
        return new RemediationRecommendation(item.category(), item.resourceKind(), action, item.samples(),
                item.succeeded(), item.failed(), success, item.averageObservationSeconds(), confidence, explanation);
    }

    private double percentage(int numerator, int denominator) {
        return denominator <= 0 ? 0 : Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    private String sanitize(String value) {
        if (value == null) return "";
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240) + "...";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void audit(String action, String targetType, String targetId, String actor, String requestId) {
        auditRepository.save(AuditLog.create(action, targetType, targetId, actor, requestId));
    }
}
