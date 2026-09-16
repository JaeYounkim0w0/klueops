package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.in.NamespaceDiagnosticsResult;
import org.springframework.stereotype.Component;

import java.util.List;

/** Builds the deterministic risk/timeline JSON contract independently of AI calls. */
@Component
public class DeterministicRiskTimelineSectionBuilder {

    private static final int MAX_ITEMS = 8;

    private final ObjectMapper objectMapper;

    /** DeterministicRiskTimelineSectionBuilder 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public DeterministicRiskTimelineSectionBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** DeterministicRiskTimelineSectionBuilder의 build 처리에 필요한 결과를 조합해 반환한다. */
    public ObjectNode build(NamespaceDiagnosticsResult.RiskForecast forecast,
                            List<NamespaceDiagnosticsResult.ChangeTimelineItem> timeline) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("_sectionName", "risk-timeline");
        result.put("_sectionSource", "DETERMINISTIC");

        NamespaceDiagnosticsResult.RiskForecast safeForecast = forecast == null
                ? new NamespaceDiagnosticsResult.RiskForecast(0, "UNKNOWN", "unknown", "", List.of())
                : forecast;
        ObjectNode riskForecast = result.putObject("riskForecast");
        riskForecast.put("summary", valueOrBlank(safeForecast.summary()));
        riskForecast.put("overallRisk", safeForecast.overallRisk());
        riskForecast.put("riskLevel", valueOrBlank(safeForecast.riskLevel()));
        riskForecast.put("horizon", valueOrBlank(safeForecast.horizon()));
        ArrayNode predictions = riskForecast.putArray("predictions");
        safeList(safeForecast.predictions()).stream().limit(MAX_ITEMS).forEach(prediction -> {
            ObjectNode item = predictions.addObject();
            item.put("category", valueOrBlank(prediction.category()));
            item.put("severity", valueOrBlank(prediction.severity()));
            item.put("probability", prediction.probability());
            item.put("horizon", valueOrBlank(prediction.horizon()));
            item.put("resourceKind", valueOrBlank(prediction.resourceKind()));
            item.put("resourceName", valueOrBlank(prediction.resourceName()));
            item.put("signal", valueOrBlank(prediction.signal()));
            item.put("impact", valueOrBlank(prediction.impact()));
            item.put("recommendation", valueOrBlank(prediction.recommendation()));
            item.putArray("evidence").add(valueOrBlank(prediction.evidence()));
            item.put("verificationCommand", valueOrBlank(prediction.verificationCommand()));
        });

        ArrayNode changeTimeline = result.putArray("changeTimeline");
        safeList(timeline).stream().limit(MAX_ITEMS).forEach(item -> {
            ObjectNode node = changeTimeline.addObject();
            node.put("occurredAt", item.occurredAt() == null ? "" : item.occurredAt().toString());
            node.put("severity", valueOrBlank(item.severity()));
            node.put("category", valueOrBlank(item.category()));
            node.put("resourceKind", valueOrBlank(item.resourceKind()));
            node.put("resourceName", valueOrBlank(item.resourceName()));
            node.put("title", valueOrBlank(item.title()));
            node.put("detail", valueOrBlank(item.detail()));
            node.put("suspectedChange", valueOrBlank(item.suspectedChange()));
            node.put("recommendation", valueOrBlank(item.recommendation()));
        });
        return result;
    }

    /** DeterministicRiskTimelineSectionBuilder의 safeList 처리에 필요한 업무 로직을 수행한다. */
    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).toList();
    }

    /** DeterministicRiskTimelineSectionBuilder의 valueOrBlank 처리에 필요한 업무 로직을 수행한다. */
    private static String valueOrBlank(String value) {
        return value == null ? "" : value;
    }
}
