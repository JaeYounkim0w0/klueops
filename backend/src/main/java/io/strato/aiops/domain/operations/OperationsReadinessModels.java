package io.strato.aiops.domain.operations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OperationsReadinessModels {

    /** OperationsReadinessModels 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private OperationsReadinessModels() {
    }

    public record FleetQueue(
            Instant generatedAt,
            int totalItems,
            int immediateItems,
            int degradedCollectors,
            List<FleetQueueItem> items
    ) {
    }

    public record FleetQueueItem(
            String id,
            String sourceType,
            UUID clusterId,
            String clusterName,
            String namespace,
            String severity,
            int score,
            String title,
            String summary,
            String nextAction,
            String targetPath,
            Instant detectedAt
    ) {
    }

    public record ShiftBriefing(
            Instant generatedAt,
            String posture,
            String beginnerSummary,
            String expertSummary,
            int openIncidents,
            int criticalIncidents,
            int runningJobs,
            int failedJobs,
            int degradedCollectors,
            List<String> immediateActions,
            List<String> watchItems
    ) {
    }

    public record ValidationScenario(
            String id,
            String title,
            String category,
            String signal,
            String expectedRootCause,
            String resourceKind,
            String safetyMode
    ) {
    }

    public record ValidationLabRun(
            UUID runId,
            String status,
            double score,
            int passedCases,
            int totalCases,
            String mode,
            Instant completedAt,
            List<ValidationCase> cases
    ) {
    }

    public record ValidationCase(
            String scenarioId,
            String title,
            String category,
            String status,
            int score,
            List<String> assertions,
            List<String> failures,
            long durationMs
    ) {
    }

    public record LiveValidationPolicy(
            boolean enabled,
            String safetyMode,
            String namespacePrefix,
            int maximumTtlSeconds,
            String requiredConfirmation,
            List<String> safeguards
    ) {
    }

    public record LiveValidationPreview(
            UUID clusterId,
            String clusterName,
            String scenarioId,
            String namespace,
            int ttlSeconds,
            boolean executable,
            List<String> passedChecks,
            List<String> blockingReasons,
            List<String> plannedResources,
            Instant expiresAt
    ) {
    }

    public record LiveValidationRun(
            UUID id,
            UUID clusterId,
            String clusterName,
            String scenarioId,
            String namespace,
            String state,
            String safetyMode,
            int ttlSeconds,
            List<String> safetyChecks,
            List<String> resources,
            String observedSignal,
            String detail,
            boolean cleanupRequired,
            Instant startedAt,
            Instant expiresAt,
            Instant completedAt,
            String triggeredBy
    ) {
    }

    public record AnalysisBenchmark(
            UUID runId,
            String state,
            double overallScore,
            double classificationAccuracy,
            double evidenceCoverage,
            double commandSafetyRate,
            double falseAssertionRate,
            long p95LatencyMs,
            int passedCases,
            int totalCases,
            String baselineVersion,
            String releaseRecommendation,
            List<String> blockingReasons,
            Instant completedAt
    ) {
    }

    public record RemediationRecommendation(
            String category,
            String resourceKind,
            String action,
            int sampleCount,
            int succeededCount,
            int failedCount,
            double successRate,
            long averageObservationSeconds,
            String confidence,
            String explanation
    ) {
    }

    public record RemediationLearning(
            UUID incidentId,
            String category,
            String resourceKind,
            int comparableSamples,
            String evidenceNotice,
            List<RemediationRecommendation> recommendations
    ) {
    }

    public record ReliabilityTrend(
            Instant generatedAt,
            int windowDays,
            UUID clusterId,
            String namespace,
            String evidenceType,
            int incidentsDetected,
            int incidentsResolved,
            int recurringIncidents,
            double recurrenceRate,
            long meanTimeToAcknowledgeMinutes,
            long meanTimeToResolveMinutes,
            double remediationSuccessRate,
            double collectorCoverageRate,
            List<ReliabilityDay> daily,
            List<ReliabilityScope> scopes
    ) {
    }

    public record ReliabilityDay(
            String date,
            int detected,
            int resolved,
            int recurred
    ) {
    }

    public record ReliabilityScope(
            UUID clusterId,
            String clusterName,
            String namespace,
            int detected,
            int open,
            int resolved,
            int recurred
    ) {
    }

    public record OutcomeAggregate(
            String category,
            String resourceKind,
            String action,
            int samples,
            int succeeded,
            int failed,
            int inconclusive,
            long averageObservationSeconds
    ) {
    }

    public record ReliabilityTrendData(
            int incidentsDetected,
            int incidentsResolved,
            int recurringIncidents,
            long meanTimeToAcknowledgeMinutes,
            long meanTimeToResolveMinutes,
            int remediationSamples,
            int remediationSucceeded,
            List<ReliabilityDay> daily,
            List<ReliabilityScope> scopes
    ) {
    }
}
