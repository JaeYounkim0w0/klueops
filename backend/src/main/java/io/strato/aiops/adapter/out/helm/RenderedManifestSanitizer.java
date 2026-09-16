package io.strato.aiops.adapter.out.helm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.strato.aiops.application.port.out.RenderedManifestSanitizationPort;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class RenderedManifestSanitizer implements RenderedManifestSanitizationPort {
    private static final String REDACTED = "***REDACTED***";
    private final ObjectMapper yaml = new ObjectMapper(YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build());

    /** RenderedManifestSanitizer의 sanitize 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public String sanitize(String manifest) {
        try {
            List<String> documents = new ArrayList<>();
            var parser = yaml.getFactory().createParser(manifest);
            var values = yaml.readValues(parser, JsonNode.class);
            while (values.hasNext()) {
                JsonNode document = values.next();
                redact(document, true);
                documents.add(yaml.writeValueAsString(document).trim());
            }
            return String.join("\n---\n", documents);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Rendered manifest cannot be sanitized", exception);
        }
    }

    // Preview가 Secret 원문과 환경 변수 값을 브라우저·DB에 노출하지 않도록 재귀적으로 제거한다.
    private void redact(JsonNode node, boolean documentRoot) {
        if (node instanceof ObjectNode object) {
            if (documentRoot && "Secret".equalsIgnoreCase(object.path("kind").asText())) {
                redactMap(object, "data");
                redactMap(object, "stringData");
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

    /** RenderedManifestSanitizer의 redactMap 처리에 필요한 업무 로직을 수행한다. */
    private void redactMap(ObjectNode object, String fieldName) {
        if (!(object.get(fieldName) instanceof ObjectNode values)) return;
        values.fieldNames().forEachRemaining(key -> values.put(key, REDACTED));
    }
}
