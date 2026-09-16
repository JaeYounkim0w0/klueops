package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.KubernetesResourceSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.application.port.out.SyncJobRepositoryPort;
import io.strato.aiops.domain.operations.OperationsModels.ChangeCandidate;
import io.strato.aiops.domain.operations.OperationsModels.ConfidenceAssessment;
import io.strato.aiops.domain.operations.OperationsModels.CorrelationEdge;
import io.strato.aiops.domain.operations.OperationsModels.CorrelationNode;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentCorrelation;
import io.strato.aiops.domain.operations.OperationsModels.IncidentEvidence;
import io.strato.aiops.domain.operations.OperationsModels.IncidentIntelligence;
import io.strato.aiops.domain.operations.OperationsModels.ResourceChange;
import io.strato.aiops.domain.operations.OperationsModels.VerificationStep;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import io.strato.aiops.domain.sync.SyncJobStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class IncidentIntelligenceService {

    private static final int MAX_GRAPH_NODES = 40;
    private static final Set<String> WORKLOAD_KINDS = Set.of(
            "Deployment", "StatefulSet", "DaemonSet", "ReplicaSet", "Job", "CronJob");

    private final OperationsRepositoryPort operationsRepository;
    private final KubernetesResourceSnapshotRepositoryPort resourceRepository;
    private final SyncJobRepositoryPort syncJobRepository;
    private final ObjectMapper objectMapper;

    /** IncidentIntelligenceService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public IncidentIntelligenceService(
            OperationsRepositoryPort operationsRepository,
            KubernetesResourceSnapshotRepositoryPort resourceRepository,
            SyncJobRepositoryPort syncJobRepository,
            ObjectMapper objectMapper
    ) {
        this.operationsRepository = operationsRepository;
        this.resourceRepository = resourceRepository;
        this.syncJobRepository = syncJobRepository;
        this.objectMapper = objectMapper;
    }

    /** IncidentIntelligenceService의 build 처리에 필요한 결과를 조합해 반환한다. */
    public IncidentIntelligence build(Incident incident, List<IncidentEvidence> evidence) {
        List<KubernetesResourceSnapshot> inventory = currentInventory(incident.clusterId());
        IncidentCorrelation correlation = correlate(incident, inventory);
        List<ChangeCandidate> changes = rankChanges(incident, correlation);
        ConfidenceAssessment confidence = assessConfidence(incident, evidence, correlation, changes);
        return new IncidentIntelligence(correlation, changes, confidence, verificationPlan(incident, correlation));
    }

    /** IncidentIntelligenceService의 correlate 처리에 필요한 업무 로직을 수행한다. */
    private IncidentCorrelation correlate(Incident incident, List<KubernetesResourceSnapshot> inventory) {
        Map<String, KubernetesResourceSnapshot> resources = new LinkedHashMap<>();
        inventory.stream()
                .sorted(Comparator.comparing(KubernetesResourceSnapshot::collectedAt))
                .forEach(item -> resources.put(resourceId(item.namespace(), item.resourceType(), item.resourceName()), item));

        String targetId = resourceId(incident.namespace(), canonicalKind(incident.resourceKind()), incident.resourceName());
        Map<String, CorrelationNode> nodes = new LinkedHashMap<>();
        Map<String, CorrelationEdge> edges = new LinkedHashMap<>();
        KubernetesResourceSnapshot target = resources.get(targetId);
        if (target != null) {
            addNode(nodes, target, "TARGET", false);
        } else {
            nodes.put(targetId, new CorrelationNode(targetId, incident.namespace(), canonicalKind(incident.resourceKind()),
                    incident.resourceName(), "NOT_IN_LATEST_SNAPSHOT", "TARGET", true, true));
        }

        for (KubernetesResourceSnapshot resource : inventory) {
            String id = resourceId(resource.namespace(), resource.resourceType(), resource.resourceName());
            JsonNode summary = read(resource.summaryJson());
            addOwnerEdges(resource, summary, resources, nodes, edges);
            addVolumeEdges(resource, summary, resources, nodes, edges);
            addTargetReferenceEdges(resource, summary, resources, nodes, edges);
            if ("Service".equals(resource.resourceType())) {
                addServiceEdges(resource, summary, inventory, resources, nodes, edges);
            }
            if ("Ingress".equals(resource.resourceType())) {
                for (JsonNode backend : array(summary, "backends")) {
                    connect(id, resource.namespace(), "Service", text(backend, "serviceName"), "ROUTES_TO",
                            false, "Ingress backend", resources, nodes, edges);
                }
            }
        }

        Set<String> connected = connectedComponent(targetId, edges.values());
        nodes.entrySet().removeIf(entry -> !connected.contains(entry.getKey()));
        edges.entrySet().removeIf(entry -> !nodes.containsKey(entry.getValue().sourceId())
                || !nodes.containsKey(entry.getValue().targetId()));
        if (nodes.size() > MAX_GRAPH_NODES) {
            Set<String> keep = nodes.keySet().stream().limit(MAX_GRAPH_NODES).collect(LinkedHashSet::new,
                    LinkedHashSet::add, LinkedHashSet::addAll);
            nodes.entrySet().removeIf(entry -> !keep.contains(entry.getKey()));
            edges.entrySet().removeIf(entry -> !keep.contains(entry.getValue().sourceId())
                    || !keep.contains(entry.getValue().targetId()));
        }

        int workloads = (int) nodes.values().stream().filter(node -> WORKLOAD_KINDS.contains(node.resourceKind())).count();
        int services = (int) nodes.values().stream().filter(node -> "Service".equals(node.resourceKind())).count();
        Instant collectedAt = inventory.stream().map(KubernetesResourceSnapshot::collectedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        String summary = nodes.size() <= 1
                ? "현재 스냅샷에서 직접 연결된 영향 리소스를 확인하지 못했습니다."
                : "대상과 연결된 리소스 " + Math.max(0, nodes.size() - 1) + "개를 확인했습니다. "
                + "workload " + workloads + "개, service " + services + "개가 영향 범위 후보입니다.";
        return new IncidentCorrelation(List.copyOf(nodes.values()), List.copyOf(edges.values()),
                Math.max(0, nodes.size() - 1), workloads, services, summary, collectedAt);
    }

    /** IncidentIntelligenceService의 addOwnerEdges 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addOwnerEdges(KubernetesResourceSnapshot resource, JsonNode summary,
                               Map<String, KubernetesResourceSnapshot> resources,
                               Map<String, CorrelationNode> nodes, Map<String, CorrelationEdge> edges) {
        String childId = resourceId(resource.namespace(), resource.resourceType(), resource.resourceName());
        for (JsonNode owner : array(summary, "ownerReferences")) {
            connect(childId, resource.namespace(), text(owner, "kind"), text(owner, "name"), "OWNED_BY",
                    false, "metadata.ownerReferences", resources, nodes, edges);
        }
        if ("ReplicaSet".equals(resource.resourceType()) && array(summary, "ownerReferences").isEmpty()) {
            String name = resource.resourceName();
            int suffix = name == null ? -1 : name.lastIndexOf('-');
            if (suffix > 0) {
                connect(childId, resource.namespace(), "Deployment", name.substring(0, suffix), "OWNED_BY",
                        true, "ReplicaSet name convention", resources, nodes, edges);
            }
        }
    }

    /** IncidentIntelligenceService의 addVolumeEdges 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addVolumeEdges(KubernetesResourceSnapshot resource, JsonNode summary,
                                Map<String, KubernetesResourceSnapshot> resources,
                                Map<String, CorrelationNode> nodes, Map<String, CorrelationEdge> edges) {
        String sourceId = resourceId(resource.namespace(), resource.resourceType(), resource.resourceName());
        for (JsonNode volume : array(summary, "volumes")) {
            String kind = text(volume, "sourceKind");
            String name = text(volume, "sourceName");
            if (kind != null && name != null && !"Other".equals(kind)) {
                connect(sourceId, resource.namespace(), kind, name, "MOUNTS", false,
                        "spec.volumes/" + defaultText(text(volume, "name"), "volume"), resources, nodes, edges);
            }
        }
    }

    /** IncidentIntelligenceService의 addTargetReferenceEdges 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addTargetReferenceEdges(KubernetesResourceSnapshot resource, JsonNode summary,
                                         Map<String, KubernetesResourceSnapshot> resources,
                                         Map<String, CorrelationNode> nodes, Map<String, CorrelationEdge> edges) {
        String sourceId = resourceId(resource.namespace(), resource.resourceType(), resource.resourceName());
        String targetKind = text(summary, "targetKind");
        String targetName = text(summary, "targetName");
        if (targetKind != null && targetName != null) {
            connect(sourceId, resource.namespace(), targetKind, targetName, "TARGETS", false,
                    "spec.scaleTargetRef", resources, nodes, edges);
        }
    }

    /** IncidentIntelligenceService의 addServiceEdges 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addServiceEdges(KubernetesResourceSnapshot service, JsonNode serviceSummary,
                                 List<KubernetesResourceSnapshot> inventory,
                                 Map<String, KubernetesResourceSnapshot> resources,
                                 Map<String, CorrelationNode> nodes, Map<String, CorrelationEdge> edges) {
        String serviceId = resourceId(service.namespace(), service.resourceType(), service.resourceName());
        JsonNode selector = serviceSummary.path("selector");
        for (KubernetesResourceSnapshot candidate : inventory) {
            if (!Objects.equals(service.namespace(), candidate.namespace())) {
                continue;
            }
            JsonNode summary = read(candidate.summaryJson());
            JsonNode labels = "Pod".equals(candidate.resourceType()) ? summary.path("labels") : summary.path("templateLabels");
            if (!selector.isObject() || selector.isEmpty() || !labelsMatch(selector, labels)) {
                continue;
            }
            String relation = "Pod".equals(candidate.resourceType()) ? "SELECTS_POD" : "SELECTS_WORKLOAD";
            connect(serviceId, candidate.namespace(), candidate.resourceType(), candidate.resourceName(), relation,
                    !"Pod".equals(candidate.resourceType()), "Service selector matches labels", resources, nodes, edges);
        }
        connect(serviceId, service.namespace(), "Endpoint", service.resourceName(), "HAS_ENDPOINTS", false,
                "same namespace/name", resources, nodes, edges);
    }

    /** IncidentIntelligenceService의 connect 처리에 필요한 업무 로직을 수행한다. */
    private void connect(String sourceId, String namespace, String targetKind, String targetName, String relation,
                         boolean inferred, String evidence, Map<String, KubernetesResourceSnapshot> resources,
                         Map<String, CorrelationNode> nodes, Map<String, CorrelationEdge> edges) {
        if (sourceId == null || targetKind == null || targetKind.isBlank() || targetName == null || targetName.isBlank()) {
            return;
        }
        String targetId = resourceId(namespace, canonicalKind(targetKind), targetName);
        KubernetesResourceSnapshot source = resources.get(sourceId);
        KubernetesResourceSnapshot target = resources.get(targetId);
        if (source == null && !nodes.containsKey(sourceId) || target == null && !nodes.containsKey(targetId)) {
            return;
        }
        if (source != null) addNode(nodes, source, "RELATED", inferred);
        if (target != null) addNode(nodes, target, relationRole(relation), inferred);
        String edgeKey = sourceId + "|" + relation + "|" + targetId;
        edges.putIfAbsent(edgeKey, new CorrelationEdge(sourceId, targetId, relation, inferred, evidence));
    }

    /** IncidentIntelligenceService의 rankChanges 처리에 필요한 업무 로직을 수행한다. */
    private List<ChangeCandidate> rankChanges(Incident incident, IncidentCorrelation correlation) {
        Set<String> graph = correlation.nodes().stream().map(CorrelationNode::id).collect(LinkedHashSet::new,
                LinkedHashSet::add, LinkedHashSet::addAll);
        Instant anchor = incident.firstDetectedAt() == null ? incident.lastDetectedAt() : incident.firstDetectedAt();
        return operationsRepository.findResourceChanges(incident.clusterId(), incident.namespace(), 500).stream()
                .map(change -> scoreChange(incident, change, graph, anchor))
                .filter(candidate -> candidate.relevanceScore() >= 25)
                .sorted(Comparator.comparingInt(ChangeCandidate::relevanceScore).reversed()
                        .thenComparing(ChangeCandidate::detectedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();
    }

    /** IncidentIntelligenceService의 scoreChange 처리에 필요한 업무 로직을 수행한다. */
    private ChangeCandidate scoreChange(Incident incident, ResourceChange change, Set<String> graph, Instant anchor) {
        String id = resourceId(change.namespace(), change.resourceKind(), change.resourceName());
        boolean target = sameResource(incident, change);
        boolean related = graph.contains(id);
        long minutes = anchor == null || change.detectedAt() == null ? Long.MAX_VALUE
                : Math.abs(Duration.between(change.detectedAt(), anchor).toMinutes());
        int score = target ? 55 : related ? 35 : 0;
        if (minutes <= 15) score += 30;
        else if (minutes <= 60) score += 20;
        else if (minutes <= 360) score += 10;
        if (Set.of("CREATED", "DELETED", "STATUS_CHANGED").contains(change.changeType())) score += 10;
        score = Math.min(100, score);
        String relation = target ? "TARGET" : related ? "DEPENDENCY" : "SAME_NAMESPACE";
        String explanation = target ? "문제 대상 자체의 변경입니다."
                : related ? "영향 범위 그래프에 포함된 의존 리소스의 변경입니다."
                : "같은 namespace의 변경이지만 직접 연결 근거는 아직 없습니다.";
        if (minutes != Long.MAX_VALUE) explanation += " 최초 감지와 " + minutes + "분 차이입니다.";
        return new ChangeCandidate(change.id(), change.namespace(), change.resourceKind(), change.resourceName(),
                change.changeType(), change.summary(), change.detectedAt(), score, relation, explanation);
    }

    /** IncidentIntelligenceService의 assessConfidence 처리에 필요한 업무 로직을 수행한다. */
    private ConfidenceAssessment assessConfidence(Incident incident, List<IncidentEvidence> evidence,
                                                  IncidentCorrelation correlation, List<ChangeCandidate> changes) {
        int facts = (int) evidence.stream().filter(IncidentEvidence::factual).count();
        int inferences = evidence.size() - facts;
        int verifiedRelations = (int) correlation.edges().stream().filter(edge -> !edge.inferred()).count();
        List<String> missing = new ArrayList<>();
        List<String> rationale = new ArrayList<>();
        int score = 15;
        if (facts > 0) {
            score += Math.min(30, facts * 12);
            rationale.add("Kubernetes 사실 근거 " + facts + "개가 연결되었습니다.");
        } else {
            missing.add("Kubernetes Event 또는 상태 사실 근거");
        }
        if (correlation.inventoryCollectedAt() != null) {
            long age = Math.max(0, Duration.between(correlation.inventoryCollectedAt(), Instant.now()).toMinutes());
            int staleLimit = operationsRepository.getOperationSettings().staleSyncMinutes();
            if (age <= staleLimit) {
                score += 20;
                rationale.add("최신 성공 동기화가 " + age + "분 전입니다.");
            } else {
                score += 5;
                missing.add("최근 " + staleLimit + "분 이내 리소스 동기화");
            }
        } else {
            missing.add("성공한 리소스 동기화 스냅샷");
        }
        if (verifiedRelations > 0) {
            score += Math.min(20, verifiedRelations * 4);
            rationale.add("명시적 Kubernetes 관계 " + verifiedRelations + "개를 검증했습니다.");
        } else {
            missing.add("ownerReference, selector 또는 volume 참조 관계");
        }
        if (!changes.isEmpty() && changes.get(0).relevanceScore() >= 60) {
            score += 15;
            rationale.add("시간과 리소스 관계가 가까운 변경 후보가 있습니다.");
        } else {
            missing.add("문제 발생 전후의 고연관 변경 이력");
        }
        if (facts == 0 && inferences > 0) score -= 15;
        score = Math.max(0, Math.min(100, score));
        String level = score >= 80 ? "HIGH" : score >= 55 ? "MEDIUM" : "LOW";
        String freshness = correlation.inventoryCollectedAt() == null ? "UNKNOWN"
                : Duration.between(correlation.inventoryCollectedAt(), Instant.now()).toMinutes()
                <= operationsRepository.getOperationSettings().staleSyncMinutes() ? "FRESH" : "STALE";
        return new ConfidenceAssessment(score, level, freshness, facts, inferences, verifiedRelations,
                List.copyOf(missing), List.copyOf(rationale));
    }

    /** IncidentIntelligenceService의 verificationPlan 처리에 필요한 업무 로직을 수행한다. */
    private List<VerificationStep> verificationPlan(Incident incident, IncidentCorrelation correlation) {
        String namespace = defaultText(incident.namespace(), "default");
        String kind = commandKind(canonicalKind(incident.resourceKind()));
        String name = defaultText(incident.resourceName(), "RESOURCE_NAME");
        List<VerificationStep> steps = new ArrayList<>();
        steps.add(step(1, "대상 상태 확인", "현재 spec, status, condition과 최근 Event를 한 번에 확인합니다.",
                "kubectl describe " + kind + "/" + name + " -n " + namespace,
                "Incident 요약과 일치하는 상태 또는 Warning Event", "READ_ONLY"));
        steps.add(step(2, "최근 이벤트 확인", "같은 대상의 반복 횟수와 최초/최근 발생 시간을 확인합니다.",
                "kubectl get events -n " + namespace + " --field-selector involvedObject.name=" + name
                        + " --sort-by=.lastTimestamp",
                "원인 후보와 시간 순서가 일치하는 Event", "READ_ONLY"));
        CorrelationNode dependency = correlation.nodes().stream()
                .filter(node -> !"TARGET".equals(node.role()))
                .filter(node -> !node.inferred())
                .findFirst().orElse(null);
        if (dependency != null) {
            steps.add(step(3, "연결 리소스 검증", "추정이 아닌 Kubernetes 참조로 연결된 리소스 상태를 확인합니다.",
                    "kubectl get " + commandKind(dependency.resourceKind()) + "/" + dependency.resourceName()
                            + " -n " + defaultText(dependency.namespace(), namespace) + " -o yaml",
                    "참조 이름, selector, port 또는 volume 설정의 일치 여부", "READ_ONLY"));
        }
        if ("Pod".equals(canonicalKind(incident.resourceKind()))) {
            steps.add(step(steps.size() + 1, "컨테이너 로그 확인", "애플리케이션 시작 실패와 종료 직전 오류를 확인합니다.",
                    "kubectl logs pod/" + name + " -n " + namespace + " --all-containers --tail=300",
                    "stack trace, bind/listen port, 설정 또는 의존성 실패 메시지", "READ_ONLY"));
        }
        return List.copyOf(steps);
    }

    /** IncidentIntelligenceService의 step 처리에 필요한 업무 로직을 수행한다. */
    private VerificationStep step(int order, String title, String purpose, String command,
                                  String expected, String safety) {
        return new VerificationStep(order, title, purpose, command, expected, safety, false);
    }

    /** IncidentIntelligenceService의 currentInventory 처리에 필요한 업무 로직을 수행한다. */
    private List<KubernetesResourceSnapshot> currentInventory(UUID clusterId) {
        return syncJobRepository.findLatestByClusterIdAndStatusIn(clusterId, EnumSet.of(SyncJobStatus.SUCCEEDED))
                .map(job -> {
                    List<KubernetesResourceSnapshot> result = new ArrayList<>();
                    int page = 0;
                    int totalPages;
                    do {
                        var slice = resourceRepository.findPageBySyncJobId(job.id(), null, null, page, 200);
                        result.addAll(slice.items());
                        totalPages = Math.min(slice.totalPages(), 50);
                        page++;
                    } while (page < totalPages);
                    return List.copyOf(result);
                }).orElseGet(List::of);
    }

    /** IncidentIntelligenceService의 connectedComponent 처리에 필요한 업무 로직을 수행한다. */
    private Set<String> connectedComponent(String target, Iterable<CorrelationEdge> edges) {
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        for (CorrelationEdge edge : edges) {
            adjacency.computeIfAbsent(edge.sourceId(), ignored -> new LinkedHashSet<>()).add(edge.targetId());
            adjacency.computeIfAbsent(edge.targetId(), ignored -> new LinkedHashSet<>()).add(edge.sourceId());
        }
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(target);
        while (!queue.isEmpty() && visited.size() < MAX_GRAPH_NODES) {
            String current = queue.removeFirst();
            if (!visited.add(current)) continue;
            adjacency.getOrDefault(current, Set.of()).stream().filter(item -> !visited.contains(item)).forEach(queue::addLast);
        }
        return visited;
    }

    /** IncidentIntelligenceService의 addNode 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addNode(Map<String, CorrelationNode> nodes, KubernetesResourceSnapshot resource,
                         String role, boolean inferred) {
        String id = resourceId(resource.namespace(), resource.resourceType(), resource.resourceName());
        CorrelationNode previous = nodes.get(id);
        String effectiveRole = previous != null && "TARGET".equals(previous.role()) ? "TARGET" : role;
        nodes.put(id, new CorrelationNode(id, resource.namespace(), resource.resourceType(), resource.resourceName(),
                resource.status(), effectiveRole, isUnhealthy(resource), inferred && (previous == null || previous.inferred())));
    }

    /** IncidentIntelligenceService의 isUnhealthy 처리 조건의 충족 여부를 판단한다. */
    private boolean isUnhealthy(KubernetesResourceSnapshot resource) {
        String status = defaultText(resource.status(), "").toUpperCase(Locale.ROOT);
        if ("Pod".equals(resource.resourceType())) return Set.of("PENDING", "FAILED", "UNKNOWN").contains(status);
        if ("PersistentVolumeClaim".equals(resource.resourceType())) return !"BOUND".equals(status);
        if (WORKLOAD_KINDS.contains(resource.resourceType()) && status.matches("\\d+/\\d+")) {
            String[] parts = status.split("/");
            return !parts[0].equals(parts[1]);
        }
        return false;
    }

    /** IncidentIntelligenceService의 labelsMatch 처리에 필요한 업무 로직을 수행한다. */
    private boolean labelsMatch(JsonNode selector, JsonNode labels) {
        if (!selector.isObject() || selector.isEmpty() || !labels.isObject()) return false;
        for (var field : selector.properties()) {
            if (!field.getValue().asText().equals(labels.path(field.getKey()).asText(null))) return false;
        }
        return true;
    }

    /** IncidentIntelligenceService의 sameResource 처리에 필요한 업무 로직을 수행한다. */
    private boolean sameResource(Incident incident, ResourceChange change) {
        return Objects.equals(defaultText(incident.namespace(), ""), defaultText(change.namespace(), ""))
                && Objects.equals(canonicalKind(incident.resourceKind()), canonicalKind(change.resourceKind()))
                && Objects.equals(incident.resourceName(), change.resourceName());
    }

    /** IncidentIntelligenceService의 read 처리 결과를 조회해 반환한다. */
    private JsonNode read(String value) {
        if (value == null || value.isBlank()) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    /** IncidentIntelligenceService의 array 처리에 필요한 업무 로직을 수행한다. */
    private List<JsonNode> array(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) return List.of();
        List<JsonNode> result = new ArrayList<>();
        value.forEach(result::add);
        return result;
    }

    /** IncidentIntelligenceService의 text 처리에 필요한 업무 로직을 수행한다. */
    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    /** IncidentIntelligenceService의 resourceId 처리에 필요한 업무 로직을 수행한다. */
    private String resourceId(String namespace, String kind, String name) {
        return defaultText(namespace, "_cluster") + "/" + defaultText(canonicalKind(kind), "Resource")
                + "/" + defaultText(name, "unknown");
    }

    /** IncidentIntelligenceService의 canonicalKind 처리 조건의 충족 여부를 판단한다. */
    private String canonicalKind(String kind) {
        if (kind == null) return null;
        return switch (kind.toLowerCase(Locale.ROOT).replace("-", "")) {
            case "pod", "pods" -> "Pod";
            case "deployment", "deployments" -> "Deployment";
            case "statefulset", "statefulsets" -> "StatefulSet";
            case "daemonset", "daemonsets" -> "DaemonSet";
            case "replicaset", "replicasets" -> "ReplicaSet";
            case "service", "services" -> "Service";
            case "endpoint", "endpoints" -> "Endpoint";
            case "ingress", "ingresses" -> "Ingress";
            case "configmap", "configmaps" -> "ConfigMap";
            case "secret", "secrets" -> "Secret";
            case "persistentvolumeclaim", "persistentvolumeclaims", "pvc" -> "PersistentVolumeClaim";
            case "persistentvolume", "persistentvolumes", "pv" -> "PersistentVolume";
            case "horizontalpodautoscaler", "horizontalpodautoscalers", "hpa" -> "HorizontalPodAutoscaler";
            default -> kind;
        };
    }

    /** IncidentIntelligenceService의 commandKind 처리에 필요한 업무 로직을 수행한다. */
    private String commandKind(String kind) {
        if (kind == null) return "resource";
        return switch (kind) {
            case "PersistentVolumeClaim" -> "pvc";
            case "PersistentVolume" -> "pv";
            case "HorizontalPodAutoscaler" -> "hpa";
            case "Endpoint" -> "endpoints";
            default -> kind.toLowerCase(Locale.ROOT);
        };
    }

    /** IncidentIntelligenceService의 relationRole 처리에 필요한 업무 로직을 수행한다. */
    private String relationRole(String relation) {
        return switch (relation) {
            case "OWNED_BY" -> "OWNER";
            case "MOUNTS" -> "DEPENDENCY";
            case "SELECTS_POD", "SELECTS_WORKLOAD" -> "TRAFFIC_TARGET";
            case "HAS_ENDPOINTS" -> "ENDPOINT";
            default -> "RELATED";
        };
    }

    /** IncidentIntelligenceService의 defaultText 처리에 필요한 업무 로직을 수행한다. */
    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
