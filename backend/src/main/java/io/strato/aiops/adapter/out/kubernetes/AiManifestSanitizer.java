package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

@Component
class AiManifestSanitizer {

    private static final String REDACTED = "***REDACTED***";
    private final ObjectMapper objectMapper;

    AiManifestSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String sanitize(Object manifest) {
        JsonNode root = objectMapper.valueToTree(manifest);
        redact(root, true);
        Object safeManifest = objectMapper.convertValue(root, Object.class);
        return Serialization.asYaml(safeManifest);
    }

    String sanitize(String jsonManifest) {
        try {
            JsonNode root = objectMapper.readTree(jsonManifest);
            redact(root, true);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Manifest cannot be sanitized", exception);
        }
    }

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
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) redact(fields.next().getValue(), false);
        } else if (node instanceof ArrayNode array) {
            array.forEach(item -> redact(item, false));
        }
    }

    private void redactMapValues(ObjectNode object, String fieldName) {
        JsonNode data = object.get(fieldName);
        if (!(data instanceof ObjectNode values)) return;
        values.fieldNames().forEachRemaining(key -> values.put(key, REDACTED));
    }

    private boolean isSensitiveDataKind(String kind) {
        return "ConfigMap".equalsIgnoreCase(kind) || "Secret".equalsIgnoreCase(kind);
    }
}
