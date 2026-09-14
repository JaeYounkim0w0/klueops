package io.strato.aiops.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NamespaceDiagnosticsResult(
        UUID clusterId,
        String namespace,
        Instant collectedAt,
        int resourceCount,
        int eventCount,
        int warningEventCount,
        int podLogCount,
        HealthScore healthScore,
        RiskForecast riskForecast,
        List<ChangeTimelineItem> changeTimeline,
        List<RunbookAction> runbookActions,
        List<ResourceKindCount> resourceKinds,
        List<ResourceSignal> problemResources,
        List<EventSignal> warningEvents,
        List<EvidenceSignal> evidenceSignals,
        List<PodLogSignal> podLogSources
) {
    public record HealthScore(int availability, int stability, int performance, int security, int operability, int overall) {
    }

    public record RiskForecast(int overallRisk, String riskLevel, String horizon, String summary, List<RiskPrediction> predictions) {
    }

    public record RiskPrediction(
            String category,
            String severity,
            int probability,
            String horizon,
            String resourceKind,
            String resourceName,
            String signal,
            String impact,
            String recommendation,
            String evidence,
            String verificationCommand
    ) {
    }

    public record ChangeTimelineItem(
            Instant occurredAt,
            String severity,
            String category,
            String resourceKind,
            String resourceName,
            String title,
            String detail,
            String suspectedChange,
            String recommendation
    ) {
    }

    public record RunbookAction(
            String priority,
            String title,
            String targetKind,
            String targetName,
            String reason,
            String command,
            boolean destructive
    ) {
    }

    public record ResourceKindCount(String resourceType, int count) {
    }

    public record ResourceSignal(String resourceType, String resourceName, String status, String summaryJson) {
    }

    public record EventSignal(String type, String reason, String involvedKind, String involvedName, String message, Integer count, Instant eventTime) {
    }

    public record EvidenceSignal(String severity, String source, String message) {
    }

    public record PodLogSignal(String podName, String containerName, boolean truncated) {
    }
}
