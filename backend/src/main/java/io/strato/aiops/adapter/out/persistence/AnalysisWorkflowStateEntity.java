package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.analysis.AnalysisWorkflowState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_workflow_states")
class AnalysisWorkflowStateEntity {

    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID analysisId;
    @Column(nullable = false)
    private String issueGroupId;
    @Column(nullable = false)
    private String status;
    @Column(length = 1000)
    private String note;
    @Column(nullable = false)
    private String updatedBy;
    @Column(nullable = false)
    private Instant updatedAt;

    protected AnalysisWorkflowStateEntity() {
    }

    private AnalysisWorkflowStateEntity(UUID id, UUID analysisId, String issueGroupId, String status, String note,
                                        String updatedBy, Instant updatedAt) {
        this.id = id;
        this.analysisId = analysisId;
        this.issueGroupId = issueGroupId;
        this.status = status;
        this.note = note;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    static AnalysisWorkflowStateEntity fromDomain(AnalysisWorkflowState workflowState) {
        return new AnalysisWorkflowStateEntity(workflowState.id(), workflowState.analysisId(),
                workflowState.issueGroupId(), workflowState.status(), workflowState.note(), workflowState.updatedBy(),
                workflowState.updatedAt());
    }

    AnalysisWorkflowState toDomain() {
        return new AnalysisWorkflowState(id, analysisId, issueGroupId, status, note, updatedBy, updatedAt);
    }
}
