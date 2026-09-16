package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.CommandExecutionRepositoryPort;
import io.strato.aiops.application.port.out.SensitiveDataMaskingPort;
import io.strato.aiops.domain.command.CommandExecution;
import io.strato.aiops.domain.command.CommandSafety;
import io.strato.aiops.domain.command.CommandStatus;
import io.strato.aiops.domain.command.CommandVerificationStatus;
import io.strato.aiops.domain.operations.OperationsModels.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentReportExportServiceTest {

    /** IncidentReportExportServiceTest의 exportsLinkedCommandVerificationAsHashedEvidence 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void exportsLinkedCommandVerificationAsHashedEvidence() {
        UUID incidentId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        CommandExecution execution = command(analysisId);
        CommandExecutionRepositoryPort commands = new CommandExecutionRepositoryPort() {
            /** 익명 구현체의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
            @Override public CommandExecution save(CommandExecution value) { return value; }
            /** 익명 구현체의 findById 처리 결과를 조회해 반환한다. */
            @Override public Optional<CommandExecution> findById(UUID id) { return Optional.empty(); }
            /** 익명 구현체의 findRecent 처리 결과를 조회해 반환한다. */
            @Override public List<CommandExecution> findRecent(UUID clusterId, String namespace, int limit) { return List.of(); }
            /** 익명 구현체의 findIncompleteBefore 처리 결과를 조회해 반환한다. */
            @Override public List<CommandExecution> findIncompleteBefore(Instant cutoff, int limit) { return List.of(); }
            /** 익명 구현체의 findBySourceAnalysisId 처리 결과를 조회해 반환한다. */
            @Override public List<CommandExecution> findBySourceAnalysisId(UUID id, int limit) {
                return analysisId.equals(id) ? List.of(execution) : List.of();
            }
        };
        AuditLogRepositoryPort audits = audit -> audit;
        SensitiveDataMaskingPort masker = new SensitiveDataMaskingPort() {
            /** 익명 구현체의 isSensitiveKey 처리 조건의 충족 여부를 판단한다. */
            @Override public boolean isSensitiveKey(String key) { return key.toLowerCase().contains("secret"); }
            /** 익명 구현체의 maskValue 처리에 필요한 업무 로직을 수행한다. */
            @Override public String maskValue(String value) { return "***"; }
            /** 익명 구현체의 maskText 처리에 필요한 업무 로직을 수행한다. */
            @Override public String maskText(String value) { return value.replace("password=plain", "password=***"); }
        };
        var service = new IncidentReportExportService(null, audits, new ObjectMapper(), masker,
                new SimpleMeterRegistry(), commands);

        String report = service.markdown(detail(incidentId, analysisId));

        assertThat(report).contains("Linked Command Verifications")
                .contains("VERIFIED_CHANGED")
                .contains("before `")
                .contains("after `")
                .doesNotContain("password=plain")
                .doesNotContain("before-secret", "after-secret");
    }

    /** IncidentReportExportServiceTest의 detail 처리에 필요한 업무 로직을 수행한다. */
    private IncidentDetail detail(UUID incidentId, UUID analysisId) {
        Instant now = Instant.parse("2026-09-09T00:00:00Z");
        Incident incident = new Incident(incidentId, "fingerprint", UUID.randomUUID(), "dev-master", "default",
                "Deployment", "api", "availability", "HIGH", IncidentState.INVESTIGATING,
                "API unavailable", "summary", "verify rollout", 1, 0, analysisId, now, now, "operator");
        IncidentIntelligence intelligence = new IncidentIntelligence(
                new IncidentCorrelation(List.of(), List.of(), 1, 1, 1, "single workload", now), List.of(),
                new ConfidenceAssessment(80, "HIGH", "FRESH", 1, 0, 1, List.of(), List.of("event evidence")),
                List.of(new VerificationStep(1, "Verify", "Check rollout", "kubectl get deploy api",
                        "Available", "READ_ONLY", false)));
        return new IncidentDetail(incident, List.of(), List.of(),
                new IncidentRecovery(incidentId, 0, 3, null, now, "Unhealthy", true), intelligence);
    }

    /** IncidentReportExportServiceTest의 command 처리에 필요한 업무 로직을 수행한다. */
    private CommandExecution command(UUID analysisId) {
        Instant now = Instant.parse("2026-09-09T00:01:00Z");
        return new CommandExecution(UUID.randomUUID(), UUID.randomUUID(), analysisId, "default",
                "kubectl scale deployment/api --replicas=2", "[]", CommandSafety.CHANGE, CommandStatus.SUCCEEDED,
                "password=plain", "", 0, 120L, false, CommandVerificationStatus.VERIFIED_CHANGED,
                "state changed", "before-secret", "after-secret", "kubectl scale deployment/api --replicas=1",
                now, "operator", "request-1", now, now, now);
    }
}
