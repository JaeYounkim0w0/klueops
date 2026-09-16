package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.NamespaceDiagnosticsResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NamespaceDiagnosticsResponse(
        UUID clusterId,
        String namespace,
        Instant collectedAt,
        int resourceCount,
        int eventCount,
        int warningEventCount,
        int podLogCount,
        NamespaceDiagnosticsResult.HealthScore healthScore,
        NamespaceDiagnosticsResult.RiskForecast riskForecast,
        List<NamespaceDiagnosticsResult.ChangeTimelineItem> changeTimeline,
        List<NamespaceDiagnosticsResult.RunbookAction> runbookActions,
        List<NamespaceDiagnosticsResult.ResourceKindCount> resourceKinds,
        List<NamespaceDiagnosticsResult.ResourceSignal> problemResources,
        List<NamespaceDiagnosticsResult.EventSignal> warningEvents,
        List<NamespaceDiagnosticsResult.EvidenceSignal> evidenceSignals,
        List<NamespaceDiagnosticsResult.PodLogSignal> podLogSources
) {
    /** NamespaceDiagnosticsResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static NamespaceDiagnosticsResponse from(NamespaceDiagnosticsResult result) {
        return new NamespaceDiagnosticsResponse(
                result.clusterId(),
                result.namespace(),
                result.collectedAt(),
                result.resourceCount(),
                result.eventCount(),
                result.warningEventCount(),
                result.podLogCount(),
                result.healthScore(),
                result.riskForecast(),
                result.changeTimeline(),
                result.runbookActions(),
                result.resourceKinds(),
                result.problemResources(),
                result.warningEvents(),
                result.evidenceSignals(),
                result.podLogSources()
        );
    }
}
