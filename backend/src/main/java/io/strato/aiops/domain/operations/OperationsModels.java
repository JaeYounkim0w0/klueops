package io.strato.aiops.domain.operations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OperationsModels {

    /** OperationsModels 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private OperationsModels() {
    }

    public enum IncidentState {
        OPEN,
        ACKNOWLEDGED,
        INVESTIGATING,
        MITIGATING,
        MONITORING,
        RESOLVED,
        REOPENED
    }

    public enum PolicyResult {
        PASS,
        WARN,
        FAIL,
        NOT_APPLICABLE
    }

    public record Incident(
            UUID id,
            String fingerprint,
            UUID clusterId,
            String clusterName,
            String namespace,
            String resourceKind,
            String resourceName,
            String category,
            String severity,
            IncidentState state,
            String title,
            String summary,
            String nextAction,
            int occurrenceCount,
            int reopenCount,
            UUID sourceAnalysisId,
            Instant firstDetectedAt,
            Instant lastDetectedAt,
            String updatedBy
    ) {
    }

    public record IncidentEvidence(
            UUID id,
            UUID incidentId,
            String evidenceKey,
            String evidenceType,
            String sourceRef,
            String summary,
            boolean factual,
            Instant occurredAt
    ) {
    }

    public record IncidentActivity(
            UUID id,
            UUID incidentId,
            String activityType,
            IncidentState fromState,
            IncidentState toState,
            String note,
            String actor,
            Instant createdAt
    ) {
    }

    public record IncidentDetail(
            Incident incident,
            List<IncidentEvidence> evidence,
            List<IncidentActivity> timeline,
            IncidentRecovery recovery,
            IncidentIntelligence intelligence
    ) {
    }

    public record IncidentIntelligence(
            IncidentCorrelation correlation,
            List<ChangeCandidate> changeCandidates,
            ConfidenceAssessment confidence,
            List<VerificationStep> verificationPlan
    ) {
    }

    public record IncidentCorrelation(
            List<CorrelationNode> nodes,
            List<CorrelationEdge> edges,
            int impactedResourceCount,
            int impactedWorkloadCount,
            int impactedServiceCount,
            String blastRadiusSummary,
            Instant inventoryCollectedAt
    ) {
    }

    public record CorrelationNode(
            String id,
            String namespace,
            String resourceKind,
            String resourceName,
            String status,
            String role,
            boolean unhealthy,
            boolean inferred
    ) {
    }

    public record CorrelationEdge(
            String sourceId,
            String targetId,
            String relation,
            boolean inferred,
            String evidence
    ) {
    }

    public record ChangeCandidate(
            UUID changeId,
            String namespace,
            String resourceKind,
            String resourceName,
            String changeType,
            String summary,
            Instant detectedAt,
            int relevanceScore,
            String relation,
            String explanation
    ) {
    }

    public record ConfidenceAssessment(
            int score,
            String level,
            String freshness,
            int factualEvidenceCount,
            int inferenceEvidenceCount,
            int verifiedRelationCount,
            List<String> missingEvidence,
            List<String> rationale
    ) {
    }

    public record VerificationStep(
            int order,
            String title,
            String purpose,
            String command,
            String expectedSignal,
            String safetyLevel,
            boolean destructive
    ) {
    }

    public record WatchSignal(
            UUID id,
            UUID clusterId,
            String clusterName,
            String namespace,
            String resourceKind,
            String resourceName,
            String action,
            String reason,
            String status,
            String summary,
            Instant observedAt
    ) {
    }

    public record WatchRuntimeStatus(
            UUID clusterId,
            String clusterName,
            String state,
            Instant connectedAt,
            Instant lastSignalAt,
            int reconnectCount,
            String lastError,
            Instant updatedAt,
            Instant lastHeartbeatAt,
            Instant nextRetryAt,
            int consecutiveFailures,
            boolean paused
    ) {
    }

    public record WatchSignalGroup(
            UUID id,
            String fingerprint,
            UUID clusterId,
            String clusterName,
            String namespace,
            String resourceKind,
            String resourceName,
            String category,
            String severity,
            String state,
            String reason,
            String summary,
            int occurrenceCount,
            Instant firstObservedAt,
            Instant lastObservedAt,
            UUID incidentId,
            String updatedBy,
            Instant updatedAt
    ) {
    }

    public record TriageItem(
            String id,
            String sourceType,
            UUID clusterId,
            String clusterName,
            String namespace,
            String resourceKind,
            String resourceName,
            String severity,
            String state,
            int score,
            int confidence,
            int impact,
            int occurrences,
            String title,
            String summary,
            String nextAction,
            UUID incidentId,
            Instant firstObservedAt,
            Instant lastObservedAt
    ) {
    }

    public record TriageQueue(
            Instant generatedAt,
            int openItems,
            int highPriority,
            int promotedSignals,
            int suppressedSignals,
            List<TriageItem> items
    ) {
    }

    public record RegressionRun(
            UUID id,
            String status,
            int passedCases,
            int totalCases,
            double score,
            String baselineVersion,
            String triggeredBy,
            Instant startedAt,
            Instant completedAt,
            List<RegressionCaseResult> cases
    ) {
    }

    public record RegressionCaseResult(
            UUID id,
            UUID runId,
            String caseId,
            String title,
            String category,
            String status,
            int score,
            List<String> assertions,
            List<String> failures,
            long durationMs
    ) {
    }

    public record IncidentRecovery(
            UUID incidentId,
            int consecutiveHealthyCount,
            int requiredHealthyCount,
            Instant firstHealthyAt,
            Instant lastObservedAt,
            String lastObservedStatus,
            boolean autoResolvable
    ) {
    }

    public record Notification(
            UUID id,
            String dedupKey,
            String notificationType,
            String severity,
            String title,
            String message,
            String targetPath,
            boolean read,
            int occurrenceCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PolicyDefinition(
            String id,
            String name,
            String description,
            String category,
            String severity,
            boolean enabled
    ) {
    }

    public record PolicyEvaluation(
            UUID id,
            String policyId,
            UUID clusterId,
            String clusterName,
            String namespace,
            String resourceKind,
            String resourceName,
            PolicyResult result,
            String evidence,
            String recommendation,
            Instant evaluatedAt
    ) {
    }

    public record ResourceBaseline(
            UUID id,
            UUID clusterId,
            String namespace,
            String resourceKind,
            String resourceName,
            String status,
            String summaryHash,
            String summaryJson,
            Instant collectedAt
    ) {
    }

    public record ResourceChange(
            UUID id,
            UUID clusterId,
            String clusterName,
            String namespace,
            String resourceKind,
            String resourceName,
            String changeType,
            String previousStatus,
            String currentStatus,
            String previousHash,
            String currentHash,
            String summary,
            Instant detectedAt
    ) {
    }

    public record RunbookTemplate(
            String id,
            String signal,
            String category,
            String resourceKind,
            String title,
            String beginnerExplanation,
            String verificationCommand,
            String expectedResult,
            String safeAction,
            String validationCommand,
            String rollbackGuidance,
            String safetyLevel,
            int version,
            boolean enabled
    ) {
    }

    public record AnalysisFeedback(
            UUID analysisId,
            String accuracy,
            String outcome,
            boolean dangerousSuggestion,
            String comment,
            String actualRootCause,
            String actualResolution,
            String validatedResourceKind,
            String validatedResourceName,
            String confidenceExpectation,
            String submittedBy,
            Instant updatedAt
    ) {
    }

    public record AiQualitySummary(
            int feedbackCount,
            int correctCount,
            int partialCount,
            int incorrectCount,
            int resolvedOrImprovedCount,
            int dangerousSuggestionCount,
            int successfulAnalyses,
            int failedAnalyses,
            double successRate,
            double verifiedAccuracyRate
    ) {
    }

    public record ModelQualityProfile(
            String model,
            String promptVersion,
            int feedbackCount,
            double verifiedAccuracyRate,
            double resolutionRate,
            int dangerousSuggestionCount
    ) {
    }

    public record GroundTruthSample(
            UUID analysisId,
            String model,
            String promptVersion,
            String accuracy,
            String actualRootCause,
            String actualResolution,
            String validatedResourceKind,
            String validatedResourceName,
            Instant updatedAt
    ) {
    }

    public record AiCalibrationSummary(
            int feedbackCount,
            int groundTruthCount,
            double groundTruthCoverageRate,
            double verifiedAccuracyRate,
            double resolutionRate,
            int dangerousSuggestionCount,
            List<ModelQualityProfile> profiles,
            List<GroundTruthSample> recentGroundTruth
    ) {
    }

    public record OperationsHotspot(
            UUID clusterId,
            String clusterName,
            String namespace,
            String resourceKind,
            String resourceName,
            int incidentCount,
            int recurrenceCount,
            String severity,
            Instant lastDetectedAt
    ) {
    }

    public record WeeklyTrend(
            int currentIncidents,
            int previousIncidents,
            int currentResolved,
            int previousResolved,
            int currentAnalyses,
            int previousAnalyses,
            String direction
    ) {
    }

    public record OperationsScorecard(
            Instant generatedAt,
            int totalIncidents,
            int openIncidents,
            int resolvedIncidents,
            long meanTimeToAcknowledgeMinutes,
            long meanTimeToResolveMinutes,
            double recurrenceRate,
            double mitigationSuccessRate,
            double analysisSuccessRate,
            double fallbackRate,
            int openSignalGroups,
            int promotedSignalGroups,
            int suppressedSignalGroups,
            WeeklyTrend weeklyTrend,
            List<OperationsHotspot> hotspots
    ) {
    }

    public record OperationSettings(
            int eventRetentionDays,
            int analysisRetentionDays,
            int jobRetentionDays,
            int notificationRetentionDays,
            int resolvedIncidentRetentionDays,
            int changeRetentionDays,
            int auditRetentionDays,
            int commandRetentionDays,
            int notificationSuppressMinutes,
            int staleSyncMinutes,
            int longRunningJobSeconds,
            String updatedBy,
            Instant updatedAt
    ) {
    }

    public record CleanupPreview(
            long eventSnapshots,
            long jobs,
            long notifications,
            long changes,
            long resolvedIncidents,
            long policyEvaluations,
            long analyses,
            long watchSignals,
            long regressionRuns,
            long auditLogs,
            long commandExecutions,
            boolean executed
    ) {
    }

    public record PriorityItem(
            String id,
            String sourceType,
            String urgency,
            int score,
            String severity,
            UUID clusterId,
            String clusterName,
            String namespace,
            String title,
            String reason,
            String nextAction,
            String targetPath,
            Instant detectedAt
    ) {
    }

    public record ClusterHealth(
            UUID clusterId,
            String clusterName,
            String status,
            int healthScore,
            int openIncidents,
            int failedPolicies,
            int warningEvents,
            Instant lastObservedAt,
            String posture
    ) {
    }

    public record CapacityPosture(
            String dataSource,
            int workloads,
            int unavailableWorkloads,
            int pods,
            int unhealthyPods,
            int pendingPvcs,
            int namespacesWithoutNetworkPolicy,
            int governanceEvidenceGaps,
            String summary
    ) {
    }

    public record OperationsOverview(
            Instant generatedAt,
            int clusters,
            int openIncidents,
            int criticalIncidents,
            int unreadNotifications,
            int failedPolicyEvaluations,
            int runningJobs,
            int failedJobs,
            long averageJobDurationMs,
            int successfulAnalyses,
            int failedAnalyses,
            List<PriorityItem> priorityQueue,
            List<ClusterHealth> clusterHealth,
            CapacityPosture capacityPosture,
            AiQualitySummary aiQuality
    ) {
    }
}
