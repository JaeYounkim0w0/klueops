package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.command.CommandExecution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface CommandExecutionRepositoryPort {
    /** CommandExecutionRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    CommandExecution save(CommandExecution execution);
    /** CommandExecutionRepositoryPort의 findById 처리 결과를 조회해 반환한다. */
    Optional<CommandExecution> findById(UUID id);
    /** CommandExecutionRepositoryPort의 findRecent 처리 결과를 조회해 반환한다. */
    List<CommandExecution> findRecent(UUID clusterId, String namespace, int limit);
    /** CommandExecutionRepositoryPort의 findIncompleteBefore 처리 결과를 조회해 반환한다. */
    List<CommandExecution> findIncompleteBefore(Instant cutoff, int limit);
    /** CommandExecutionRepositoryPort의 findBySourceAnalysisId 처리 결과를 조회해 반환한다. */
    default List<CommandExecution> findBySourceAnalysisId(UUID sourceAnalysisId, int limit) { return List.of(); }
}
