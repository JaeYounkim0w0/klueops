package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.AnalysisCommandExecution;
import io.strato.aiops.domain.analysis.AnalysisWorkflowState;

import java.util.List;
import java.util.UUID;

public interface AnalysisCommandUseCase {

    /** AnalysisCommandUseCase의 previewCommand 처리 계약을 정의한다. */
    AnalysisCommandPreviewResult previewCommand(UUID analysisId, AnalysisCommandExecuteCommand command);

    /** AnalysisCommandUseCase의 executeCommand 처리의 핵심 작업 흐름을 실행한다. */
    AnalysisCommandExecution executeCommand(UUID analysisId, AnalysisCommandExecuteCommand command, String actor, String requestId);

    /** AnalysisCommandUseCase의 listCommandExecutions 처리 결과를 조회해 반환한다. */
    List<AnalysisCommandExecution> listCommandExecutions(UUID analysisId);

    /** AnalysisCommandUseCase의 updateWorkflowState 처리 대상의 상태를 갱신한다. */
    AnalysisWorkflowState updateWorkflowState(UUID analysisId, String issueGroupId, UpdateAnalysisWorkflowStateCommand command,
                                              String actor, String requestId);

    /** AnalysisCommandUseCase의 listWorkflowStates 처리 결과를 조회해 반환한다. */
    List<AnalysisWorkflowState> listWorkflowStates(UUID analysisId);
}
