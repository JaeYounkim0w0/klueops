package io.strato.aiops.adapter.out.ai;

import io.strato.aiops.application.port.out.HelmValuesSuggestionPort.SuggestionRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HelmValuesPromptFactoryTest {
    /** 제공사와 Chart가 달라도 시스템 규칙은 동일하며 근거만 런타임에 바뀐다. */
    @Test void systemPromptIsIndependentOfChart() {
        var first = HelmValuesPromptFactory.create(request("sample-a", "vendor-a"));
        var second = HelmValuesPromptFactory.create(request("sample-b", "vendor-b"));
        assertThat(first.system()).isEqualTo(second.system()).doesNotContain("sample-a", "vendor-a", "NodePort", "prometheus");
        assertThat(first.user()).contains("sample-a", "vendor-a", "# original comment", "현재 값", "요청");
        assertThat(second.user()).contains("sample-b", "vendor-b");
    }

    /** 공통 생성 요청에 정확한 Chart와 원문 Values를 전달한다. */
    private SuggestionRequest request(String chart, String provider) {
        return new SuggestionRequest("helm-values.grounded.v2", chart, chart, provider, "UPLOAD", "1.2", "3.4",
                "현재 값", "ref0 values.yaml", "# original comment\nreplicas: 1", "", java.util.List.of(), "요청", null);
    }
}
