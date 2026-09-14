package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.command.CommandExecution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface CommandExecutionRepositoryPort {
    CommandExecution save(CommandExecution execution);
    Optional<CommandExecution> findById(UUID id);
    List<CommandExecution> findRecent(UUID clusterId, String namespace, int limit);
    List<CommandExecution> findIncompleteBefore(Instant cutoff, int limit);
    default List<CommandExecution> findBySourceAnalysisId(UUID sourceAnalysisId, int limit) { return List.of(); }
}
