package io.strato.aiops.adapter.out.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaRequestBudgetTest {

    /** 짧은 요청은 기본 컨텍스트를 사용하고 설정한 출력 상한을 그대로 유지하는지 검증한다. */
    @Test
    void keepsMinimumContextForShortRequest() {
        var budget = OllamaRequestBudget.calculate("system", "replicaCount: 2", 512, 16_384);

        assertThat(budget.contextTokens()).isEqualTo(4_096);
        assertThat(budget.outputTokens()).isEqualTo(512);
    }

    /** Prometheus처럼 큰 Chart 계약은 4K에서 잘리지 않도록 컨텍스트를 동적으로 확장한다. */
    @Test
    void expandsContextForLargeChartContract() {
        String largePrompt = "프로메테우스 values 계약\n".repeat(1_400);

        var budget = OllamaRequestBudget.calculate("system", largePrompt, 4_096, 16_384);

        assertThat(budget.contextTokens()).isGreaterThan(4_096).isLessThanOrEqualTo(16_384);
        assertThat(budget.outputTokens()).isBetween(256, 4_096);
        assertThat(budget.estimatedInputTokens()).isGreaterThan(4_096);
    }

    /** 입력이 context보다 크면 조용히 잘리지 않도록 호출 전에 거부한다. */
    @Test
    void boundsOutputWhenInputReachesMaximumContext() {
        String oversized = "values-path: object\n".repeat(4_000);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> OllamaRequestBudget.calculate("system", oversized, 4_096, 8_192))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("context budget");
    }
}
