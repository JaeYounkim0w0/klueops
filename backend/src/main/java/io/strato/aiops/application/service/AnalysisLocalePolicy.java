package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.domain.analysis.SupportedLocale;
import org.springframework.stereotype.Component;

@Component
public class AnalysisLocalePolicy {
    private final ObjectMapper objectMapper;

    public AnalysisLocalePolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String instruction(SupportedLocale locale) {
        return "Write every natural-language JSON value in " + locale.responseLanguage()
                + ". Preserve Kubernetes resource names, namespaces, labels, logs, event messages, YAML, JSON keys, "
                + "commands, IDs, enum codes, model names, and provider names exactly as supplied.";
    }

    public String attachLocale(String resultJson, SupportedLocale locale) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(resultJson);
            root.put("locale", locale.tag());
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to attach analysis locale", exception);
        }
    }
}
