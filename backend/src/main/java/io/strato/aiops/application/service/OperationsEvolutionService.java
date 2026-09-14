package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.AnalysisCommandExecutionRepositoryPort;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesResourceSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.OperationsEvolutionRepositoryPort;
import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisCommandExecution;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.AiReleaseGate;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.IncidentPostmortem;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.RemediationObservation;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.SignalNoisePolicy;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.WatchContinuity;
import io.strato.aiops.domain.operations.OperationsModels.AiCalibrationSummary;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentActivity;
import io.strato.aiops.domain.operations.OperationsModels.IncidentDetail;
import io.strato.aiops.domain.operations.OperationsModels.IncidentEvidence;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import io.strato.aiops.domain.operations.OperationsModels.RegressionRun;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.springframework.scheduling.annotation.Scheduled;
import io.strato.aiops.application.port.out.RuntimeLeasePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class OperationsEvolutionService {
    private RuntimeLeasePort runtimeLeasePort = RuntimeLeasePort.localOnly();

    @org.springframework.beans.factory.annotation.Autowired
    void setRuntimeLeasePort(RuntimeLeasePort runtimeLeasePort) {
        this.runtimeLeasePort = runtimeLeasePort;
    }

    private static final Set<String> UNHEALTHY_STATUS = Set.of(
            "PENDING", "FAILED", "UNKNOWN", "CRASHLOOPBACKOFF", "IMAGEPULLBACKOFF",
            "ERRIMAGEPULL", "PROGRESSDEADLINEEXCEEDED", "DEGRADED"
    );

    private final OperationsEvolutionRepositoryPort repository;
    private final OperationsRepositoryPort operationsRepository;
    private final KubernetesResourceSnapshotRepositoryPort resourceRepository;
    private final AnalysisCommandExecutionRepositoryPort commandRepository;
    private final AnalysisRegressionService regressionService;
    private final OperationsControlPlaneService operationsService;
    private final AuditLogRepositoryPort auditRepository;
    private final OperationsEventStream eventStream;
    private final ObjectMapper objectMapper;

    public OperationsEvolutionService(
            OperationsEvolutionRepositoryPort repository,
            OperationsRepositoryPort operationsRepository,
            KubernetesResourceSnapshotRepositoryPort resourceRepository,
            AnalysisCommandExecutionRepositoryPort commandRepository,
            AnalysisRegressionService regressionService,
            OperationsControlPlaneService operationsService,
            AuditLogRepositoryPort auditRepository,
            OperationsEventStream eventStream,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.operationsRepository = operationsRepository;
        this.resourceRepository = resourceRepository;
        this.commandRepository = commandRepository;
        this.regressionService = regressionService;
        this.operationsService = operationsService;
        this.auditRepository = auditRepository;
        this.eventStream = eventStream;
        this.objectMapper = objectMapper;
    }

    public List<WatchContinuity> watchContinuities() {
        return repository.findWatchContinuities();
    }

    public List<SignalNoisePolicy> noisePolicies() {
        return repository.findNoisePolicies();
    }

    @Transactional
    public SignalNoisePolicy saveNoisePolicy(UUID id, String name, UUID clusterId, String namespacePattern,
                                             String severityFloor, int repeatThreshold, Instant maintenanceStart,
                                             Instant maintenanceEnd, Instant snoozeUntil, boolean enabled,
                                             String actor, String requestId) {
        if (maintenanceStart != null && maintenanceEnd != null && !maintenanceEnd.isAfter(maintenanceStart)) {
            throw new IllegalArgumentException("maintenanceEnd must be after maintenanceStart");
        }
        String severity = normalizeSeverity(severityFloor);
        SignalNoisePolicy saved = repository.saveNoisePolicy(new SignalNoisePolicy(
                id == null ? UUID.randomUUID() : id, requireText(name, "name"), clusterId,
                namespacePattern == null || namespacePattern.isBlank() ? "*" : namespacePattern.trim(),
                severity, Math.max(1, Math.min(repeatThreshold, 100)), maintenanceStart, maintenanceEnd,
                snoozeUntil, enabled, actor, Instant.now()));
        audit("SIGNAL_NOISE_POLICY_SAVED", "SIGNAL_NOISE_POLICY", saved.id().toString(), actor, requestId);
        eventStream.publish("noise-policy", saved);
        return saved;
    }

    @Transactional
    public void deleteNoisePolicy(UUID policyId, String actor, String requestId) {
        repository.findNoisePolicy(policyId)
                .orElseThrow(() -> new NoSuchElementException("Signal noise policy not found: " + policyId));
        repository.deleteNoisePolicy(policyId);
        audit("SIGNAL_NOISE_POLICY_DELETED", "SIGNAL_NOISE_POLICY", policyId.toString(), actor, requestId);
        eventStream.publish("noise-policy", java.util.Map.of("id", policyId, "deleted", true));
    }

    @Transactional
    public RemediationObservation startObservation(UUID incidentId, UUID analysisId, UUID commandExecutionId,
                                                    int observationSeconds, String actor, String requestId) {
        Incident incident = requireIncident(incidentId);
        if (commandExecutionId != null) {
            if (analysisId == null) throw new IllegalArgumentException("analysisId is required with commandExecutionId");
            AnalysisCommandExecution execution = commandRepository.findByAnalysisId(analysisId).stream()
                    .filter(item -> item.id().equals(commandExecutionId)).findFirst()
                    .orElseThrow(() -> new NoSuchElementException("Command execution not found: " + commandExecutionId));
            if (!"SUCCEEDED".equals(execution.status().name())) {
                throw new IllegalArgumentException("Only a succeeded command can start remediation observation");
            }
        }
        int seconds = Math.max(30, Math.min(observationSeconds, 3600));
        Instant now = Instant.now();
        RemediationObservation saved = repository.saveRemediationObservation(new RemediationObservation(
                UUID.randomUUID(), incidentId, analysisId, commandExecutionId, "OBSERVING", seconds, now,
                now.plusSeconds(seconds), snapshotJson(incident, matchingSnapshot(incident)), null,
                "조치 후 Kubernetes 상태를 관찰하고 있습니다.", null, actor, now));
        audit("REMEDIATION_OBSERVATION_STARTED", "INCIDENT", incidentId.toString(), actor, requestId);
        eventStream.publish("remediation", saved);
        return saved;
    }

    public List<RemediationObservation> observations(UUID incidentId, String state, int limit) {
        return repository.findRemediationObservations(incidentId, normalize(state), limit);
    }

    @Transactional
    public RemediationObservation evaluateObservation(UUID observationId, String actor, String requestId) {
        RemediationObservation current = repository.findRemediationObservation(observationId)
                .orElseThrow(() -> new NoSuchElementException("Remediation observation not found: " + observationId));
        if (!"OBSERVING".equals(current.state())) return current;
        Incident incident = requireIncident(current.incidentId());
        KubernetesResourceSnapshot latest = matchingSnapshot(incident);
        Instant now = Instant.now();
        String state = "OBSERVING";
        String conclusion = "관찰 시간이 진행 중입니다.";
        String rollback = null;
        if (latest != null && !unhealthy(latest.status())) {
            state = "SUCCEEDED";
            conclusion = "동일 리소스가 최신 Kubernetes snapshot에서 정상 상태로 확인되었습니다.";
        } else if (!now.isBefore(current.observeUntil())) {
            state = latest == null ? "INCONCLUSIVE" : "FAILED";
            conclusion = latest == null
                    ? "최신 Kubernetes snapshot이 없어 조치 결과를 확정할 수 없습니다."
                    : "관찰 시간이 끝났지만 동일 리소스의 비정상 상태가 유지됩니다.";
            if ("FAILED".equals(state) && current.commandExecutionId() != null) {
                rollback = "실행 전 revision 또는 설정으로 되돌릴 수 있는지 Rollback Guard에서 확인하세요.";
            }
        }
        RemediationObservation saved = repository.saveRemediationObservation(new RemediationObservation(
                current.id(), current.incidentId(), current.analysisId(), current.commandExecutionId(), state,
                current.observationSeconds(), current.startedAt(), current.observeUntil(), current.baselineJson(),
                snapshotJson(incident, latest), conclusion, rollback, actor, now));
        audit("REMEDIATION_OBSERVATION_EVALUATED", "INCIDENT", incident.id().toString(), actor, requestId);
        recordObservationOutcome(incident, saved, actor);
        eventStream.publish("remediation", saved);
        return saved;
    }

    @Transactional
    public RemediationObservation cancelObservation(UUID observationId, String actor, String requestId) {
        RemediationObservation current = repository.findRemediationObservation(observationId)
                .orElseThrow(() -> new NoSuchElementException("Remediation observation not found: " + observationId));
        if (!"OBSERVING".equals(current.state())) return current;
        RemediationObservation saved = repository.saveRemediationObservation(new RemediationObservation(
                current.id(), current.incidentId(), current.analysisId(), current.commandExecutionId(), "CANCELLED",
                current.observationSeconds(), current.startedAt(), current.observeUntil(), current.baselineJson(),
                current.latestJson(), "운영자가 관찰을 중단했습니다.", null, actor, Instant.now()));
        audit("REMEDIATION_OBSERVATION_CANCELLED", "INCIDENT", current.incidentId().toString(), actor, requestId);
        eventStream.publish("remediation", saved);
        return saved;
    }

    @Scheduled(fixedDelay = 15000)
    public void evaluateRunningObservations() {
        if (!runtimeLeasePort.acquireOrRenew("scheduler:remediation-observation", Duration.ofSeconds(45))) return;
        repository.findRemediationObservations(null, "OBSERVING", 100).forEach(item -> {
            try {
                evaluateObservation(item.id(), "system", "scheduled-remediation-observation");
            } catch (RuntimeException ignored) {
                // One inaccessible cluster must not stop other observation sessions.
            }
        });
    }

    @Transactional
    public AiReleaseGate evaluateReleaseGate(String candidateVersion, String baselineVersion,
                                             double minimumRegressionScore, int minimumGroundTruthSamples,
                                             double minimumVerifiedAccuracy, String actor, String requestId) {
        RegressionRun regression = regressionService.run(actor);
        AiCalibrationSummary calibration = operationsService.getAiCalibration();
        List<String> reasons = new ArrayList<>();
        if (!"PASSED".equals(regression.status()) || regression.score() < minimumRegressionScore) {
            reasons.add("회귀 인증 점수가 기준보다 낮습니다.");
        }
        if (calibration.groundTruthCount() < minimumGroundTruthSamples) {
            reasons.add("Ground Truth 표본이 " + minimumGroundTruthSamples + "개 미만입니다.");
        }
        if (calibration.verifiedAccuracyRate() < minimumVerifiedAccuracy) {
            reasons.add("검증 정확도가 기준보다 낮습니다.");
        }
        if (calibration.dangerousSuggestionCount() > 0) {
            reasons.add("위험한 제안으로 표시된 피드백이 존재합니다.");
        }
        AiReleaseGate saved = repository.saveReleaseGate(new AiReleaseGate(
                UUID.randomUUID(), requireText(candidateVersion, "candidateVersion"),
                requireText(baselineVersion, "baselineVersion"), reasons.isEmpty() ? "PASSED" : "BLOCKED",
                regression.score(), bounded(minimumRegressionScore), calibration.groundTruthCount(),
                Math.max(0, minimumGroundTruthSamples), calibration.verifiedAccuracyRate(),
                bounded(minimumVerifiedAccuracy), calibration.dangerousSuggestionCount(), List.copyOf(reasons),
                Instant.now(), actor));
        audit("AI_RELEASE_GATE_EVALUATED", "AI_RELEASE_GATE", saved.id().toString(), actor, requestId);
        eventStream.publish("ai-release-gate", saved);
        return saved;
    }

    public List<AiReleaseGate> releaseGates(int limit) {
        return repository.findReleaseGates(limit);
    }

    @Transactional
    public IncidentPostmortem generatePostmortem(UUID incidentId, String actor, String requestId) {
        IncidentDetail detail = operationsService.getIncident(incidentId);
        Incident incident = detail.incident();
        List<IncidentEvidence> factual = detail.evidence().stream().filter(IncidentEvidence::factual).toList();
        String rootCause = factual.stream().findFirst().map(IncidentEvidence::summary)
                .orElse("확정된 사실 근거가 부족합니다. 원인을 단정하지 마세요.");
        String resolution = detail.timeline().stream()
                .filter(item -> item.toState() != null && Set.of("MONITORING", "RESOLVED").contains(item.toState().name()))
                .map(IncidentActivity::note).filter(this::hasText).findFirst()
                .orElse("검증된 해결 조치가 아직 기록되지 않았습니다.");
        List<String> evidence = factual.stream().limit(10)
                .map(item -> item.evidenceType() + ": " + item.summary()).toList();
        List<String> prevention = List.of(
                "동일 fingerprint 재발 여부를 Triage와 Incident에서 추적합니다.",
                "관련 Kubernetes 정책 위반과 최근 변경 후보를 함께 검토합니다.",
                "조치 후 Closed-loop 관찰을 실행해 정상 상태를 확인합니다."
        );
        IncidentPostmortem saved = repository.savePostmortem(new IncidentPostmortem(incidentId,
                incident.title() + " 회고", detail.intelligence().correlation().blastRadiusSummary(), rootCause,
                resolution, evidence, prevention, Instant.now(), actor));
        audit("INCIDENT_POSTMORTEM_GENERATED", "INCIDENT", incidentId.toString(), actor, requestId);
        eventStream.publish("postmortem", saved);
        return saved;
    }

    public IncidentPostmortem postmortem(UUID incidentId) {
        return repository.findPostmortem(incidentId)
                .orElseThrow(() -> new NoSuchElementException("Incident postmortem not found: " + incidentId));
    }

    private Incident requireIncident(UUID incidentId) {
        return operationsRepository.findIncidentById(incidentId)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + incidentId));
    }

    private KubernetesResourceSnapshot matchingSnapshot(Incident incident) {
        return resourceRepository.findLatest(incident.clusterId(), incident.namespace(), incident.resourceKind(), 200)
                .stream().filter(item -> item.resourceName().equals(incident.resourceName())).findFirst().orElse(null);
    }

    private boolean unhealthy(String status) {
        if (status == null || status.isBlank()) return true;
        String normalized = status.toUpperCase(Locale.ROOT);
        if (UNHEALTHY_STATUS.stream().anyMatch(normalized::contains)) return true;
        if (normalized.matches("\\d+/\\d+")) {
            String[] replicas = normalized.split("/", 2);
            return !replicas[0].equals(replicas[1]);
        }
        return false;
    }

    private void recordObservationOutcome(Incident incident, RemediationObservation observation, String actor) {
        if (!Set.of("SUCCEEDED", "FAILED", "INCONCLUSIVE").contains(observation.state())) return;
        IncidentState target = incident.state();
        String activityType = "REMEDIATION_" + observation.state();
        if ("SUCCEEDED".equals(observation.state())
                && Set.of(IncidentState.INVESTIGATING, IncidentState.MITIGATING).contains(incident.state())) {
            target = IncidentState.MONITORING;
            operationsRepository.saveIncident(new Incident(incident.id(), incident.fingerprint(), incident.clusterId(),
                    incident.clusterName(), incident.namespace(), incident.resourceKind(), incident.resourceName(),
                    incident.category(), incident.severity(), target, incident.title(), incident.summary(),
                    incident.nextAction(), incident.occurrenceCount(), incident.reopenCount(), incident.sourceAnalysisId(),
                    incident.firstDetectedAt(), incident.lastDetectedAt(), actor));
        }
        operationsRepository.saveIncidentActivity(new IncidentActivity(UUID.randomUUID(), incident.id(), activityType,
                incident.state(), target, observation.conclusion(), actor, Instant.now()));
    }

    private String snapshotJson(Incident incident, KubernetesResourceSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "resourceKind", incident.resourceKind(),
                    "resourceName", incident.resourceName(),
                    "namespace", incident.namespace() == null ? "" : incident.namespace(),
                    "status", snapshot == null || snapshot.status() == null ? "UNAVAILABLE" : snapshot.status(),
                    "collectedAt", snapshot == null ? "" : String.valueOf(snapshot.collectedAt())
            ));
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String normalizeSeverity(String value) {
        String normalized = normalize(value);
        if (!Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(normalized)) {
            throw new IllegalArgumentException("severityFloor must be LOW, MEDIUM, HIGH or CRITICAL");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private double bounded(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void audit(String action, String targetType, String targetId, String actor, String requestId) {
        auditRepository.save(AuditLog.create(action, targetType, targetId, actor, requestId));
    }
}
