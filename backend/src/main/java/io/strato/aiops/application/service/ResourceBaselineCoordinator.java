package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.operations.OperationsModels.ResourceBaseline;
import io.strato.aiops.domain.operations.OperationsModels.ResourceChange;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ResourceBaselineCoordinator {

    private static final int DELETE_DETECTION_RESOURCE_LIMIT = 500;

    private final OperationsRepositoryPort operationsRepository;
    private final ObjectMapper objectMapper;

    /** ResourceBaselineCoordinator 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ResourceBaselineCoordinator(OperationsRepositoryPort operationsRepository, ObjectMapper objectMapper) {
        this.operationsRepository = operationsRepository;
        this.objectMapper = objectMapper;
    }

    /** ResourceBaselineCoordinator의 reconcile 처리에 필요한 업무 로직을 수행한다. */
    public void reconcile(Cluster cluster, List<KubernetesResourceSnapshot> resources) {
        BaselinePlan plan = plan(cluster, resources,
                operationsRepository.findResourceBaselines(cluster.id()), Instant.now());
        plan.baselines().forEach(operationsRepository::saveResourceBaseline);
        plan.changes().forEach(operationsRepository::saveResourceChange);
        plan.deletedBaselineIds().forEach(operationsRepository::deleteResourceBaseline);
    }

    /** ResourceBaselineCoordinator의 plan 처리에 필요한 업무 로직을 수행한다. */
    BaselinePlan plan(Cluster cluster,
                      List<KubernetesResourceSnapshot> resources,
                      List<ResourceBaseline> existingBaselines,
                      Instant detectedAt) {
        Map<String, ResourceBaseline> existing = existingBaselines.stream()
                .collect(Collectors.toMap(item -> resourceKey(item.namespace(), item.resourceKind(), item.resourceName()),
                        Function.identity()));
        boolean initialBaseline = existing.isEmpty();
        Set<String> seen = new HashSet<>();
        List<ResourceBaseline> baselines = new ArrayList<>();
        List<ResourceChange> changes = new ArrayList<>();
        List<UUID> deletedBaselineIds = new ArrayList<>();

        for (KubernetesResourceSnapshot resource : resources) {
            String key = resourceKey(resource.namespace(), resource.resourceType(), resource.resourceName());
            seen.add(key);
            ResourceBaseline previous = existing.get(key);
            String canonical = canonicalJson(resource.summaryJson());
            String currentHash = hash(defaultText(resource.status(), "") + "|" + canonical);
            ResourceBaseline current = new ResourceBaseline(previous == null ? UUID.randomUUID() : previous.id(),
                    cluster.id(), resource.namespace(), resource.resourceType(), resource.resourceName(), resource.status(),
                    currentHash, cleanText(canonical, 8000), resource.collectedAt());
            baselines.add(current);
            if (!initialBaseline && previous == null) {
                changes.add(change(cluster, current, null, "CREATED",
                        "새 리소스가 동기화에서 감지되었습니다.", detectedAt));
            } else if (previous != null && !previous.summaryHash().equals(currentHash)) {
                String type = Objects.equals(previous.summaryJson(), current.summaryJson())
                        ? "STATUS_CHANGED" : "UPDATED";
                changes.add(change(cluster, current, previous, type,
                        safeChangeSummary(previous, current), detectedAt));
            }
        }

        if (!initialBaseline && resources.size() < DELETE_DETECTION_RESOURCE_LIMIT) {
            for (Map.Entry<String, ResourceBaseline> entry : existing.entrySet()) {
                if (seen.contains(entry.getKey())) {
                    continue;
                }
                ResourceBaseline previous = entry.getValue();
                changes.add(change(cluster, null, previous, "DELETED",
                        "리소스가 최신 inventory에서 더 이상 확인되지 않습니다.", detectedAt));
                deletedBaselineIds.add(previous.id());
            }
        }
        return new BaselinePlan(List.copyOf(baselines), List.copyOf(changes), List.copyOf(deletedBaselineIds));
    }

    /** ResourceBaselineCoordinator의 change 처리 대상의 상태를 갱신한다. */
    private ResourceChange change(Cluster cluster,
                                  ResourceBaseline current,
                                  ResourceBaseline previous,
                                  String type,
                                  String summary,
                                  Instant detectedAt) {
        ResourceBaseline source = current == null ? previous : current;
        return new ResourceChange(UUID.randomUUID(), cluster.id(), cluster.name(), source.namespace(),
                source.resourceKind(), source.resourceName(), type,
                previous == null ? null : previous.status(), current == null ? null : current.status(),
                previous == null ? null : previous.summaryHash(), current == null ? null : current.summaryHash(),
                cleanText(summary, 2000), detectedAt);
    }

    /** ResourceBaselineCoordinator의 safeChangeSummary 처리에 필요한 업무 로직을 수행한다. */
    private String safeChangeSummary(ResourceBaseline previous, ResourceBaseline current) {
        if (!Objects.equals(previous.status(), current.status())) {
            return "status: " + defaultText(previous.status(), "-") + " → " + defaultText(current.status(), "-");
        }
        return "안전 summary 필드가 변경되었습니다. 상세 manifest는 Kubernetes API에서 실시간 확인하세요.";
    }

    /** ResourceBaselineCoordinator의 canonicalJson 처리 조건의 충족 여부를 판단한다. */
    private String canonicalJson(String value) {
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(defaultText(value, "{}")));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    /** ResourceBaselineCoordinator의 resourceKey 처리에 필요한 업무 로직을 수행한다. */
    private String resourceKey(String namespace, String kind, String name) {
        return defaultText(namespace, "") + "|" + kind + "|" + name;
    }

    /** ResourceBaselineCoordinator의 hash 처리 조건의 충족 여부를 판단한다. */
    private String hash(String value) {
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

    /** ResourceBaselineCoordinator의 cleanText 처리에 필요한 업무 로직을 수행한다. */
    private String cleanText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?i)(token|password|secret)\\s*[=:]\\s*[^\\s,]+", "$1=***");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    /** ResourceBaselineCoordinator의 defaultText 처리에 필요한 업무 로직을 수행한다. */
    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    record BaselinePlan(List<ResourceBaseline> baselines,
                        List<ResourceChange> changes,
                        List<UUID> deletedBaselineIds) {
    }
}
