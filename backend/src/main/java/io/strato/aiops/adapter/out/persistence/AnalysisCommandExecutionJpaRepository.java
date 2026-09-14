package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AnalysisCommandExecutionJpaRepository extends JpaRepository<AnalysisCommandExecutionEntity, UUID> {

    List<AnalysisCommandExecutionEntity> findByAnalysisIdOrderByCreatedAtDesc(UUID analysisId);

    void deleteByAnalysisId(UUID analysisId);
}
