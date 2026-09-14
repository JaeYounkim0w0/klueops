package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.operations.OperationsModels.PolicyDefinition;
import io.strato.aiops.domain.operations.OperationsModels.PolicyEvaluation;
import io.strato.aiops.domain.operations.OperationsModels.PolicyResult;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OperationsPolicyEvaluator {

    private static final Set<String> WORKLOAD_KINDS = Set.of(
            "Deployment", "StatefulSet", "ReplicaSet", "DaemonSet");
    private static final Set<String> SCALABLE_WORKLOAD_KINDS = Set.of("Deployment", "StatefulSet");
    private static final Set<String> REFERENCE_KINDS = Set.of(
            "ConfigMap", "Secret", "PersistentVolumeClaim");

    private final ObjectMapper objectMapper;

    public OperationsPolicyEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<PolicyEvaluation> evaluate(Cluster cluster,
                                           List<KubernetesResourceSnapshot> resources,
                                           List<PolicyDefinition> definitions) {
        Objects.requireNonNull(cluster, "cluster must not be null");
        List<KubernetesResourceSnapshot> snapshots = resources == null ? List.of() : resources;
        Map<String, PolicyDefinition> policies = (definitions == null ? List.<PolicyDefinition>of() : definitions)
                .stream()
                .filter(PolicyDefinition::enabled)
                .collect(Collectors.toMap(PolicyDefinition::id, Function.identity(), (left, right) -> right));
        List<PolicyEvaluation> result = new ArrayList<>();
        Instant now = Instant.now();
        Set<String> namespaces = snapshots.stream().map(KubernetesResourceSnapshot::namespace)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Map<String, KubernetesResourceSnapshot> endpoints = snapshots.stream()
                .filter(resource -> "Endpoint".equals(resource.resourceType()))
                .collect(Collectors.toMap(resource -> resourceKey(resource.namespace(), "Endpoint", resource.resourceName()),
                        Function.identity(), (left, right) -> right));
        Map<String, Set<String>> resourceKindsByNamespace = snapshots.stream()
                .filter(resource -> resource.namespace() != null)
                .collect(Collectors.groupingBy(KubernetesResourceSnapshot::namespace,
                        Collectors.mapping(KubernetesResourceSnapshot::resourceType, Collectors.toSet())));
        Set<String> namespacesWithContainerEvidence = snapshots.stream()
                .filter(resource -> resource.namespace() != null)
                .filter(resource -> WORKLOAD_KINDS.contains(resource.resourceType()))
                .filter(resource -> {
                    JsonNode containers = readTree(resource.summaryJson()).path("containers");
                    return containers.isArray() && !containers.isEmpty();
                })
                .map(KubernetesResourceSnapshot::namespace)
                .collect(Collectors.toSet());
        Set<String> resourceNames = snapshots.stream()
                .map(resource -> resourceKey(resource.namespace(), resource.resourceType(), resource.resourceName()))
                .collect(Collectors.toSet());
        Set<String> hpaTargets = snapshots.stream()
                .filter(resource -> "HorizontalPodAutoscaler".equals(resource.resourceType()))
                .map(resource -> {
                    JsonNode summary = readTree(resource.summaryJson());
                    return resourceKey(resource.namespace(), summary.path("targetKind").asText(),
                            summary.path("targetName").asText());
                })
                .collect(Collectors.toSet());
        Map<String, List<JsonNode>> pdbSelectors = snapshots.stream()
                .filter(resource -> "PodDisruptionBudget".equals(resource.resourceType()))
                .collect(Collectors.groupingBy(KubernetesResourceSnapshot::namespace,
                        Collectors.mapping(resource -> readTree(resource.summaryJson()).path("selector"),
                                Collectors.toList())));
        Map<String, List<KubernetesResourceSnapshot>> workloadsByNamespace = snapshots.stream()
                .filter(resource -> Set.of("Deployment", "StatefulSet", "DaemonSet")
                        .contains(resource.resourceType()))
                .collect(Collectors.groupingBy(resource -> defaultText(resource.namespace(), "")));

        snapshots.forEach(resource -> evaluateResource(result, policies, cluster, resource, endpoints,
                workloadsByNamespace, resourceNames, hpaTargets, pdbSelectors, now));
        namespaces.forEach(namespace -> evaluateNamespace(result, policies, cluster, namespace,
                resourceKindsByNamespace.getOrDefault(namespace, Set.of()),
                namespacesWithContainerEvidence.contains(namespace), now));
        return List.copyOf(result);
    }

    private void evaluateResource(List<PolicyEvaluation> result,
                                  Map<String, PolicyDefinition> policies,
                                  Cluster cluster,
                                  KubernetesResourceSnapshot resource,
                                  Map<String, KubernetesResourceSnapshot> endpoints,
                                  Map<String, List<KubernetesResourceSnapshot>> workloadsByNamespace,
                                  Set<String> resourceNames,
                                  Set<String> hpaTargets,
                                  Map<String, List<JsonNode>> pdbSelectors,
                                  Instant now) {
        JsonNode summary = readTree(resource.summaryJson());
        switch (resource.resourceType()) {
            case "Deployment", "StatefulSet", "ReplicaSet", "DaemonSet" -> {
                int desired = firstInt(summary, "desiredReplicas", "desired");
                int available = firstInt(summary, "availableReplicas", "ready");
                addEvaluation(result, policies.get("WORKLOAD_AVAILABILITY"), cluster, resource,
                        desired > 0 && available < desired ? PolicyResult.FAIL : PolicyResult.PASS,
                        "available=" + available + ", desired=" + desired,
                        "rollout 상태와 준비되지 않은 Pod의 Event/로그를 확인하세요.", now);
                addEvaluation(result, policies.get("SINGLE_REPLICA"), cluster, resource,
                        desired == 1 ? PolicyResult.WARN : desired <= 0 ? PolicyResult.NOT_APPLICABLE : PolicyResult.PASS,
                        "desired replicas=" + desired,
                        "가용성이 필요한 workload는 replica와 PDB 기준을 함께 검토하세요.", now);
                addContainerPolicyEvaluations(result, policies, cluster, resource, summary.path("containers"), now);
                addReferencePolicyEvaluation(result, policies, cluster, resource, summary.path("volumes"),
                        resourceNames, now);
                if (SCALABLE_WORKLOAD_KINDS.contains(resource.resourceType())) {
                    boolean multiReplica = desired > 1;
                    boolean hpaPresent = hpaTargets.contains(resourceKey(resource.namespace(),
                            resource.resourceType(), resource.resourceName()));
                    addEvaluation(result, policies.get("HPA_COVERAGE"), cluster, resource,
                            !multiReplica ? PolicyResult.NOT_APPLICABLE
                                    : hpaPresent ? PolicyResult.PASS : PolicyResult.WARN,
                            !multiReplica ? "desired replicas <= 1" : "matching HPA present=" + hpaPresent,
                            "고정 replica가 의도된 것인지 확인하고 변동 부하라면 HPA를 검토하세요.", now);
                    JsonNode workloadLabels = summary.path("templateLabels");
                    boolean pdbEvidenceAvailable = workloadLabels.isObject() && !workloadLabels.isEmpty();
                    boolean pdbPresent = pdbSelectors.getOrDefault(resource.namespace(), List.of()).stream()
                            .anyMatch(selector -> selectorMatches(selector, workloadLabels));
                    addEvaluation(result, policies.get("PDB_COVERAGE"), cluster, resource,
                            !multiReplica || !pdbEvidenceAvailable ? PolicyResult.NOT_APPLICABLE
                                    : pdbPresent ? PolicyResult.PASS : PolicyResult.WARN,
                            !multiReplica ? "desired replicas <= 1"
                                    : !pdbEvidenceAvailable ? "workload label evidence unavailable"
                                    : "matching PDB present=" + pdbPresent,
                            "노드 유지보수와 voluntary disruption 시 최소 가용 Pod 수를 검토하세요.", now);
                }
            }
            case "Pod" -> {
                int restarts = summary.path("restartCount").asInt(0);
                String phase = defaultText(summary.path("phase").asText(null), resource.status());
                boolean unhealthy = Set.of("PENDING", "FAILED", "UNKNOWN").contains(normalizeUpper(phase))
                        || restarts >= 5;
                addEvaluation(result, policies.get("POD_UNHEALTHY"), cluster, resource,
                        unhealthy ? PolicyResult.FAIL : PolicyResult.PASS,
                        "phase=" + phase + ", restartCount=" + restarts,
                        "Pod describe와 현재/이전 컨테이너 로그를 확인하세요.", now);
                addReferencePolicyEvaluation(result, policies, cluster, resource, summary.path("volumes"),
                        resourceNames, now);
            }
            case "PersistentVolumeClaim" -> {
                String phase = defaultText(summary.path("phase").asText(null), resource.status());
                addEvaluation(result, policies.get("PVC_PENDING"), cluster, resource,
                        "BOUND".equals(normalizeUpper(phase)) ? PolicyResult.PASS : PolicyResult.FAIL,
                        "phase=" + phase, "PVC Event, StorageClass, PV binding 조건을 확인하세요.", now);
            }
            case "Service" -> evaluateService(result, policies, cluster, resource, summary, endpoints,
                    workloadsByNamespace, now);
            default -> {
            }
        }
    }

    private void evaluateService(List<PolicyEvaluation> result,
                                 Map<String, PolicyDefinition> policies,
                                 Cluster cluster,
                                 KubernetesResourceSnapshot resource,
                                 JsonNode summary,
                                 Map<String, KubernetesResourceSnapshot> endpoints,
                                 Map<String, List<KubernetesResourceSnapshot>> workloadsByNamespace,
                                 Instant now) {
        KubernetesResourceSnapshot endpoint = endpoints.get(
                resourceKey(resource.namespace(), "Endpoint", resource.resourceName()));
        PolicyResult endpointResult = endpoint == null ? PolicyResult.NOT_APPLICABLE
                : readTree(endpoint.summaryJson()).path("readyAddresses").asInt(0) == 0
                ? PolicyResult.FAIL : PolicyResult.PASS;
        String endpointEvidence = endpoint == null ? "matching Endpoint snapshot not found"
                : "readyAddresses=" + readTree(endpoint.summaryJson()).path("readyAddresses").asInt(0);
        addEvaluation(result, policies.get("SERVICE_ENDPOINT_EMPTY"), cluster, resource, endpointResult,
                endpointEvidence, "Service selector, Pod readiness, targetPort를 함께 확인하세요.", now);
        List<KubernetesResourceSnapshot> workloads = workloadsByNamespace.getOrDefault(
                defaultText(resource.namespace(), ""), List.of());
        PolicyResult portResult = servicePortPolicyResult(summary, workloads);
        addEvaluation(result, policies.get("PORT_MISMATCH"), cluster, resource, portResult,
                servicePortEvidence(portResult),
                "Service selector가 선택한 workload의 named/numeric containerPort와 targetPort를 비교하세요.", now);
    }

    private void evaluateNamespace(List<PolicyEvaluation> result,
                                   Map<String, PolicyDefinition> policies,
                                   Cluster cluster,
                                   String namespace,
                                   Set<String> kinds,
                                   boolean hasContainerEvidence,
                                   Instant now) {
        boolean hasNetworkPolicy = kinds.contains("NetworkPolicy");
        addScopeEvaluation(result, policies.get("NETWORK_POLICY_MISSING"), cluster, namespace,
                hasNetworkPolicy ? PolicyResult.PASS : PolicyResult.WARN,
                hasNetworkPolicy ? "NetworkPolicy snapshot exists" : "NetworkPolicy snapshot not found",
                "namespace 통신 요구사항에 맞는 기본 deny/allow 정책을 검토하세요.", now);
        boolean hasQuota = kinds.contains("ResourceQuota");
        addScopeEvaluation(result, policies.get("RESOURCE_QUOTA_MISSING"), cluster, namespace,
                hasQuota ? PolicyResult.PASS : PolicyResult.WARN,
                hasQuota ? "ResourceQuota snapshot exists" : "ResourceQuota snapshot not found",
                "Namespace workload 특성에 맞는 requests/limits quota를 검토하세요.", now);
        boolean hasLimitRange = kinds.contains("LimitRange");
        addScopeEvaluation(result, policies.get("LIMIT_RANGE_MISSING"), cluster, namespace,
                hasLimitRange ? PolicyResult.PASS : PolicyResult.WARN,
                hasLimitRange ? "LimitRange snapshot exists" : "LimitRange snapshot not found",
                "기본 request/limit 값을 제공하는 LimitRange를 검토하세요.", now);
        addScopeEvaluation(result, policies.get("RESOURCE_GOVERNANCE_UNKNOWN"), cluster, namespace,
                hasContainerEvidence ? PolicyResult.PASS : PolicyResult.NOT_APPLICABLE,
                hasContainerEvidence ? "workload container spec evidence is available"
                        : "현재 inventory snapshot에는 container requests/limits/probe 근거가 없습니다.",
                "Namespace AI Analysis 진단 요약에서 workload spec 근거를 추가 확인하세요.", now);
    }

    private void addContainerPolicyEvaluations(List<PolicyEvaluation> target,
                                               Map<String, PolicyDefinition> policies,
                                               Cluster cluster,
                                               KubernetesResourceSnapshot resource,
                                               JsonNode containers,
                                               Instant evaluatedAt) {
        boolean available = containers.isArray() && !containers.isEmpty();
        PolicyResult evidenceMissing = available ? PolicyResult.PASS : PolicyResult.NOT_APPLICABLE;
        boolean requestsMissing = available && anyContainer(containers, container ->
                !container.path("requests").hasNonNull("cpu") || !container.path("requests").hasNonNull("memory"));
        boolean limitsMissing = available && anyContainer(containers, container ->
                !container.path("limits").hasNonNull("cpu") || !container.path("limits").hasNonNull("memory"));
        boolean readinessMissing = available && anyContainer(containers,
                container -> !container.path("hasReadinessProbe").asBoolean(false));
        boolean livenessMissing = available && anyContainer(containers,
                container -> !container.path("hasLivenessProbe").asBoolean(false));
        boolean latestImage = available && anyContainer(containers, container ->
                container.path("image").asText("").toLowerCase(Locale.ROOT).endsWith(":latest"));
        addEvaluation(target, policies.get("REQUESTS_MISSING"), cluster, resource,
                available ? requestsMissing ? PolicyResult.WARN : PolicyResult.PASS : evidenceMissing,
                available ? requestsMissing ? "CPU 또는 memory request가 없는 container가 있습니다."
                        : "모든 container에 CPU/memory request가 있습니다." : "container spec evidence unavailable",
                "scheduler와 HPA가 사용할 수 있도록 실제 특성에 맞는 requests를 설정하세요.", evaluatedAt);
        addEvaluation(target, policies.get("LIMITS_MISSING"), cluster, resource,
                available ? limitsMissing ? PolicyResult.WARN : PolicyResult.PASS : evidenceMissing,
                available ? limitsMissing ? "CPU 또는 memory limit가 없는 container가 있습니다."
                        : "모든 container에 CPU/memory limit가 있습니다." : "container spec evidence unavailable",
                "OOM/CPU throttling 위험을 검토한 뒤 workload 특성에 맞는 limits를 설정하세요.", evaluatedAt);
        addEvaluation(target, policies.get("READINESS_PROBE_MISSING"), cluster, resource,
                available ? readinessMissing ? PolicyResult.WARN : PolicyResult.PASS : evidenceMissing,
                available ? "readiness probe missing=" + readinessMissing : "container spec evidence unavailable",
                "실제 listen port/path와 일치하는 readiness probe를 설정하세요.", evaluatedAt);
        addEvaluation(target, policies.get("LIVENESS_PROBE_MISSING"), cluster, resource,
                available ? livenessMissing ? PolicyResult.WARN : PolicyResult.PASS : evidenceMissing,
                available ? "liveness probe missing=" + livenessMissing : "container spec evidence unavailable",
                "초기화 시간을 고려해 startup/liveness probe를 함께 검토하세요.", evaluatedAt);
        addEvaluation(target, policies.get("LATEST_IMAGE"), cluster, resource,
                available ? latestImage ? PolicyResult.WARN : PolicyResult.PASS : evidenceMissing,
                available ? "latest image tag used=" + latestImage : "container spec evidence unavailable",
                "재현 가능한 version 또는 digest로 image를 고정하세요.", evaluatedAt);
    }

    private boolean anyContainer(JsonNode containers, java.util.function.Predicate<JsonNode> predicate) {
        for (JsonNode container : containers) {
            if (predicate.test(container)) {
                return true;
            }
        }
        return false;
    }

    private void addReferencePolicyEvaluation(List<PolicyEvaluation> target,
                                              Map<String, PolicyDefinition> policies,
                                              Cluster cluster,
                                              KubernetesResourceSnapshot resource,
                                              JsonNode volumes,
                                              Set<String> resourceNames,
                                              Instant evaluatedAt) {
        if (!volumes.isArray()) {
            addEvaluation(target, policies.get("REFERENCE_MISSING"), cluster, resource, PolicyResult.NOT_APPLICABLE,
                    "volume reference evidence unavailable", "workload manifest의 volume 참조를 확인하세요.", evaluatedAt);
            return;
        }
        List<String> references = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (JsonNode volume : volumes) {
            String kind = volume.path("sourceKind").asText();
            String name = volume.path("sourceName").asText();
            if (!REFERENCE_KINDS.contains(kind) || name.isBlank()) {
                continue;
            }
            references.add(kind + "/" + name);
            if (!resourceNames.contains(resourceKey(resource.namespace(), kind, name))) {
                missing.add(kind + "/" + name);
            }
        }
        PolicyResult policyResult = references.isEmpty() ? PolicyResult.NOT_APPLICABLE
                : missing.isEmpty() ? PolicyResult.PASS : PolicyResult.FAIL;
        String evidence = references.isEmpty() ? "no ConfigMap, Secret, or PVC volume reference"
                : missing.isEmpty() ? "all references found: " + String.join(", ", references)
                : "missing references: " + String.join(", ", missing);
        addEvaluation(target, policies.get("REFERENCE_MISSING"), cluster, resource, policyResult, evidence,
                "참조 이름과 namespace를 확인하고 누락 리소스를 복구하거나 workload 참조를 수정하세요.", evaluatedAt);
    }

    private PolicyResult servicePortPolicyResult(JsonNode service, List<KubernetesResourceSnapshot> workloads) {
        JsonNode selector = service.path("selector");
        JsonNode ports = service.path("ports");
        if (!selector.isObject() || selector.isEmpty() || !ports.isArray() || ports.isEmpty()) {
            return PolicyResult.NOT_APPLICABLE;
        }
        List<JsonNode> selectedContainers = workloads.stream()
                .map(workload -> readTree(workload.summaryJson()))
                .filter(summary -> selectorMatches(selector, summary.path("templateLabels")))
                .flatMap(summary -> {
                    List<JsonNode> values = new ArrayList<>();
                    summary.path("containers").forEach(values::add);
                    return values.stream();
                }).toList();
        boolean hasDeclaredPorts = selectedContainers.stream()
                .anyMatch(container -> container.path("ports").isArray() && !container.path("ports").isEmpty());
        if (selectedContainers.isEmpty() || !hasDeclaredPorts) {
            return PolicyResult.NOT_APPLICABLE;
        }
        for (JsonNode servicePort : ports) {
            String targetPort = servicePort.path("targetPort").asText();
            if (targetPort.isBlank()) {
                targetPort = servicePort.path("port").asText();
            }
            String expected = targetPort;
            boolean matched = selectedContainers.stream().anyMatch(container -> {
                for (JsonNode containerPort : container.path("ports")) {
                    if (expected.equals(containerPort.path("name").asText())
                            || expected.equals(containerPort.path("containerPort").asText())) {
                        return true;
                    }
                }
                return false;
            });
            if (!matched) {
                return PolicyResult.FAIL;
            }
        }
        return PolicyResult.PASS;
    }

    private String servicePortEvidence(PolicyResult result) {
        return switch (result) {
            case PASS -> "all Service targetPorts match declared container ports";
            case FAIL -> "one or more Service targetPorts do not match declared container ports";
            default -> "selector, selected workload, or declared containerPort evidence is unavailable";
        };
    }

    private boolean selectorMatches(JsonNode selector, JsonNode labels) {
        if (!selector.isObject() || selector.isEmpty() || !labels.isObject()) {
            return false;
        }
        var fields = selector.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (!entry.getValue().asText().equals(labels.path(entry.getKey()).asText(null))) {
                return false;
            }
        }
        return true;
    }

    private void addEvaluation(List<PolicyEvaluation> target, PolicyDefinition policy, Cluster cluster,
                               KubernetesResourceSnapshot resource, PolicyResult result, String evidence,
                               String recommendation, Instant evaluatedAt) {
        if (policy == null) {
            return;
        }
        target.add(new PolicyEvaluation(UUID.randomUUID(), policy.id(), cluster.id(), cluster.name(),
                resource.namespace(), resource.resourceType(), resource.resourceName(), result, evidence,
                recommendation, evaluatedAt));
    }

    private void addScopeEvaluation(List<PolicyEvaluation> target, PolicyDefinition policy, Cluster cluster,
                                    String namespace, PolicyResult result, String evidence, String recommendation,
                                    Instant evaluatedAt) {
        if (policy == null) {
            return;
        }
        target.add(new PolicyEvaluation(UUID.randomUUID(), policy.id(), cluster.id(), cluster.name(), namespace,
                "Namespace", namespace, result, evidence, recommendation, evaluatedAt));
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(defaultText(json, "{}"));
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private int firstInt(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && node.path(field).canConvertToInt()) {
                return node.path(field).asInt();
            }
        }
        return 0;
    }

    private String resourceKey(String namespace, String kind, String name) {
        return defaultText(namespace, "") + "|" + kind + "|" + name;
    }

    private String normalizeUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
