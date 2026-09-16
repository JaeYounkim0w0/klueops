package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AnalysisAssuranceRepositoryPort;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.OperationsEvolutionRepositoryPort;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.SignalNoisePolicy;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.TriageItem;
import io.strato.aiops.domain.operations.OperationsModels.TriageQueue;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignalGroup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class WatchSignalTriageService {

    private static final Set<String> HIGH_SIGNAL_EVENTS = Set.of(
            "FAILEDMOUNT", "FAILEDSCHEDULING", "UNHEALTHY", "BACKOFF", "FAILEDBINDING",
            "CRASHLOOPBACKOFF", "PROGRESSDEADLINEEXCEEDED", "IMAGEPULLBACKOFF", "ERRIMAGEPULL");

    private final AnalysisAssuranceRepositoryPort assuranceRepository;
    private final OperationsEvolutionRepositoryPort evolutionRepository;
    private final ClusterRepositoryPort clusterRepository;
    private final IncidentSignalCoordinator incidentSignalCoordinator;
    private final OperationsNotificationPublisher notificationPublisher;
    private final AuditLogRepositoryPort auditRepository;
    private final OperationsEventStream eventStream;

    /** WatchSignalTriageService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public WatchSignalTriageService(AnalysisAssuranceRepositoryPort assuranceRepository,
                                    OperationsEvolutionRepositoryPort evolutionRepository,
                                    ClusterRepositoryPort clusterRepository,
                                    IncidentSignalCoordinator incidentSignalCoordinator,
                                    OperationsNotificationPublisher notificationPublisher,
                                    AuditLogRepositoryPort auditRepository,
                                    OperationsEventStream eventStream) {
        this.assuranceRepository = assuranceRepository;
        this.evolutionRepository = evolutionRepository;
        this.clusterRepository = clusterRepository;
        this.incidentSignalCoordinator = incidentSignalCoordinator;
        this.notificationPublisher = notificationPublisher;
        this.auditRepository = auditRepository;
        this.eventStream = eventStream;
    }

    /** WatchSignalTriageService의 ingestWatchSignal 처리에 필요한 업무 로직을 수행한다. */
    @Transactional
    public void ingestWatchSignal(WatchSignal signal) {
        SignalClassification classification = classify(signal);
        if (classification == null) {
            return;
        }
        String key = sha256(String.join("|", signal.clusterId().toString(), defaultText(signal.namespace(), ""),
                signal.resourceKind(), signal.resourceName(), classification.category(), classification.reason()));
        Instant observedAt = signal.observedAt() == null ? Instant.now() : signal.observedAt();
        WatchSignalGroup current = assuranceRepository.findWatchSignalGroupByFingerprint(key).orElse(null);
        int occurrences = current == null ? 1 : current.occurrenceCount() + 1;
        String state = current == null ? "OPEN" : current.state();
        SignalNoisePolicy policy = evolutionRepository
                .findApplicableNoisePolicy(signal.clusterId(), signal.namespace(), observedAt).orElse(null);
        int repeatThreshold = policy == null ? 3 : policy.repeatThreshold();
        boolean belowSeverityFloor = policy != null
                && severityScore(classification.severity()) < severityScore(policy.severityFloor());
        boolean policySuppressed = policy != null && (policy.suppressionActive(observedAt) || belowSeverityFloor);
        if (policySuppressed && current == null) {
            state = "SUPPRESSED";
        }
        boolean promote = !policySuppressed && !"SUPPRESSED".equals(state)
                && ("CRITICAL".equals(classification.severity()) || "HIGH".equals(classification.severity())
                || occurrences >= repeatThreshold);
        WatchSignalGroup group = new WatchSignalGroup(current == null ? UUID.randomUUID() : current.id(), key,
                signal.clusterId(), signal.clusterName(), signal.namespace(), signal.resourceKind(), signal.resourceName(),
                classification.category(), classification.severity(), state, classification.reason(),
                OperationsNotificationPublisher.cleanText(signal.summary(), 4000), occurrences,
                current == null ? observedAt : current.firstObservedAt(), observedAt,
                current == null ? null : current.incidentId(), "kubernetes-watch", Instant.now());
        if (promote && group.incidentId() == null) {
            Cluster cluster = clusterRepository.findById(signal.clusterId()).orElse(null);
            if (cluster != null) {
                IncidentSignalCoordinator.IncidentPromotion promotion = incidentSignalCoordinator.promoteWatchSignal(
                        cluster, signal, classification.category(), classification.severity(),
                        classification.reason(), "kubernetes-watch");
                Incident incident = promotion.incident();
                if (promotion.notification() != null) {
                    var notification = promotion.notification();
                    notificationPublisher.publish(notification.type(), notification.severity(), notification.title(),
                            notification.message(), notification.targetPath(), notification.dedupKey());
                }
                group = new WatchSignalGroup(group.id(), group.fingerprint(), group.clusterId(), group.clusterName(),
                        group.namespace(), group.resourceKind(), group.resourceName(), group.category(), group.severity(),
                        "INCIDENT_CREATED", group.reason(), group.summary(), group.occurrenceCount(),
                        group.firstObservedAt(), group.lastObservedAt(), incident.id(), group.updatedBy(), Instant.now());
            }
        }
        WatchSignalGroup saved = assuranceRepository.saveWatchSignalGroup(group);
        eventStream.publish("triage", saved);
    }

    /** WatchSignalTriageService의 getQueue 처리 결과를 조회해 반환한다. */
    public TriageQueue getQueue(UUID clusterId, String namespace, String state, int limit) {
        List<WatchSignalGroup> groups = assuranceRepository.findWatchSignalGroups(clusterId, namespace, state, limit);
        List<TriageItem> items = groups.stream().map(group -> new TriageItem(group.id().toString(), "WATCH_SIGNAL",
                group.clusterId(), group.clusterName(), group.namespace(), group.resourceKind(), group.resourceName(),
                group.severity(), group.state(), triageScore(group), triageConfidence(group), triageImpact(group),
                group.occurrenceCount(), group.reason() + " · " + group.resourceKind() + "/" + group.resourceName(),
                group.summary(), "상세 분석에서 현재 상태와 연결 리소스를 확인하세요.", group.incidentId(),
                group.firstObservedAt(), group.lastObservedAt())).toList();
        return new TriageQueue(Instant.now(), count(groups, item -> !"SUPPRESSED".equals(item.state())),
                count(groups, item -> Set.of("CRITICAL", "HIGH").contains(item.severity())),
                count(groups, item -> "INCIDENT_CREATED".equals(item.state())),
                count(groups, item -> "SUPPRESSED".equals(item.state())), items);
    }

    /** WatchSignalTriageService의 updateState 처리 대상의 상태를 갱신한다. */
    @Transactional
    public WatchSignalGroup updateState(UUID groupId, String requestedState, String actor, String requestId) {
        String state = requireState(requestedState);
        WatchSignalGroup current = assuranceRepository.findWatchSignalGroup(groupId)
                .orElseThrow(() -> new NoSuchElementException("Watch signal group not found: " + groupId));
        WatchSignalGroup saved = assuranceRepository.saveWatchSignalGroup(new WatchSignalGroup(current.id(),
                current.fingerprint(), current.clusterId(), current.clusterName(), current.namespace(),
                current.resourceKind(), current.resourceName(), current.category(), current.severity(), state,
                current.reason(), current.summary(), current.occurrenceCount(), current.firstObservedAt(),
                current.lastObservedAt(), current.incidentId(), actor, Instant.now()));
        auditRepository.save(AuditLog.create("WATCH_SIGNAL_GROUP_STATE_CHANGED", "WATCH_SIGNAL_GROUP",
                groupId.toString(), actor, requestId));
        eventStream.publish("triage", saved);
        return saved;
    }

    /** WatchSignalTriageService의 classify 처리에 필요한 업무 로직을 수행한다. */
    static SignalClassification classify(WatchSignal signal) {
        String reason = defaultText(signal.reason(), signal.action());
        String token = normalizeToken(reason);
        String status = normalizeUpper(signal.status());
        boolean warning = "WARNING".equals(status);
        boolean unhealthy = Set.of("FAILED", "PENDING", "UNKNOWN").contains(status);
        boolean deleted = "DELETED".equalsIgnoreCase(signal.action());
        if (!HIGH_SIGNAL_EVENTS.contains(token) && !warning && !unhealthy && !deleted) {
            return null;
        }
        String severity = Set.of("FAILEDMOUNT", "FAILEDSCHEDULING", "FAILEDBINDING", "CRASHLOOPBACKOFF",
                "IMAGEPULLBACKOFF", "ERRIMAGEPULL", "PROGRESSDEADLINEEXCEEDED").contains(token)
                ? "HIGH" : warning || unhealthy ? "MEDIUM" : "LOW";
        return new SignalClassification(eventCategory(token), severity, reason);
    }

    /** WatchSignalTriageService의 requireState 처리 입력과 현재 상태의 유효성을 검증한다. */
    private static String requireState(String value) {
        String normalized = normalizeUpper(value);
        if (!Set.of("OPEN", "ACKNOWLEDGED", "SUPPRESSED").contains(normalized)) {
            throw new IllegalArgumentException("state must be one of [OPEN, ACKNOWLEDGED, SUPPRESSED]");
        }
        return normalized;
    }

    /** WatchSignalTriageService의 triageScore 처리에 필요한 업무 로직을 수행한다. */
    private static int triageScore(WatchSignalGroup group) {
        return Math.min(100, severityScore(group.severity()) + Math.min(25, group.occurrenceCount() * 3)
                + ("INCIDENT_CREATED".equals(group.state()) ? 10 : 0));
    }

    /** WatchSignalTriageService의 triageConfidence 처리에 필요한 업무 로직을 수행한다. */
    private static int triageConfidence(WatchSignalGroup group) {
        return Math.min(98, 55 + Math.min(35, group.occurrenceCount() * 5)
                + (group.incidentId() == null ? 0 : 8));
    }

    /** WatchSignalTriageService의 triageImpact 처리에 필요한 업무 로직을 수행한다. */
    private static int triageImpact(WatchSignalGroup group) {
        return Math.min(100, severityScore(group.severity())
                + ("Pod".equalsIgnoreCase(group.resourceKind()) ? 15 : 5));
    }

    /** WatchSignalTriageService의 eventCategory 처리에 필요한 업무 로직을 수행한다. */
    private static String eventCategory(String reason) {
        return switch (reason) {
            case "FAILEDMOUNT", "FAILEDBINDING" -> "STORAGE_CONFIG";
            case "FAILEDSCHEDULING" -> "CAPACITY";
            case "UNHEALTHY" -> "PROBE";
            case "BACKOFF" -> "APPLICATION_STARTUP";
            case "IMAGEPULLBACKOFF", "ERRIMAGEPULL" -> "IMAGE";
            case "PROGRESSDEADLINEEXCEEDED" -> "ROLLOUT";
            default -> "KUBERNETES_EVENT";
        };
    }

    /** WatchSignalTriageService의 severityScore 처리에 필요한 업무 로직을 수행한다. */
    private static int severityScore(String severity) {
        return switch (normalizeUpper(severity)) {
            case "CRITICAL" -> 70;
            case "HIGH" -> 55;
            case "MEDIUM" -> 35;
            case "LOW" -> 20;
            default -> 10;
        };
    }

    /** WatchSignalTriageService의 normalizeToken 처리 데이터를 필요한 표현으로 변환한다. */
    private static String normalizeToken(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    /** WatchSignalTriageService의 normalizeUpper 처리 데이터를 필요한 표현으로 변환한다. */
    private static String normalizeUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    /** WatchSignalTriageService의 defaultText 처리에 필요한 업무 로직을 수행한다. */
    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** WatchSignalTriageService의 sha256 처리에 필요한 업무 로직을 수행한다. */
    private static String sha256(String value) {
        return OperationsNotificationPublisher.sha256(value);
    }

    /** WatchSignalTriageService의 count 처리에 필요한 업무 로직을 수행한다. */
    private static <T> int count(List<T> values, java.util.function.Predicate<T> predicate) {
        return Math.toIntExact(values.stream().filter(predicate).count());
    }

    record SignalClassification(String category, String severity, String reason) {
    }
}
