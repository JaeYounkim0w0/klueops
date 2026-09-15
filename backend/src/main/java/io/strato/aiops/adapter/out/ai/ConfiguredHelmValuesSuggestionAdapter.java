package io.strato.aiops.adapter.out.ai;

import io.strato.aiops.application.port.out.HelmValuesSuggestionPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ConfiguredHelmValuesSuggestionAdapter implements HelmValuesSuggestionPort {
    private final ConfiguredAiClient client;

    public ConfiguredHelmValuesSuggestionAdapter(ConfiguredAiClient client) {
        this.client = client;
    }

    @Override
    public String suggest(UUID tenantId, String currentValuesYaml, String instruction) {
        String system = """
                You are the KlueOps Helm Values assistant.
                Return only a complete YAML mapping without markdown fences or commentary.
                Modify only custom values; never generate or edit Helm templates.
                Preserve unrelated keys and use conservative Kubernetes defaults.
                Values marked ***REDACTED*** are secrets: preserve the marker and never invent a value.
                """;
        String user = "Operator request:\n" + instruction + "\n\nCurrent custom values:\n" + currentValuesYaml;
        return client.complete(tenantId, "HELM_VALUES", system, user)
                .map(ConfiguredAiClient.Completion::content)
                .orElseThrow(() -> new IllegalStateException("HELM_VALUES AI routing is not configured for this tenant"));
    }
}
