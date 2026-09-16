package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.domain.analysis.SupportedLocale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisLocalePolicyTest {

    private final AnalysisLocalePolicy policy = new AnalysisLocalePolicy(new ObjectMapper());

    /** AnalysisLocalePolicyTest의 attachesRequestedLocaleWithoutChangingAnalysisPayload 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void attachesRequestedLocaleWithoutChangingAnalysisPayload() {
        String localized = policy.attachLocale("{\"schemaVersion\":\"analysis-result.v1\",\"summary\":\"ok\"}",
                SupportedLocale.KOREAN);

        assertThat(localized).contains("\"locale\":\"ko-KR\"")
                .contains("\"schemaVersion\":\"analysis-result.v1\"")
                .contains("\"summary\":\"ok\"");
    }

    /** AnalysisLocalePolicyTest의 keepsKubernetesEvidenceAndCommandsInTheirOriginalLanguage 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void keepsKubernetesEvidenceAndCommandsInTheirOriginalLanguage() {
        assertThat(policy.instruction(SupportedLocale.ENGLISH))
                .contains("English")
                .contains("Preserve Kubernetes resource names")
                .contains("commands");
    }
}
