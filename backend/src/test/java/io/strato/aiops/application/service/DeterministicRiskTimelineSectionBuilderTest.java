package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.in.NamespaceDiagnosticsResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicRiskTimelineSectionBuilderTest {

    private final DeterministicRiskTimelineSectionBuilder builder =
            new DeterministicRiskTimelineSectionBuilder(new ObjectMapper());

    /** DeterministicRiskTimelineSectionBuilderTest의 buildsStableRiskAndTimelineContract 처리에 필요한 결과를 조합해 반환한다. */
    @Test
    void buildsStableRiskAndTimelineContract() {
        NamespaceDiagnosticsResult.RiskForecast forecast = new NamespaceDiagnosticsResult.RiskForecast(
                82, "HIGH", "24h", "A deployment is likely to remain unavailable.", List.of(
                new NamespaceDiagnosticsResult.RiskPrediction(
                        "availability", "HIGH", 90, "24h", "Deployment", "web",
                        "unavailable replicas", "traffic may fail", "restore replicas", "3 unavailable pods", "kubectl get deploy web")));
        NamespaceDiagnosticsResult.ChangeTimelineItem change = new NamespaceDiagnosticsResult.ChangeTimelineItem(
                Instant.parse("2026-09-07T00:00:00Z"), "WARNING", "availability", "Pod", "web-0",
                "Pod became unavailable", "container is waiting", "image pull failure", "check image credentials");

        ObjectNode result = builder.build(forecast, List.of(change));

        assertThat(result.path("_sectionName").asText()).isEqualTo("risk-timeline");
        assertThat(result.path("_sectionSource").asText()).isEqualTo("DETERMINISTIC");
        assertThat(result.path("riskForecast").path("overallRisk").asInt()).isEqualTo(82);
        assertThat(result.path("riskForecast").path("predictions").get(0).path("evidence").get(0).asText())
                .isEqualTo("3 unavailable pods");
        assertThat(result.path("changeTimeline").get(0).path("occurredAt").asText())
                .isEqualTo("2026-09-07T00:00:00Z");
    }

    /** DeterministicRiskTimelineSectionBuilderTest의 limitsLargeInputsAndNormalizesNullableValues 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void limitsLargeInputsAndNormalizesNullableValues() {
        NamespaceDiagnosticsResult.RiskForecast forecast = new NamespaceDiagnosticsResult.RiskForecast(
                0, null, null, null, List.of(
                new NamespaceDiagnosticsResult.RiskPrediction(null, null, 0, null, null, null,
                        null, null, null, null, null)));
        List<NamespaceDiagnosticsResult.ChangeTimelineItem> changes = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new NamespaceDiagnosticsResult.ChangeTimelineItem(
                        null, null, null, null, null, null, null, null, null))
                .toList();

        ObjectNode result = builder.build(forecast, changes);

        assertThat(result.path("riskForecast").path("riskLevel").asText()).isEmpty();
        assertThat(result.path("riskForecast").path("predictions")).hasSize(1);
        assertThat(result.path("changeTimeline")).hasSize(8);
        assertThat(result.path("changeTimeline").get(0).path("occurredAt").asText()).isEmpty();
    }
}
