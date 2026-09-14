package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AnalysisSessionRepositoryPort;
import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesEventSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisSession;
import io.strato.aiops.domain.analysis.AnalysisStatus;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobStatus;
import io.strato.aiops.domain.operations.OperationsModels.AiQualitySummary;
import io.strato.aiops.domain.operations.OperationsModels.CapacityPosture;
import io.strato.aiops.domain.operations.OperationsModels.ClusterHealth;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import io.strato.aiops.domain.operations.OperationsModels.OperationsOverview;
import io.strato.aiops.domain.operations.OperationsModels.PolicyEvaluation;
import io.strato.aiops.domain.operations.OperationsModels.PolicyResult;
import io.strato.aiops.domain.operations.OperationsModels.PriorityItem;
import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class OperationsOverviewQueryService {

    private final ClusterRepositoryPort clusterRepository;
    private final OperationsRepositoryPort operationsRepository;
    private final AsyncJobRepositoryPort jobRepository;
    private final AnalysisSessionRepositoryPort analysisRepository;
    private final KubernetesEventSnapshotRepositoryPort eventRepository;

    public OperationsOverviewQueryService(ClusterRepositoryPort clusterRepository,
                                          OperationsRepositoryPort operationsRepository,
                                          AsyncJobRepositoryPort jobRepository,
                                          AnalysisSessionRepositoryPort analysisRepository,
                                          KubernetesEventSnapshotRepositoryPort eventRepository) {
        this.clusterRepository = clusterRepository;
        this.operationsRepository = operationsRepository;
        this.jobRepository = jobRepository;
        this.analysisRepository = analysisRepository;
        this.eventRepository = eventRepository;
    }

    public OperationsOverview build(AiQualitySummary aiQuality) {
        List<Cluster> clusters = clusterRepository.findAll();
        List<Incident> incidents = operationsRepository.findIncidents(null, null, null, null, 500);
        List<PolicyEvaluation> evaluations = operationsRepository.findPolicyEvaluations(null, null, null, 500);
        List<AsyncJob> jobs = jobRepository.findRecent(100);
        List<AnalysisSession> analyses = analysisRepository.findRecent(null, null, null, 100);
        List<ClusterHealth> health = clusters.stream()
                .map(cluster -> clusterHealth(cluster, incidents, evaluations,
                        eventRepository.findLatest(cluster.id(), null, 500)))
                .sorted(Comparator.comparingInt(ClusterHealth::healthScore))
                .toList();
        int runningJobs = count(jobs,
                job -> EnumSet.of(AsyncJobStatus.PENDING, AsyncJobStatus.RUNNING).contains(job.status()));
        int failedJobs = count(jobs,
                job -> EnumSet.of(AsyncJobStatus.FAILED, AsyncJobStatus.TIMEOUT).contains(job.status()));
        long averageJobDuration = Math.round(jobs.stream()
                .filter(job -> job.startedAt() != null && job.completedAt() != null)
                .mapToLong(job -> Duration.between(job.startedAt(), job.completedAt()).toMillis())
                .average().orElse(0));
        int succeeded = count(analyses, analysis -> analysis.status() == AnalysisStatus.SUCCEEDED);
        int failed = count(analyses, analysis -> analysis.status() == AnalysisStatus.FAILED);
        return new OperationsOverview(Instant.now(), clusters.size(),
                count(incidents, incident -> incident.state() != IncidentState.RESOLVED),
                count(incidents, incident -> incident.state() != IncidentState.RESOLVED
                        && "CRITICAL".equals(incident.severity())),
                Math.toIntExact(operationsRepository.countUnreadNotifications()),
                count(evaluations, item -> item.result() == PolicyResult.FAIL), runningJobs, failedJobs,
                averageJobDuration, succeeded, failed, priorityQueue(incidents, jobs), health,
                capacityPosture(evaluations), aiQuality);
    }

    static ClusterHealth clusterHealth(Cluster cluster,
                                       List<Incident> incidents,
                                       List<PolicyEvaluation> evaluations,
                                       List<KubernetesEventSnapshot> events) {
        int open = count(incidents, incident -> incident.clusterId().equals(cluster.id())
                && incident.state() != IncidentState.RESOLVED);
        int critical = count(incidents, incident -> incident.clusterId().equals(cluster.id())
                && incident.state() != IncidentState.RESOLVED
                && Set.of("CRITICAL", "HIGH").contains(incident.severity()));
        int failedPolicies = count(evaluations, evaluation -> evaluation.clusterId().equals(cluster.id())
                && evaluation.result() == PolicyResult.FAIL);
        int warningEvents = count(events, event -> "WARNING".equalsIgnoreCase(event.type()));
        int score = Math.max(0, 100 - critical * 18 - Math.max(0, open - critical) * 7
                - failedPolicies * 5 - Math.min(20, warningEvents));
        Instant observed = events.stream().map(KubernetesEventSnapshot::collectedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(cluster.updatedAt());
        String posture = score >= 85 ? "STABLE" : score >= 65 ? "ATTENTION"
                : score >= 40 ? "DEGRADED" : "CRITICAL";
        return new ClusterHealth(cluster.id(), cluster.name(), cluster.status().name(), score, open, failedPolicies,
                warningEvents, observed, posture);
    }

    static CapacityPosture capacityPosture(List<PolicyEvaluation> evaluations) {
        int workloads = count(evaluations, item -> "WORKLOAD_AVAILABILITY".equals(item.policyId()));
        int unavailable = count(evaluations, item -> "WORKLOAD_AVAILABILITY".equals(item.policyId())
                && item.result() == PolicyResult.FAIL);
        int pods = count(evaluations, item -> "POD_UNHEALTHY".equals(item.policyId()));
        int unhealthyPods = count(evaluations, item -> "POD_UNHEALTHY".equals(item.policyId())
                && item.result() == PolicyResult.FAIL);
        int pendingPvcs = count(evaluations, item -> "PVC_PENDING".equals(item.policyId())
                && item.result() == PolicyResult.FAIL);
        int namespacesWithoutNetworkPolicy = count(evaluations,
                item -> "NETWORK_POLICY_MISSING".equals(item.policyId()) && item.result() == PolicyResult.WARN);
        int governanceGaps = count(evaluations,
                item -> "RESOURCE_GOVERNANCE_UNKNOWN".equals(item.policyId())
                        && item.result() == PolicyResult.NOT_APPLICABLE);
        String summary = unavailable + " unavailable workload, " + unhealthyPods + " unhealthy Pod, "
                + pendingPvcs + " pending PVC. 실제 사용률이 아닌 Kubernetes 구성 근거입니다.";
        return new CapacityPosture("CONFIGURATION_BASED", workloads, unavailable, pods, unhealthyPods,
                pendingPvcs, namespacesWithoutNetworkPolicy, governanceGaps, summary);
    }

    private static List<PriorityItem> priorityQueue(List<Incident> incidents, List<AsyncJob> jobs) {
        List<PriorityItem> result = incidents.stream()
                .filter(incident -> incident.state() != IncidentState.RESOLVED)
                .map(incident -> {
                    int score = severityScore(incident.severity()) + Math.min(20, incident.occurrenceCount() * 2)
                            + (incident.nextAction() == null ? 0 : 5);
                    return new PriorityItem(incident.id().toString(), "INCIDENT", urgency(score), score,
                            incident.severity(), incident.clusterId(), incident.clusterName(), incident.namespace(),
                            incident.title(), incident.summary(), incident.nextAction(), "/incidents/" + incident.id(),
                            incident.lastDetectedAt());
                }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        jobs.stream().filter(job -> EnumSet.of(AsyncJobStatus.FAILED, AsyncJobStatus.TIMEOUT).contains(job.status()))
                .limit(10).forEach(job -> result.add(new PriorityItem(job.id().toString(), "JOB", "오늘 확인", 45,
                        "MEDIUM", null, null, null, job.type().name() + " 작업 실패",
                        defaultText(job.errorMessage(), job.status().name()),
                        "실패 상세를 확인하고 재시도 가능 여부를 판단하세요.", "/",
                        job.completedAt() == null ? job.createdAt() : job.completedAt())));
        return result.stream().sorted(Comparator.comparingInt(PriorityItem::score).reversed()
                        .thenComparing(PriorityItem::detectedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20).toList();
    }

    private static int severityScore(String severity) {
        return switch (severity == null ? "" : severity.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "CRITICAL" -> 70;
            case "HIGH" -> 55;
            case "MEDIUM" -> 35;
            case "LOW" -> 20;
            default -> 10;
        };
    }

    private static String urgency(int score) {
        return score >= 65 ? "즉시 확인" : score >= 40 ? "오늘 확인" : "관찰";
    }

    private static <T> int count(List<T> values, java.util.function.Predicate<T> predicate) {
        return Math.toIntExact(values.stream().filter(predicate).count());
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
