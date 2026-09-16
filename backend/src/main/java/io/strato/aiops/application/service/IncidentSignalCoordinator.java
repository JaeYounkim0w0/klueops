package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.AnalysisSessionRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesEventSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisSession;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentActivity;
import io.strato.aiops.domain.operations.OperationsModels.IncidentEvidence;
import io.strato.aiops.domain.operations.OperationsModels.IncidentRecovery;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;
import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IncidentSignalCoordinator {

    private static final Set<String> HIGH_SIGNAL_EVENTS = Set.of(
            "FAILEDMOUNT", "FAILEDSCHEDULING", "UNHEALTHY", "BACKOFF", "FAILEDBINDING",
            "CRASHLOOPBACKOFF", "PROGRESSDEADLINEEXCEEDED", "IMAGEPULLBACKOFF", "ERRIMAGEPULL");
    private static final Pattern RESOURCE_REFERENCE_PATTERN = Pattern.compile(
            "(?i)\\b(Namespace|Pod|Deployment|StatefulSet|DaemonSet|ReplicaSet|Service|Endpoint|Ingress|ConfigMap|Secret|PersistentVolumeClaim|PersistentVolume|Job|CronJob)/([a-z0-9][a-z0-9._:-]*)");

    private final OperationsRepositoryPort operationsRepository;
    private final AnalysisSessionRepositoryPort analysisRepository;
    private final KubernetesEventSnapshotRepositoryPort eventRepository;
    private final ObjectMapper objectMapper;
    private final IncidentRecoveryCoordinator recoveryCoordinator;

    /** IncidentSignalCoordinator 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public IncidentSignalCoordinator(OperationsRepositoryPort operationsRepository,
                                     AnalysisSessionRepositoryPort analysisRepository,
                                     KubernetesEventSnapshotRepositoryPort eventRepository,
                                     ObjectMapper objectMapper,
                                     IncidentRecoveryCoordinator recoveryCoordinator) {
        this.operationsRepository = operationsRepository;
        this.analysisRepository = analysisRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.recoveryCoordinator = recoveryCoordinator;
    }

    /** IncidentSignalCoordinator의 reconcile 처리에 필요한 업무 로직을 수행한다. */
    public List<IncidentNotification> reconcile(Cluster cluster, String actor) {
        List<IncidentNotification> notifications = new ArrayList<>();
        for (AnalysisSession analysis : analysisRepository.findRecent(cluster.id(), null, null, 50)) {
            for (IncidentSignal signal : analysisSignals(cluster, analysis, objectMapper)) {
                addNotification(notifications, upsert(cluster, signal, actor).notification());
            }
        }
        for (KubernetesEventSnapshot event : eventRepository.findLatest(cluster.id(), null, 500)) {
            IncidentSignal signal = eventSignal(event);
            if (signal != null) {
                addNotification(notifications, upsert(cluster, signal, actor).notification());
            }
        }
        return List.copyOf(notifications);
    }

    /** IncidentSignalCoordinator의 promoteWatchSignal 처리에 필요한 업무 로직을 수행한다. */
    public IncidentPromotion promoteWatchSignal(Cluster cluster,
                                                WatchSignal watchSignal,
                                                String category,
                                                String severity,
                                                String reason,
                                                String actor) {
        Instant observedAt = watchSignal.observedAt() == null ? Instant.now() : watchSignal.observedAt();
        IncidentSignal signal = new IncidentSignal(watchSignal.namespace(), watchSignal.resourceKind(),
                watchSignal.resourceName(), category, severity,
                reason + " · " + watchSignal.resourceKind() + "/" + watchSignal.resourceName(),
                watchSignal.summary(), "관련 리소스의 상태, Event, 로그를 순서대로 검증하세요.", reason, null,
                "watch:" + watchSignal.id(), "KUBERNETES_WATCH", "watch:" + watchSignal.id(), true, observedAt);
        IncidentUpsertResult result = upsert(cluster, signal, actor);
        return new IncidentPromotion(result.incident(), result.notification());
    }

    /** IncidentSignalCoordinator의 analysisSignals 처리에 필요한 업무 로직을 수행한다. */
    static List<IncidentSignal> analysisSignals(Cluster cluster, AnalysisSession analysis, ObjectMapper mapper) {
        if (analysis.resultJson() == null || analysis.resultJson().isBlank()) {
            return List.of();
        }
        JsonNode root = readTree(mapper, analysis.resultJson());
        List<JsonNode> candidates = array(root, "issueGroups");
        if (candidates.isEmpty()) {
            candidates = array(root, "rootCauses");
        }
        List<IncidentSignal> signals = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            JsonNode candidate = candidates.get(index);
            String severity = normalizeSeverity(text(candidate, "severity", text(root, "severity", "MEDIUM")));
            if (!Set.of("CRITICAL", "HIGH").contains(severity)) {
                continue;
            }
            String category = defaultText(firstText(candidate, "category", "type"), "AI_ANALYSIS");
            String title = defaultText(firstText(candidate, "title", "reason"), "AI analysis high-risk finding");
            ResourceTarget target = extractResourceTarget(candidate, title);
            String summary = defaultText(firstText(candidate, "summary", "description", "evidence"), title);
            String nextAction = defaultText(firstText(candidate, "recommendation", "nextAction"),
                    "근거를 확인하고 안전한 검증 명령부터 실행하세요.");
            signals.add(new IncidentSignal(analysis.namespace(), target.kind(), target.name(), category, severity,
                    title, summary, nextAction, title, analysis.id(), hash("analysis:" + analysis.id() + ":" + index),
                    "AI_INFERENCE", "analysis:" + analysis.id(), false, analysis.createdAt()));
        }
        return List.copyOf(signals);
    }

    /** IncidentSignalCoordinator의 eventSignal 처리에 필요한 업무 로직을 수행한다. */
    static IncidentSignal eventSignal(KubernetesEventSnapshot event) {
        String reason = normalizeToken(event.reason());
        int occurrences = event.count() == null ? 1 : event.count();
        if (!"WARNING".equalsIgnoreCase(event.type())
                || (!HIGH_SIGNAL_EVENTS.contains(reason) && occurrences < 3)) {
            return null;
        }
        String severity = occurrences >= 20 || Set.of("FAILEDMOUNT", "FAILEDSCHEDULING", "FAILEDBINDING")
                .contains(reason) ? "HIGH" : "MEDIUM";
        String title = defaultText(event.reason(), "Kubernetes Warning Event") + " · "
                + defaultText(event.involvedKind(), "Resource") + "/" + defaultText(event.involvedName(), "unknown");
        String evidenceKey = hash(String.join(":", "event", defaultText(event.reason(), ""),
                defaultText(event.involvedKind(), ""), defaultText(event.involvedName(), ""),
                String.valueOf(event.eventTime()), String.valueOf(occurrences)));
        return new IncidentSignal(event.namespace(), event.involvedKind(), event.involvedName(), eventCategory(reason),
                severity, title, cleanText(event.message(), 4000),
                "관련 리소스의 describe, 최근 Event, 연결된 Pod 로그를 순서대로 확인하세요.",
                defaultText(event.reason(), "Warning"), null, evidenceKey, "KUBERNETES_EVENT",
                "event:" + defaultText(event.reason(), "Warning"), true,
                event.eventTime() == null ? event.collectedAt() : event.eventTime());
    }

    /** IncidentSignalCoordinator의 upsert 처리에 필요한 업무 로직을 수행한다. */
    private IncidentUpsertResult upsert(Cluster cluster, IncidentSignal signal, String actor) {
        String incidentFingerprint = fingerprint(cluster.id(), signal.namespace(), signal.resourceKind(),
                signal.resourceName(), signal.category(), signal.fingerprintReason());
        Incident current = operationsRepository.findIncidentByFingerprint(incidentFingerprint).orElse(null);
        if (current == null && signal.resourceKind() != null && signal.resourceName() != null) {
            Incident legacy = operationsRepository.findIncidents(cluster.id(), signal.namespace(), null, null, 500).stream()
                    .filter(item -> Objects.equals(item.title(), cleanText(signal.title(), 500)))
                    .filter(item -> item.resourceKind() == null && item.resourceName() == null)
                    .findFirst().orElse(null);
            if (legacy != null) {
                current = operationsRepository.saveIncident(new Incident(legacy.id(), incidentFingerprint,
                        legacy.clusterId(), legacy.clusterName(), signal.namespace(), signal.resourceKind(),
                        signal.resourceName(), signal.category(), maxSeverity(legacy.severity(), signal.severity()),
                        legacy.state(), legacy.title(), legacy.summary(), legacy.nextAction(), legacy.occurrenceCount(),
                        legacy.reopenCount(), legacy.sourceAnalysisId(), legacy.firstDetectedAt(),
                        legacy.lastDetectedAt(), actor));
            }
        }
        boolean evidenceExists = current != null && operationsRepository.findIncidentEvidence(current.id()).stream()
                .anyMatch(item -> item.evidenceKey().equals(signal.evidenceKey()));
        if (evidenceExists) {
            return new IncidentUpsertResult(current, null);
        }
        Instant now = Instant.now();
        boolean reopened = current != null && current.state() == IncidentState.RESOLVED;
        Incident incident = current == null
                ? new Incident(UUID.randomUUID(), incidentFingerprint, cluster.id(), cluster.name(), signal.namespace(),
                signal.resourceKind(), signal.resourceName(), signal.category(), signal.severity(), IncidentState.OPEN,
                cleanText(signal.title(), 500), cleanText(signal.summary(), 4000), cleanText(signal.nextAction(), 2000),
                1, 0, signal.analysisId(), signal.occurredAt() == null ? now : signal.occurredAt(), now, actor)
                : new Incident(current.id(), current.fingerprint(), current.clusterId(), current.clusterName(),
                signal.namespace(), signal.resourceKind(), signal.resourceName(), signal.category(),
                maxSeverity(current.severity(), signal.severity()), reopened ? IncidentState.REOPENED : current.state(),
                cleanText(signal.title(), 500), cleanText(signal.summary(), 4000), cleanText(signal.nextAction(), 2000),
                current.occurrenceCount() + 1, current.reopenCount() + (reopened ? 1 : 0),
                signal.analysisId() == null ? current.sourceAnalysisId() : signal.analysisId(),
                current.firstDetectedAt(), now, actor);
        Incident saved = operationsRepository.saveIncident(incident);
        if (current != null && (reopened || current.state() == IncidentState.MONITORING)) {
            operationsRepository.saveIncidentRecovery(new IncidentRecovery(saved.id(), 0,
                    IncidentRecoveryCoordinator.REQUIRED_HEALTHY_OBSERVATIONS, null, now, "SIGNAL_DETECTED",
                    recoveryCoordinator.supports(saved)));
        }
        operationsRepository.saveIncidentEvidence(new IncidentEvidence(UUID.randomUUID(), saved.id(),
                signal.evidenceKey(), signal.evidenceType(), cleanText(signal.sourceRef(), 1000),
                cleanText(signal.summary(), 4000), signal.factual(), signal.occurredAt() == null ? now : signal.occurredAt()));
        operationsRepository.saveIncidentActivity(new IncidentActivity(UUID.randomUUID(), saved.id(),
                current == null ? "CREATED" : reopened ? "REOPENED" : "EVIDENCE_ADDED",
                current == null ? null : current.state(), saved.state(), signal.title(), actor, now));
        IncidentNotification notification = current == null || reopened
                ? new IncidentNotification(reopened ? "INCIDENT_REOPENED" : "INCIDENT_CREATED", saved.severity(),
                saved.title(), saved.summary(), "/incidents/" + saved.id(), "incident:" + saved.id())
                : null;
        return new IncidentUpsertResult(saved, notification);
    }

    /** IncidentSignalCoordinator의 addNotification 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addNotification(List<IncidentNotification> notifications, IncidentNotification notification) {
        if (notification != null) {
            notifications.add(notification);
        }
    }

    /** IncidentSignalCoordinator의 eventCategory 처리에 필요한 업무 로직을 수행한다. */
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

    /** IncidentSignalCoordinator의 readTree 처리 결과를 조회해 반환한다. */
    private static JsonNode readTree(ObjectMapper mapper, String json) {
        try {
            return mapper.readTree(defaultText(json, "{}"));
        } catch (JsonProcessingException exception) {
            return mapper.createObjectNode();
        }
    }

    /** IncidentSignalCoordinator의 array 처리에 필요한 업무 로직을 수행한다. */
    private static List<JsonNode> array(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isArray()) {
            return List.of();
        }
        List<JsonNode> result = new ArrayList<>();
        value.forEach(result::add);
        return result;
    }

    /** IncidentSignalCoordinator의 text 처리에 필요한 업무 로직을 수행한다. */
    private static String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** IncidentSignalCoordinator의 firstText 처리에 필요한 업무 로직을 수행한다. */
    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** IncidentSignalCoordinator의 extractResourceTarget 처리에 필요한 업무 로직을 수행한다. */
    private static ResourceTarget extractResourceTarget(JsonNode candidate, String title) {
        String kind = firstText(candidate, "targetKind", "resourceKind", "kind");
        String name = firstText(candidate, "targetName", "resourceName", "name");
        JsonNode target = candidate.path("target");
        if ((kind == null || name == null) && target.isObject()) {
            kind = defaultText(kind, firstText(target, "kind", "resourceKind"));
            name = defaultText(name, firstText(target, "name", "resourceName"));
        }
        if (kind != null && name != null) {
            return new ResourceTarget(kind, name);
        }
        Matcher matcher = RESOURCE_REFERENCE_PATTERN.matcher(defaultText(title, ""));
        return matcher.find() ? new ResourceTarget(matcher.group(1), matcher.group(2))
                : new ResourceTarget(kind, name);
    }

    /** IncidentSignalCoordinator의 fingerprint 처리에 필요한 업무 로직을 수행한다. */
    private static String fingerprint(UUID clusterId, String namespace, String kind, String name,
                                      String category, String reason) {
        return hash(String.join("|", clusterId.toString(), normalizeToken(namespace), normalizeToken(kind),
                normalizeToken(name), normalizeToken(category), normalizeToken(reason)));
    }

    /** IncidentSignalCoordinator의 hash 처리 조건의 충족 여부를 판단한다. */
    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** IncidentSignalCoordinator의 normalizeToken 처리 데이터를 필요한 표현으로 변환한다. */
    private static String normalizeToken(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    /** IncidentSignalCoordinator의 normalizeSeverity 처리 데이터를 필요한 표현으로 변환한다. */
    private static String normalizeSeverity(String value) {
        String normalized = value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
        return normalized != null && Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO").contains(normalized)
                ? normalized : "MEDIUM";
    }

    /** IncidentSignalCoordinator의 maxSeverity 처리에 필요한 업무 로직을 수행한다. */
    private static String maxSeverity(String left, String right) {
        return severityScore(left) >= severityScore(right) ? left : right;
    }

    /** IncidentSignalCoordinator의 severityScore 처리에 필요한 업무 로직을 수행한다. */
    private static int severityScore(String severity) {
        return switch (normalizeSeverity(severity)) {
            case "CRITICAL" -> 70;
            case "HIGH" -> 55;
            case "MEDIUM" -> 35;
            case "LOW" -> 20;
            default -> 10;
        };
    }

    /** IncidentSignalCoordinator의 cleanText 처리에 필요한 업무 로직을 수행한다. */
    private static String cleanText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?i)(token|password|secret)\\s*[=:]\\s*[^\\s,]+", "$1=***");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    /** IncidentSignalCoordinator의 defaultText 처리에 필요한 업무 로직을 수행한다. */
    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    record IncidentSignal(String namespace,
                          String resourceKind,
                          String resourceName,
                          String category,
                          String severity,
                          String title,
                          String summary,
                          String nextAction,
                          String fingerprintReason,
                          UUID analysisId,
                          String evidenceKey,
                          String evidenceType,
                          String sourceRef,
                          boolean factual,
                          Instant occurredAt) {
    }

    public record IncidentNotification(String type,
                                       String severity,
                                       String title,
                                       String message,
                                       String targetPath,
                                       String dedupKey) {
    }

    public record IncidentPromotion(Incident incident, IncidentNotification notification) {
    }

    private record IncidentUpsertResult(Incident incident, IncidentNotification notification) {
    }

    private record ResourceTarget(String kind, String name) {
    }
}
