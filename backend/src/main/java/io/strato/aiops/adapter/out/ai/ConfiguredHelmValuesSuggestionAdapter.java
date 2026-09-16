package io.strato.aiops.adapter.out.ai;

import io.strato.aiops.application.port.out.HelmValuesSuggestionPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ConfiguredHelmValuesSuggestionAdapter implements HelmValuesSuggestionPort {
    private final ConfiguredAiClient client;

    /** ConfiguredHelmValuesSuggestionAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ConfiguredHelmValuesSuggestionAdapter(ConfiguredAiClient client) {
        this.client = client;
    }

    /** ConfiguredHelmValuesSuggestionAdapter의 suggest 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public String suggest(UUID tenantId, SuggestionRequest request) {
        HelmValuesPromptFactory.Prompt prompt = HelmValuesPromptFactory.create(request);
        return client.complete(tenantId, "HELM_VALUES", prompt.system(), prompt.user())
                .map(ConfiguredAiClient.Completion::content)
                .orElseThrow(() -> new IllegalStateException("HELM_VALUES AI routing is not configured for this tenant"));
    }
}
