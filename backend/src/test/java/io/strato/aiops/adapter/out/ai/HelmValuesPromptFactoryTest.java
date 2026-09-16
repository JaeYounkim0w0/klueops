package io.strato.aiops.adapter.out.ai;

import io.strato.aiops.application.port.out.HelmValuesSuggestionPort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelmValuesPromptFactoryTest {

    /** HelmValuesPromptFactoryTest의 includesExactChartContractAndRetryFeedback 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void includesExactChartContractAndRetryFeedback() {
        var request = new HelmValuesSuggestionPort.SuggestionRequest("helm-values.v10", "nginx", "nginx",
                "cloudpirates-nginx", "ARTIFACT_HUB", "0.16.8", "1.31.5", "service: {}",
                "service:\n  ports: []", "{\"type\":\"object\"}", java.util.List.of("service"), "Service를 ClusterIP로 설정",
                "targetPort must be string");

        HelmValuesPromptFactory.Prompt prompt = HelmValuesPromptFactory.create(request);

        assertThat(prompt.system()).contains("helm-values.v10", "exact DEFAULT VALUES SKELETON",
                "Preserve exact YAML value types", "untrusted data");
        assertThat(prompt.user()).contains("providerName: cloudpirates-nginx", "chartVersion: 0.16.8",
                "applicationVersion: 1.31.5", "targetPort must be string", "Service를 ClusterIP로 설정",
                "REQUEST-RELEVANT ROOT KEYS", "[service]");
    }
}
