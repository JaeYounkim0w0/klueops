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

    /** AnalysisWorkflowState 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** AnalysisWorkflowState의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static AnalysisWorkflowState create(UUID analysisId, String issueGroupId, String status, String note,
                                               String actor) {
        return new AnalysisWorkflowState(UUID.randomUUID(), analysisId, issueGroupId, status, note, actor, Instant.now());
    }

    /** AnalysisWorkflowState의 update 처리 대상의 상태를 갱신한다. */
    public AnalysisWorkflowState update(String nextStatus, String nextNote, String actor) {
        return new AnalysisWorkflowState(id, analysisId, issueGroupId, nextStatus, nextNote, actor, Instant.now());
    }

    /** AnalysisWorkflowState의 id 처리에 필요한 업무 로직을 수행한다. */
    public UUID id() { return id; }
    /** AnalysisWorkflowState의 analysisId 처리에 필요한 업무 로직을 수행한다. */
    public UUID analysisId() { return analysisId; }
    /** AnalysisWorkflowState의 issueGroupId 처리 조건의 충족 여부를 판단한다. */
    public String issueGroupId() { return issueGroupId; }
    /** AnalysisWorkflowState의 status 처리에 필요한 업무 로직을 수행한다. */
    public String status() { return status; }
    /** AnalysisWorkflowState의 note 처리에 필요한 업무 로직을 수행한다. */
    public String note() { return note; }
    /** AnalysisWorkflowState의 updatedBy 처리 대상의 상태를 갱신한다. */
    public String updatedBy() { return updatedBy; }
    /** AnalysisWorkflowState의 updatedAt 처리 대상의 상태를 갱신한다. */
    public Instant updatedAt() { return updatedAt; }
}
