package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Component;


@Component
class AiManifestSanitizer {

    private static final String REDACTED = "***REDACTED***";
    private final ObjectMapper objectMapper;

    /** AiManifestSanitizer 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    AiManifestSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** AiManifestSanitizer의 sanitize 처리에 필요한 업무 로직을 수행한다. */
    String sanitize(Object manifest) {
        JsonNode root = objectMapper.valueToTree(manifest);
        redact(root, true);
        Object safeManifest = objectMapper.convertValue(root, Object.class);
        return Serialization.asYaml(safeManifest);
    }

    /** AiManifestSanitizer의 sanitize 처리에 필요한 업무 로직을 수행한다. */
    String sanitize(String jsonManifest) {
        try {
            JsonNode root = objectMapper.readTree(jsonManifest);
            redact(root, true);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Manifest cannot be sanitized", exception);
        }
    }

    /** AiManifestSanitizer의 redact 처리에 필요한 업무 로직을 수행한다. */
    private void redact(JsonNode node, boolean root) {
        if (node instanceof ObjectNode object) {
            if (object.has("annotations")) {
                object.set("annotations", objectMapper.createObjectNode());
            }
            if (root && isSensitiveDataKind(object.path("kind").asText())) {
                redactMapValues(object, "data");
                redactMapValues(object, "binaryData");
                redactMapValues(object, "stringData");
            }
            JsonNode environment = object.get("env");
            if (environment instanceof ArrayNode entries) {
                entries.forEach(entry -> {
                    if (entry instanceof ObjectNode item && item.has("value")) item.put("value", REDACTED);
                });
            }
            object.properties().forEach(field -> redact(field.getValue(), false));
        } else if (node instanceof ArrayNode array) {
            array.forEach(item -> redact(item, false));
        }
    }

    /** AiManifestSanitizer의 redactMapValues 처리에 필요한 업무 로직을 수행한다. */
    private void redactMapValues(ObjectNode object, String fieldName) {
        JsonNode data = object.get(fieldName);
        if (!(data instanceof ObjectNode values)) return;
        values.fieldNames().forEachRemaining(key -> values.put(key, REDACTED));
    }

    /** AiManifestSanitizer의 isSensitiveDataKind 처리 조건의 충족 여부를 판단한다. */
    private boolean isSensitiveDataKind(String kind) {
        return "ConfigMap".equalsIgnoreCase(kind) || "Secret".equalsIgnoreCase(kind);
    }
}
