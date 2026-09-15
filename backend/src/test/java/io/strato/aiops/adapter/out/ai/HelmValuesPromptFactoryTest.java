package io.strato.aiops.adapter.out.ai;

import io.strato.aiops.application.port.out.HelmValuesSuggestionPort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelmValuesPromptFactoryTest {

    @Test
    void includesExactChartContractAndRetryFeedback() {
        var request = new HelmValuesSuggestionPort.SuggestionRequest("helm-values.v2", "nginx", "nginx",
                "cloudpirates-nginx", "ARTIFACT_HUB", "0.16.8", "1.31.5", "service: {}",
                "service:\n  ports: []", "{\"type\":\"object\"}", "Service를 ClusterIP로 설정",
                "targetPort must be string");

        HelmValuesPromptFactory.Prompt prompt = HelmValuesPromptFactory.create(request);

        assertThat(prompt.system()).contains("helm-values.v2", "exact DEFAULT VALUES SKELETON",
                "Preserve exact YAML value types", "untrusted data");
        assertThat(prompt.user()).contains("providerName: cloudpirates-nginx", "chartVersion: 0.16.8",
                "applicationVersion: 1.31.5", "targetPort must be string", "Service를 ClusterIP로 설정");
    }
}
