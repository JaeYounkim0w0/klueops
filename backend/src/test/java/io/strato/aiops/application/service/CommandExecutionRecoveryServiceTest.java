package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.CommandExecutionAdmissionPort;
import io.strato.aiops.application.port.out.CommandExecutionRepositoryPort;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.command.CommandExecution;
import io.strato.aiops.domain.command.CommandSafety;
import io.strato.aiops.domain.command.CommandStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommandExecutionRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-09T05:00:00Z");

    /** CommandExecutionRecoveryServiceTest의 failsAnOrphanedExecutionAndRecordsAuditEvidence 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void failsAnOrphanedExecutionAndRecordsAuditEvidence() {
        CommandExecution running = runningExecution();
        InMemoryExecutionRepository executions = new InMemoryExecutionRepository(running);
        InMemoryAdmission admission = new InMemoryAdmission(Set.of());
        List<AuditLog> audits = new ArrayList<>();
        CommandExecutionRecoveryService service = service(executions, admission, audits);

        service.recoverOrphans();

        CommandExecution recovered = executions.findById(running.id()).orElseThrow();
        assertThat(recovered.status()).isEqualTo(CommandStatus.FAILED);
        assertThat(recovered.stderrText()).contains("worker lease expired");
        assertThat(recovered.exitCode()).isEqualTo(-1);
        assertThat(audits).extracting(AuditLog::action)
                .containsExactly("COMMAND_EXECUTION_RECOVERED_AS_FAILED");
    }

    /** CommandExecutionRecoveryServiceTest의 leavesExecutionRunningWhileItsWorkerLeaseIsActive 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void leavesExecutionRunningWhileItsWorkerLeaseIsActive() {
        CommandExecution running = runningExecution();
        InMemoryExecutionRepository executions = new InMemoryExecutionRepository(running);
        InMemoryAdmission admission = new InMemoryAdmission(Set.of(running.id()));
        List<AuditLog> audits = new ArrayList<>();

        service(executions, admission, audits).recoverOrphans();

        assertThat(executions.findById(running.id()).orElseThrow().status()).isEqualTo(CommandStatus.RUNNING);
        assertThat(audits).isEmpty();
    }

    /** CommandExecutionRecoveryServiceTest의 service 처리에 필요한 업무 로직을 수행한다. */
    private CommandExecutionRecoveryService service(InMemoryExecutionRepository executions,
            InMemoryAdmission admission, List<AuditLog> audits) {
        CommandExecutionCoordinator coordinator = new CommandExecutionCoordinator(admission,
                Clock.fixed(NOW, ZoneOffset.UTC), 5, 3, 50, 20, 30, 120);
        AuditLogRepositoryPort auditRepository = audit -> {
            audits.add(audit);
            return audit;
        };
        return new CommandExecutionRecoveryService(executions, coordinator, new CommandEventStream(), auditRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), 90);
    }

    /** CommandExecutionRecoveryServiceTest의 runningExecution 처리의 핵심 작업 흐름을 실행한다. */
    private CommandExecution runningExecution() {
        Instant createdAt = NOW.minusSeconds(300);
        return new CommandExecution(UUID.randomUUID(), UUID.randomUUID(), null, "default",
                "kubectl get pods", "[]", CommandSafety.READ_ONLY, CommandStatus.RUNNING, "partial", "", null,
                null, false, io.strato.aiops.domain.command.CommandVerificationStatus.NOT_REQUIRED,
                "", "", "", null, null, "operator", "request-1", createdAt, createdAt.plusSeconds(1), null);
    }

    private static final class InMemoryExecutionRepository implements CommandExecutionRepositoryPort {
        private final Map<UUID, CommandExecution> values = new LinkedHashMap<>();

        /** InMemoryExecutionRepository 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
        private InMemoryExecutionRepository(CommandExecution execution) {
            values.put(execution.id(), execution);
        }

        /** InMemoryExecutionRepository의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public CommandExecution save(CommandExecution execution) {
            values.put(execution.id(), execution);
            return execution;
        }

        /** InMemoryExecutionRepository의 findById 처리 결과를 조회해 반환한다. */
        @Override public Optional<CommandExecution> findById(UUID id) { return Optional.ofNullable(values.get(id)); }
        /** InMemoryExecutionRepository의 findRecent 처리 결과를 조회해 반환한다. */
        @Override public List<CommandExecution> findRecent(UUID clusterId, String namespace, int limit) { return List.of(); }
        /** InMemoryExecutionRepository의 findIncompleteBefore 처리 결과를 조회해 반환한다. */
        @Override public List<CommandExecution> findIncompleteBefore(Instant cutoff, int limit) {
            return values.values().stream()
                    .filter(value -> value.completedAt() == null && value.createdAt().isBefore(cutoff))
                    .limit(limit)
                    .toList();
        }
    }

    private static final class InMemoryAdmission implements CommandExecutionAdmissionPort {
        private final Set<UUID> active;

        /** InMemoryAdmission 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
        private InMemoryAdmission(Set<UUID> active) { this.active = active; }
        /** InMemoryAdmission의 acquire 처리에 필요한 업무 로직을 수행한다. */
        @Override public void acquire(AdmissionRequest request) { }
        /** InMemoryAdmission의 renew 처리에 필요한 업무 로직을 수행한다. */
        @Override public void renew(UUID executionId, Instant expiresAt) { }
        /** InMemoryAdmission의 release 처리에 필요한 업무 로직을 수행한다. */
        @Override public void release(UUID executionId) { }
        /** InMemoryAdmission의 isActive 처리 조건의 충족 여부를 판단한다. */
        @Override public boolean isActive(UUID executionId, Instant now) { return active.contains(executionId); }
    }
}
