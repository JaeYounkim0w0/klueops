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

    /** AnalysisWorkflowStateEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected AnalysisWorkflowStateEntity() {
    }

    /** AnalysisWorkflowStateEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** AnalysisWorkflowStateEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static AnalysisWorkflowStateEntity fromDomain(AnalysisWorkflowState workflowState) {
        return new AnalysisWorkflowStateEntity(workflowState.id(), workflowState.analysisId(),
                workflowState.issueGroupId(), workflowState.status(), workflowState.note(), workflowState.updatedBy(),
                workflowState.updatedAt());
    }

    /** AnalysisWorkflowStateEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    AnalysisWorkflowState toDomain() {
        return new AnalysisWorkflowState(id, analysisId, issueGroupId, status, note, updatedBy, updatedAt);
    }
}
