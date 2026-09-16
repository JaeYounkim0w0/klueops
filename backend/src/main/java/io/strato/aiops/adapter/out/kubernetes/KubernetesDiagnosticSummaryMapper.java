package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Quantity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
class KubernetesDiagnosticSummaryMapper {
    private final ObjectMapper objectMapper;

    /** KubernetesDiagnosticSummaryMapper 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    KubernetesDiagnosticSummaryMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** KubernetesDiagnosticSummaryMapper의 summary 처리에 필요한 업무 로직을 수행한다. */
    Map<String, Object> summary(Object... pairs) {
        Map<String, Object> value = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length - 1; index += 2) {
            value.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return value;
    }

    /** KubernetesDiagnosticSummaryMapper의 replicaSummary 처리에 필요한 업무 로직을 수행한다. */
    Map<String, Object> replicaSummary(Integer availableReplicas, Integer desiredReplicas, Map<String, Object> extra) {
        Map<String, Object> value = summary(
                "availableReplicas", availableReplicas == null ? 0 : availableReplicas,
                "desiredReplicas", desiredReplicas == null ? 0 : desiredReplicas);
        value.putAll(extra);
        return value;
    }

    /** KubernetesDiagnosticSummaryMapper의 nullSafeMap 처리에 필요한 업무 로직을 수행한다. */
    Map<String, String> nullSafeMap(Map<String, String> value) {
        return value == null ? Map.of() : value;
    }

    /** KubernetesDiagnosticSummaryMapper의 quantityMap 처리에 필요한 업무 로직을 수행한다. */
    Map<String, String> quantityMap(Map<String, Quantity> value) {
        if (value == null || value.isEmpty()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        value.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue().toString()));
        return result;
    }

    /** KubernetesDiagnosticSummaryMapper의 statusFromSummary 처리에 필요한 업무 로직을 수행한다. */
    String statusFromSummary(Map<String, Object> value) {
        Object phase = value.get("phase");
        if (phase != null && !String.valueOf(phase).isBlank()) return String.valueOf(phase);
        if (value.containsKey("availableReplicas") && value.containsKey("desiredReplicas")) {
            return value.get("availableReplicas") + "/" + value.get("desiredReplicas");
        }
        if (value.containsKey("type")) return String.valueOf(value.get("type"));
        return "ACTIVE";
    }

    /** KubernetesDiagnosticSummaryMapper의 writeJson 처리에 필요한 업무 로직을 수행한다. */
    String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Kubernetes diagnostics resource summary", exception);
        }
    }
}
