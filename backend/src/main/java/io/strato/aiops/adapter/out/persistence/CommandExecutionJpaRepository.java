package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

interface CommandExecutionJpaRepository extends JpaRepository<CommandExecutionEntity, UUID> {
    /** CommandExecutionJpaRepository의 findByClusterIdOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    List<CommandExecutionEntity> findByClusterIdOrderByCreatedAtDesc(UUID clusterId, Pageable pageable);
    /** CommandExecutionJpaRepository의 findByClusterIdAndNamespaceOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    List<CommandExecutionEntity> findByClusterIdAndNamespaceOrderByCreatedAtDesc(UUID clusterId, String namespace, Pageable pageable);
    /** CommandExecutionJpaRepository의 findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc 처리 결과를 조회해 반환한다. */
    List<CommandExecutionEntity> findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
            List<io.strato.aiops.domain.command.CommandStatus> statuses, Instant cutoff, Pageable pageable);
    /** CommandExecutionJpaRepository의 findBySourceAnalysisIdOrderByCreatedAtAsc 처리 결과를 조회해 반환한다. */
    List<CommandExecutionEntity> findBySourceAnalysisIdOrderByCreatedAtAsc(UUID sourceAnalysisId, Pageable pageable);
}
