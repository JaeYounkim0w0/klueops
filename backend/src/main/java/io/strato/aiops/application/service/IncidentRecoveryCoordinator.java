package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentActivity;
import io.strato.aiops.domain.operations.OperationsModels.IncidentRecovery;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class IncidentRecoveryCoordinator {

    static final int REQUIRED_HEALTHY_OBSERVATIONS = 2;
    private static final ObjectMapper DECISION_MAPPER = new ObjectMapper();
    private static final Pattern READY_STATUS = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

    private final OperationsRepositoryPort operationsRepository;

    /** IncidentRecoveryCoordinator 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public IncidentRecoveryCoordinator(OperationsRepositoryPort operationsRepository) {
        this.operationsRepository = operationsRepository;
    }

    /** IncidentRecoveryCoordinator의 reconcile 처리에 필요한 업무 로직을 수행한다. */
    public List<RecoveryNotification> reconcile(Cluster cluster,
                                                List<KubernetesResourceSnapshot> resources,
                                                String actor) {
        Map<String, KubernetesResourceSnapshot> currentResources = resources.stream()
                .collect(Collectors.toMap(resource -> resourceKey(resource.namespace(), resource.resourceType(),
                                resource.resourceName()), Function.identity(),
                        (left, right) -> right.collectedAt().isAfter(left.collectedAt()) ? right : left));
        List<RecoveryNotification> notifications = new ArrayList<>();
        for (Incident incident : operationsRepository.findIncidents(cluster.id(), null, null, null, 500)) {
            if (!isAutoResolvable(incident)) {
                continue;
            }
            KubernetesResourceSnapshot resource = currentResources.get(resourceKey(incident.namespace(),
                    canonicalResourceKind(incident.resourceKind()), incident.resourceName()));
            if (!isNewerThanIncident(resource, incident)) {
                continue;
            }
            IncidentRecovery previous = operationsRepository.findIncidentRecovery(incident.id()).orElse(null);
            if (previous != null && previous.lastObservedAt() != null
                    && !resource.collectedAt().isAfter(previous.lastObservedAt())) {
                continue;
            }
            RecoveryDecision decision = decide(incident, resource, previous);
            persistDecision(incident, resource, decision, actor, notifications);
        }
        return List.copyOf(notifications);
    }

    /** IncidentRecoveryCoordinator의 view 처리에 필요한 업무 로직을 수행한다. */
    public IncidentRecovery view(Incident incident) {
        IncidentRecovery stored = operationsRepository.findIncidentRecovery(incident.id()).orElse(null);
        if (stored == null) {
            return new IncidentRecovery(incident.id(), 0, REQUIRED_HEALTHY_OBSERVATIONS,
                    null, null, null, isAutoResolvable(incident));
        }
        return new IncidentRecovery(stored.incidentId(), stored.consecutiveHealthyCount(),
                REQUIRED_HEALTHY_OBSERVATIONS, stored.firstHealthyAt(), stored.lastObservedAt(),
                stored.lastObservedStatus(), isAutoResolvable(incident));
    }

    /** IncidentRecoveryCoordinator의 supports 처리 조건의 충족 여부를 판단한다. */
    public boolean supports(Incident incident) {
        return isAutoResolvable(incident);
    }

    /** IncidentRecoveryCoordinator의 decide 처리에 필요한 업무 로직을 수행한다. */
    static RecoveryDecision decide(Incident incident,
                                   KubernetesResourceSnapshot resource,
                                   IncidentRecovery previous) {
        boolean healthy = isRecoveryHealthy(resource);
        if (healthy) {
            int count = Math.min(REQUIRED_HEALTHY_OBSERVATIONS,
                    (previous == null ? 0 : previous.consecutiveHealthyCount()) + 1);
            Instant firstHealthyAt = previous == null || previous.consecutiveHealthyCount() == 0
                    ? resource.collectedAt() : previous.firstHealthyAt();
            IncidentState nextState = count >= REQUIRED_HEALTHY_OBSERVATIONS
                    ? IncidentState.RESOLVED : IncidentState.MONITORING;
            boolean transition = incident.state() != nextState && incident.state() != IncidentState.RESOLVED;
            String note = count >= REQUIRED_HEALTHY_OBSERVATIONS
                    ? "서로 다른 최신 스냅샷에서 2회 연속 정상 상태가 확인되어 자동 해소했습니다."
                    : "최신 Kubernetes 스냅샷에서 정상 상태가 확인되어 관찰 단계로 전환했습니다.";
            return new RecoveryDecision(true, count, firstHealthyAt, nextState, incident.reopenCount(),
                    transition ? count >= REQUIRED_HEALTHY_OBSERVATIONS ? "AUTO_RESOLVED" : "AUTO_MONITORING" : null,
                    transition && nextState == IncidentState.RESOLVED ? "INCIDENT_AUTO_RESOLVED" : null, note);
        }
        boolean reopen = incident.state() == IncidentState.MONITORING || incident.state() == IncidentState.RESOLVED;
        String note = "관찰 중 새 스냅샷에서 비정상 상태가 다시 확인되어 Incident를 재개했습니다.";
        return new RecoveryDecision(false, 0, null, reopen ? IncidentState.REOPENED : null,
                incident.reopenCount() + (reopen ? 1 : 0), reopen ? "AUTO_REOPENED" : null,
                reopen ? "INCIDENT_REOPENED" : null, note);
    }

    /** IncidentRecoveryCoordinator의 persistDecision 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void persistDecision(Incident incident,
                                 KubernetesResourceSnapshot resource,
                                 RecoveryDecision decision,
                                 String actor,
                                 List<RecoveryNotification> notifications) {
        operationsRepository.saveIncidentRecovery(new IncidentRecovery(incident.id(),
                decision.consecutiveHealthyCount(), REQUIRED_HEALTHY_OBSERVATIONS, decision.firstHealthyAt(),
                resource.collectedAt(), cleanText(resource.status(), 255), true));
        if (decision.activityType() == null || decision.nextState() == null) {
            return;
        }
        Incident saved = operationsRepository.saveIncident(withState(
                incident, decision.nextState(), decision.reopenCount(), actor));
        String note = decision.note() + " status=" + defaultText(resource.status(), "-");
        operationsRepository.saveIncidentActivity(new IncidentActivity(UUID.randomUUID(), saved.id(),
                decision.activityType(), incident.state(), decision.nextState(), note, actor, Instant.now()));
        if (decision.notificationType() != null) {
            notifications.add(new RecoveryNotification(decision.notificationType(),
                    "INCIDENT_AUTO_RESOLVED".equals(decision.notificationType()) ? "LOW" : saved.severity(),
                    saved.title(), note, "/incidents/" + saved.id(),
                    "INCIDENT_AUTO_RESOLVED".equals(decision.notificationType())
                            ? "incident-resolved:" + saved.id() : "incident:" + saved.id()));
        }
    }

    /** IncidentRecoveryCoordinator의 isNewerThanIncident 처리 조건의 충족 여부를 판단한다. */
    private boolean isNewerThanIncident(KubernetesResourceSnapshot resource, Incident incident) {
        return resource != null && resource.collectedAt() != null
                && resource.collectedAt().isAfter(incident.lastDetectedAt());
    }

    /** IncidentRecoveryCoordinator의 isAutoResolvable 처리 조건의 충족 여부를 판단한다. */
    private static boolean isAutoResolvable(Incident incident) {
        if (incident.resourceKind() == null || incident.resourceName() == null) {
            return false;
        }
        String kind = canonicalResourceKind(incident.resourceKind());
        String category = normalizeToken(incident.category());
        if (Set.of("PersistentVolumeClaim", "Endpoint", "Deployment", "StatefulSet", "DaemonSet", "ReplicaSet", "Job")
                .contains(kind)) {
            return true;
        }
        return "Pod".equals(kind) && (category.contains("STORAGE") || category.contains("SCHEDUL")
                || category.contains("CAPACITY") || category.contains("ROLLOUT")
                || category.contains("IMAGE") || category.contains("APPLICATIONSTARTUP"));
    }

    /** IncidentRecoveryCoordinator의 isRecoveryHealthy 처리 조건의 충족 여부를 판단한다. */
    private static boolean isRecoveryHealthy(KubernetesResourceSnapshot resource) {
        String kind = canonicalResourceKind(resource.resourceType());
        String status = defaultText(resource.status(), "").trim();
        if ("Pod".equals(kind)) {
            return Set.of("RUNNING", "SUCCEEDED").contains(status.toUpperCase(Locale.ROOT));
        }
        if ("PersistentVolumeClaim".equals(kind)) {
            return "BOUND".equalsIgnoreCase(status);
        }
        if ("Endpoint".equals(kind)) {
            try {
                return DECISION_MAPPER.readTree(defaultText(resource.summaryJson(), "{}"))
                        .path("readyAddresses").asInt(0) > 0;
            } catch (JsonProcessingException exception) {
                return false;
            }
        }
        if (Set.of("Deployment", "StatefulSet", "DaemonSet", "ReplicaSet", "Job").contains(kind)) {
            Matcher matcher = READY_STATUS.matcher(status);
            return matcher.find() && Integer.parseInt(matcher.group(2)) > 0
                    && Integer.parseInt(matcher.group(1)) >= Integer.parseInt(matcher.group(2));
        }
        return false;
    }

    /** IncidentRecoveryCoordinator의 canonicalResourceKind 처리 조건의 충족 여부를 판단한다. */
    private static String canonicalResourceKind(String kind) {
        String normalized = normalizeToken(kind);
        return switch (normalized) {
            case "POD" -> "Pod";
            case "DEPLOYMENT" -> "Deployment";
            case "STATEFULSET" -> "StatefulSet";
            case "DAEMONSET" -> "DaemonSet";
            case "REPLICASET" -> "ReplicaSet";
            case "PVC", "PERSISTENTVOLUMECLAIM" -> "PersistentVolumeClaim";
            case "ENDPOINT", "ENDPOINTS" -> "Endpoint";
            case "JOB" -> "Job";
            default -> kind;
        };
    }

    /** IncidentRecoveryCoordinator의 withState 처리에 필요한 업무 로직을 수행한다. */
    private static Incident withState(Incident incident, IncidentState state, int reopenCount, String actor) {
        return new Incident(incident.id(), incident.fingerprint(), incident.clusterId(), incident.clusterName(),
                incident.namespace(), incident.resourceKind(), incident.resourceName(), incident.category(),
                incident.severity(), state, incident.title(), incident.summary(), incident.nextAction(),
                incident.occurrenceCount(), reopenCount, incident.sourceAnalysisId(), incident.firstDetectedAt(),
                incident.lastDetectedAt(), actor);
    }

    /** IncidentRecoveryCoordinator의 resourceKey 처리에 필요한 업무 로직을 수행한다. */
    private static String resourceKey(String namespace, String kind, String name) {
        return defaultText(namespace, "") + "|" + kind + "|" + name;
    }

    /** IncidentRecoveryCoordinator의 normalizeToken 처리 데이터를 필요한 표현으로 변환한다. */
    private static String normalizeToken(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    /** IncidentRecoveryCoordinator의 cleanText 처리에 필요한 업무 로직을 수행한다. */
    private static String cleanText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?i)(token|password|secret)\\s*[=:]\\s*[^\\s,]+", "$1=***");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    /** IncidentRecoveryCoordinator의 defaultText 처리에 필요한 업무 로직을 수행한다. */
    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    record RecoveryDecision(boolean healthy,
                            int consecutiveHealthyCount,
                            Instant firstHealthyAt,
                            IncidentState nextState,
                            int reopenCount,
                            String activityType,
                            String notificationType,
                            String note) {
    }

    public record RecoveryNotification(String type,
                                       String severity,
                                       String title,
                                       String message,
                                       String targetPath,
                                       String dedupKey) {
    }
}
