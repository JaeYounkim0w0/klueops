package io.strato.aiops.domain.operations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OperationsEvolutionModels {

    private OperationsEvolutionModels() {
    }

    public record WatchContinuity(
            UUID clusterId,
            String podResourceVersion,
            String eventResourceVersion,
            String continuityState,
            int gapSignalCount,
            Instant lastReconciledAt,
            String lastError,
            Instant updatedAt
    ) {
    }

    public record SignalNoisePolicy(
            UUID id,
            String name,
            UUID clusterId,
            String namespacePattern,
            String severityFloor,
            int repeatThreshold,
            Instant maintenanceStart,
            Instant maintenanceEnd,
            Instant snoozeUntil,
            boolean enabled,
            String updatedBy,
            Instant updatedAt
    ) {
        public boolean applies(UUID candidateClusterId, String namespace, Instant now) {
            if (!enabled || clusterId != null && !clusterId.equals(candidateClusterId)) return false;
            String pattern = namespacePattern == null || namespacePattern.isBlank() ? "*" : namespacePattern.trim();
            String value = namespace == null ? "" : namespace;
            return "*".equals(pattern)
                    || pattern.endsWith("*") && value.startsWith(pattern.substring(0, pattern.length() - 1))
                    || pattern.equals(value);
        }

        public boolean suppressionActive(Instant now) {
            return snoozeUntil != null && snoozeUntil.isAfter(now)
                    || maintenanceStart != null && maintenanceEnd != null
                    && !now.isBefore(maintenanceStart) && now.isBefore(maintenanceEnd);
        }
    }

    public record RemediationObservation(
            UUID id,
            UUID incidentId,
            UUID analysisId,
            UUID commandExecutionId,
            String state,
            int observationSeconds,
            Instant startedAt,
            Instant observeUntil,
            String baselineJson,
            String latestJson,
            String conclusion,
            String rollbackCandidate,
            String updatedBy,
            Instant updatedAt
    ) {
    }

    public record AiReleaseGate(
            UUID id,
            String candidateVersion,
            String baselineVersion,
            String state,
            double regressionScore,
            double minimumRegressionScore,
            int groundTruthSamples,
            int minimumGroundTruthSamples,
            double verifiedAccuracy,
            double minimumVerifiedAccuracy,
            int dangerousSuggestionCount,
            List<String> reasons,
            Instant evaluatedAt,
            String evaluatedBy
    ) {
    }

    public record IncidentPostmortem(
            UUID incidentId,
            String title,
            String impact,
            String rootCause,
            String resolution,
            List<String> evidence,
            List<String> prevention,
            Instant generatedAt,
            String generatedBy
    ) {
    }
}
