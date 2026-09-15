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
    public String suggest(UUID tenantId, SuggestionRequest request) {
        HelmValuesPromptFactory.Prompt prompt = HelmValuesPromptFactory.create(request);
        return client.complete(tenantId, "HELM_VALUES", prompt.system(), prompt.user())
                .map(ConfiguredAiClient.Completion::content)
                .orElseThrow(() -> new IllegalStateException("HELM_VALUES AI routing is not configured for this tenant"));
    }
}
