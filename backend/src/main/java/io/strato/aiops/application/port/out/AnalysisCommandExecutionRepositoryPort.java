package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.analysis.AnalysisCommandExecution;

import java.util.List;
import java.util.UUID;

public interface AnalysisCommandExecutionRepositoryPort {

    AnalysisCommandExecution save(AnalysisCommandExecution execution);

    List<AnalysisCommandExecution> findByAnalysisId(UUID analysisId);

    void deleteByAnalysisId(UUID analysisId);
}
