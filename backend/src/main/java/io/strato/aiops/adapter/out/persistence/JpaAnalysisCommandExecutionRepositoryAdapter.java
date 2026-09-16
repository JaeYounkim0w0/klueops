package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AnalysisCommandExecutionRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisCommandExecution;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JpaAnalysisCommandExecutionRepositoryAdapter implements AnalysisCommandExecutionRepositoryPort {

    private final AnalysisCommandExecutionJpaRepository repository;

    /** JpaAnalysisCommandExecutionRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaAnalysisCommandExecutionRepositoryAdapter(AnalysisCommandExecutionJpaRepository repository) {
        this.repository = repository;
    }

    /** JpaAnalysisCommandExecutionRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public AnalysisCommandExecution save(AnalysisCommandExecution execution) {
        return repository.save(AnalysisCommandExecutionEntity.fromDomain(execution)).toDomain();
    }

    /** JpaAnalysisCommandExecutionRepositoryAdapter의 findByAnalysisId 처리 결과를 조회해 반환한다. */
    @Override
    public List<AnalysisCommandExecution> findByAnalysisId(UUID analysisId) {
        return repository.findByAnalysisIdOrderByCreatedAtDesc(analysisId).stream()
                .map(AnalysisCommandExecutionEntity::toDomain)
                .toList();
    }

    /** JpaAnalysisCommandExecutionRepositoryAdapter의 deleteByAnalysisId 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    public void deleteByAnalysisId(UUID analysisId) {
        repository.deleteByAnalysisId(analysisId);
    }
}
