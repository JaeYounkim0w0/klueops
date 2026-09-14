package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.AnalysisCommandExecution;
import io.strato.aiops.domain.analysis.AnalysisWorkflowState;

import java.util.List;
import java.util.UUID;

public interface AnalysisCommandUseCase {

    AnalysisCommandPreviewResult previewCommand(UUID analysisId, AnalysisCommandExecuteCommand command);

    AnalysisCommandExecution executeCommand(UUID analysisId, AnalysisCommandExecuteCommand command, String actor, String requestId);

    List<AnalysisCommandExecution> listCommandExecutions(UUID analysisId);

    AnalysisWorkflowState updateWorkflowState(UUID analysisId, String issueGroupId, UpdateAnalysisWorkflowStateCommand command,
                                              String actor, String requestId);

    List<AnalysisWorkflowState> listWorkflowStates(UUID analysisId);
}
