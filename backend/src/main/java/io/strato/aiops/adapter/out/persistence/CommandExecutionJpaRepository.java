package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

interface CommandExecutionJpaRepository extends JpaRepository<CommandExecutionEntity, UUID> {
    List<CommandExecutionEntity> findByClusterIdOrderByCreatedAtDesc(UUID clusterId, Pageable pageable);
    List<CommandExecutionEntity> findByClusterIdAndNamespaceOrderByCreatedAtDesc(UUID clusterId, String namespace, Pageable pageable);
    List<CommandExecutionEntity> findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
            List<io.strato.aiops.domain.command.CommandStatus> statuses, Instant cutoff, Pageable pageable);
    List<CommandExecutionEntity> findBySourceAnalysisIdOrderByCreatedAtAsc(UUID sourceAnalysisId, Pageable pageable);
}
