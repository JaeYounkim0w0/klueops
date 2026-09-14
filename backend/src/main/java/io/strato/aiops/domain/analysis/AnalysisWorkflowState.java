package io.strato.aiops.domain.analysis;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AnalysisWorkflowState {

    private final UUID id;
    private final UUID analysisId;
    private final String issueGroupId;
    private final String status;
    private final String note;
    private final String updatedBy;
    private final Instant updatedAt;

    public AnalysisWorkflowState(UUID id, UUID analysisId, String issueGroupId, String status, String note,
                                 String updatedBy, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.analysisId = Objects.requireNonNull(analysisId, "analysisId must not be null");
        this.issueGroupId = Objects.requireNonNull(issueGroupId, "issueGroupId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.note = note;
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static AnalysisWorkflowState create(UUID analysisId, String issueGroupId, String status, String note,
                                               String actor) {
        return new AnalysisWorkflowState(UUID.randomUUID(), analysisId, issueGroupId, status, note, actor, Instant.now());
    }

    public AnalysisWorkflowState update(String nextStatus, String nextNote, String actor) {
        return new AnalysisWorkflowState(id, analysisId, issueGroupId, nextStatus, nextNote, actor, Instant.now());
    }

    public UUID id() { return id; }
    public UUID analysisId() { return analysisId; }
    public String issueGroupId() { return issueGroupId; }
    public String status() { return status; }
    public String note() { return note; }
    public String updatedBy() { return updatedBy; }
    public Instant updatedAt() { return updatedAt; }
}
