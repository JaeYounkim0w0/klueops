package io.strato.aiops.domain.operator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OperatorWorkspaceModels {

    /** OperatorWorkspaceModels 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private OperatorWorkspaceModels() {
    }

    public record SearchResult(
            String id, String type, String title, String description,
            UUID clusterId, String clusterName, String namespace,
            String resourceKind, String resourceName, String status,
            String targetPath, Instant updatedAt
    ) {
    }

    public record ResourceContext(
            UUID clusterId, String namespace, String resourceKind, String resourceName,
            String status, Instant collectedAt, List<ResourceRelation> relations,
            List<ResourceChangeItem> changes, List<ResourceFieldDiff> fieldDiffs,
            List<LinkedIncident> incidents
    ) {
    }

    public record ResourceFieldDiff(String field, String previousValue, String currentValue) {
    }

    public record ResourceRelation(
            String relation, String resourceKind, String resourceName,
            String status, boolean explicit, String evidence
    ) {
    }

    public record ResourceChangeItem(
            UUID id, String changeType, String previousStatus, String currentStatus,
            String summary, Instant detectedAt
    ) {
    }

    public record LinkedIncident(
            UUID id, String severity, String state, String title, Instant lastDetectedAt
    ) {
    }

    public record IncidentCollaboration(
            UUID incidentId, String assignee, List<String> tags,
            Instant acknowledgeDueAt, Instant resolveDueAt,
            String updatedBy, Instant updatedAt, List<IncidentLink> links
    ) {
    }

    public record IncidentLink(
            UUID incidentId, UUID relatedIncidentId, String relationType,
            String relatedTitle, String relatedState, String createdBy, Instant createdAt
    ) {
    }

    public record ManagedRunbook(
            String id, String sourceType, String signal, String category, String resourceKind,
            String title, String beginnerExplanation, String verificationCommand,
            String expectedResult, String safeAction, String validationCommand,
            String rollbackGuidance, String safetyLevel, int version, boolean enabled,
            String owner, Instant createdAt, Instant updatedAt
    ) {
    }

    public record RunbookVersion(
            UUID id, String runbookId, int version, String changeNote,
            String createdBy, Instant createdAt
    ) {
    }
}
