package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.analysis.AnalysisCommandExecution;

import java.util.List;
import java.util.UUID;

public interface AnalysisCommandExecutionRepositoryPort {

    /** AnalysisCommandExecutionRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    AnalysisCommandExecution save(AnalysisCommandExecution execution);

    /** AnalysisCommandExecutionRepositoryPort의 findByAnalysisId 처리 결과를 조회해 반환한다. */
    List<AnalysisCommandExecution> findByAnalysisId(UUID analysisId);

    /** AnalysisCommandExecutionRepositoryPort의 deleteByAnalysisId 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteByAnalysisId(UUID analysisId);
}
