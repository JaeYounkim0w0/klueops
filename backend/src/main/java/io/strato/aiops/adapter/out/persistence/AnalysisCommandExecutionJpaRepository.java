package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AnalysisCommandExecutionJpaRepository extends JpaRepository<AnalysisCommandExecutionEntity, UUID> {

    /** AnalysisCommandExecutionJpaRepository의 findByAnalysisIdOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    List<AnalysisCommandExecutionEntity> findByAnalysisIdOrderByCreatedAtDesc(UUID analysisId);

    /** AnalysisCommandExecutionJpaRepository의 deleteByAnalysisId 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteByAnalysisId(UUID analysisId);
}
