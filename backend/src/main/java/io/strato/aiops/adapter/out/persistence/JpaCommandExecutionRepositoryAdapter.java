package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.CommandExecutionRepositoryPort;
import io.strato.aiops.domain.command.CommandExecution;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import io.strato.aiops.domain.command.CommandStatus;

@Repository
public class JpaCommandExecutionRepositoryAdapter implements CommandExecutionRepositoryPort {
    private final CommandExecutionJpaRepository repository;

    public JpaCommandExecutionRepositoryAdapter(CommandExecutionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public CommandExecution save(CommandExecution execution) {
        return repository.save(CommandExecutionEntity.fromDomain(execution)).toDomain();
    }

    @Override
    public Optional<CommandExecution> findById(UUID id) {
        return repository.findById(id).map(CommandExecutionEntity::toDomain);
    }

    @Override
    public List<CommandExecution> findRecent(UUID clusterId, String namespace, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 100)));
        List<CommandExecutionEntity> values = namespace == null || namespace.isBlank()
                ? repository.findByClusterIdOrderByCreatedAtDesc(clusterId, page)
                : repository.findByClusterIdAndNamespaceOrderByCreatedAtDesc(clusterId, namespace, page);
        return values.stream().map(CommandExecutionEntity::toDomain).toList();
    }

    @Override
    public List<CommandExecution> findIncompleteBefore(Instant cutoff, int limit) {
        return repository.findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                        List.of(CommandStatus.QUEUED, CommandStatus.RUNNING), cutoff,
                        PageRequest.of(0, Math.max(1, Math.min(limit, 500))))
                .stream().map(CommandExecutionEntity::toDomain).toList();
    }

    @Override
    public List<CommandExecution> findBySourceAnalysisId(UUID sourceAnalysisId, int limit) {
        if (sourceAnalysisId == null) return List.of();
        return repository.findBySourceAnalysisIdOrderByCreatedAtAsc(sourceAnalysisId,
                        PageRequest.of(0, Math.max(1, Math.min(limit, 200))))
                .stream().map(CommandExecutionEntity::toDomain).toList();
    }
}
