package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AnalysisSessionRepositoryPort;
import io.strato.aiops.application.port.in.WatchSignalTriageUseCase;
import io.strato.aiops.application.port.out.AnalysisAssuranceRepositoryPort;
import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesResourceSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.application.port.out.SyncJobRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisSession;
import io.strato.aiops.domain.analysis.AnalysisStatus;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobStatus;
import io.strato.aiops.domain.operations.OperationsModels.AiQualitySummary;
import io.strato.aiops.domain.operations.OperationsModels.AiCalibrationSummary;
import io.strato.aiops.domain.operations.OperationsModels.AnalysisFeedback;
import io.strato.aiops.domain.operations.OperationsModels.CleanupPreview;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentActivity;
import io.strato.aiops.domain.operations.OperationsModels.IncidentDetail;
import io.strato.aiops.domain.operations.OperationsModels.IncidentEvidence;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import io.strato.aiops.domain.operations.OperationsModels.Notification;
import io.strato.aiops.domain.operations.OperationsModels.OperationSettings;
import io.strato.aiops.domain.operations.OperationsModels.OperationsOverview;
import io.strato.aiops.domain.operations.OperationsModels.OperationsScorecard;
import io.strato.aiops.domain.operations.OperationsModels.PolicyDefinition;
import io.strato.aiops.domain.operations.OperationsModels.PolicyEvaluation;
import io.strato.aiops.domain.operations.OperationsModels.ResourceChange;
import io.strato.aiops.domain.operations.OperationsModels.RunbookTemplate;
import io.strato.aiops.domain.operations.OperationsModels.TriageItem;
import io.strato.aiops.domain.operations.OperationsModels.TriageQueue;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignalGroup;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import io.strato.aiops.domain.sync.SyncJobStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class OperationsControlPlaneService implements WatchSignalTriageUseCase {

    private final OperationsRepositoryPort operationsRepository;
    private final ClusterRepositoryPort clusterRepository;
    private final AnalysisSessionRepositoryPort analysisRepository;
    private final AsyncJobRepositoryPort jobRepository;
    private final KubernetesResourceSnapshotRepositoryPort resourceRepository;
    private final SyncJobRepositoryPort syncJobRepository;
    private final AuditLogRepositoryPort auditRepository;
    private final IncidentIntelligenceService incidentIntelligenceService;
    private final AnalysisAssuranceRepositoryPort assuranceRepository;
    private final OperationsEventStream eventStream;
    private final IncidentStateTransitionPolicy incidentStateTransitionPolicy;
    private final OperationsPolicyEvaluator policyEvaluator;
    private final IncidentRecoveryCoordinator incidentRecoveryCoordinator;
    private final IncidentSignalCoordinator incidentSignalCoordinator;
    private final OperationsOverviewQueryService overviewQueryService;
    private final ResourceBaselineCoordinator baselineCoordinator;
    private final OperationsNotificationPublisher notificationPublisher;
    private final WatchSignalTriageService watchSignalTriageService;
    private final AiQualityQueryService aiQualityQueryService;
    private final OperationsScorecardQueryService scorecardQueryService;
    private volatile CachedOverview cachedOverview;

    public OperationsControlPlaneService(
            OperationsRepositoryPort operationsRepository,
            ClusterRepositoryPort clusterRepository,
            AnalysisSessionRepositoryPort analysisRepository,
            AsyncJobRepositoryPort jobRepository,
            KubernetesResourceSnapshotRepositoryPort resourceRepository,
            SyncJobRepositoryPort syncJobRepository,
            AuditLogRepositoryPort auditRepository,
            IncidentIntelligenceService incidentIntelligenceService,
            AnalysisAssuranceRepositoryPort assuranceRepository,
            OperationsEventStream eventStream,
            IncidentStateTransitionPolicy incidentStateTransitionPolicy,
            OperationsPolicyEvaluator policyEvaluator,
            IncidentRecoveryCoordinator incidentRecoveryCoordinator,
            IncidentSignalCoordinator incidentSignalCoordinator,
            OperationsOverviewQueryService overviewQueryService,
            ResourceBaselineCoordinator baselineCoordinator,
            OperationsNotificationPublisher notificationPublisher,
            WatchSignalTriageService watchSignalTriageService,
            AiQualityQueryService aiQualityQueryService,
            OperationsScorecardQueryService scorecardQueryService
    ) {
        this.operationsRepository = operationsRepository;
        this.clusterRepository = clusterRepository;
        this.analysisRepository = analysisRepository;
        this.jobRepository = jobRepository;
        this.resourceRepository = resourceRepository;
        this.syncJobRepository = syncJobRepository;
        this.auditRepository = auditRepository;
        this.incidentIntelligenceService = incidentIntelligenceService;
        this.assuranceRepository = assuranceRepository;
        this.eventStream = eventStream;
        this.incidentStateTransitionPolicy = incidentStateTransitionPolicy;
        this.policyEvaluator = policyEvaluator;
        this.incidentRecoveryCoordinator = incidentRecoveryCoordinator;
        this.incidentSignalCoordinator = incidentSignalCoordinator;
        this.overviewQueryService = overviewQueryService;
        this.baselineCoordinator = baselineCoordinator;
        this.notificationPublisher = notificationPublisher;
        this.watchSignalTriageService = watchSignalTriageService;
        this.aiQualityQueryService = aiQualityQueryService;
        this.scorecardQueryService = scorecardQueryService;
    }

    public OperationsOverview getOverview() {
        CachedOverview cache = cachedOverview;
        if (cache != null && cache.createdAt().isAfter(Instant.now().minusSeconds(15))) {
            return cache.overview();
        }
        OperationsOverview overview = overviewQueryService.build(getAiQuality());
        cachedOverview = new CachedOverview(overview, Instant.now());
        return overview;
    }

    @Transactional
    public OperationsOverview reconcile(String actor, String requestId) {
        for (Cluster cluster : clusterRepository.findAll()) {
            reconcileCluster(cluster, actor);
        }
        createRuntimeNotifications();
        audit("OPERATIONS_RECONCILED", "OPERATIONS", "all", actor, requestId);
        invalidateOverview();
        return getOverview();
    }

    public List<Incident> listIncidents(UUID clusterId, String namespace, String state, String severity, int limit) {
        return operationsRepository.findIncidents(clusterId, namespace, normalizeUpper(state), normalizeUpper(severity), limit);
    }

    public IncidentDetail getIncident(UUID incidentId) {
        Incident incident = operationsRepository.findIncidentById(incidentId)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + incidentId));
        List<IncidentEvidence> evidence = operationsRepository.findIncidentEvidence(incidentId);
        return new IncidentDetail(incident, evidence, operationsRepository.findIncidentActivities(incidentId),
                incidentRecoveryCoordinator.view(incident), incidentIntelligenceService.build(incident, evidence));
    }

    @Transactional
    public Incident updateIncidentState(UUID incidentId, IncidentState nextState, String note, String actor, String requestId) {
        Incident current = operationsRepository.findIncidentById(incidentId)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + incidentId));
        incidentStateTransitionPolicy.validate(current.state(), nextState);
        Incident updated = new Incident(current.id(), current.fingerprint(), current.clusterId(), current.clusterName(),
                current.namespace(), current.resourceKind(), current.resourceName(), current.category(), current.severity(),
                nextState, current.title(), current.summary(), current.nextAction(), current.occurrenceCount(),
                current.reopenCount(), current.sourceAnalysisId(), current.firstDetectedAt(), current.lastDetectedAt(), actor);
        Incident saved = operationsRepository.saveIncident(updated);
        operationsRepository.saveIncidentActivity(new IncidentActivity(UUID.randomUUID(), incidentId, "STATE_CHANGED",
                current.state(), nextState, cleanText(note, 2000), actor, Instant.now()));
        audit("INCIDENT_STATE_CHANGED", "INCIDENT", incidentId.toString(), actor, requestId);
        invalidateOverview();
        eventStream.publish("incident", saved);
        return saved;
    }

    public List<Notification> listNotifications(boolean unreadOnly, int limit) {
        return operationsRepository.findNotifications(unreadOnly, limit);
    }

    public long unreadNotificationCount() {
        return operationsRepository.countUnreadNotifications();
    }

    @Transactional
    public void markNotificationRead(UUID notificationId, String actor, String requestId) {
        operationsRepository.markNotificationRead(notificationId);
        audit("NOTIFICATION_READ", "NOTIFICATION", notificationId.toString(), actor, requestId);
        invalidateOverview();
    }

    @Transactional
    public void markAllNotificationsRead(String actor, String requestId) {
        operationsRepository.markAllNotificationsRead();
        audit("NOTIFICATIONS_READ_ALL", "NOTIFICATION", "all", actor, requestId);
        invalidateOverview();
    }

    public List<PolicyDefinition> listPolicies() {
        return operationsRepository.findPolicyDefinitions();
    }

    @Transactional
    public PolicyDefinition updatePolicy(String policyId, boolean enabled, String severity, String actor, String requestId) {
        PolicyDefinition current = operationsRepository.findPolicyDefinition(policyId)
                .orElseThrow(() -> new NoSuchElementException("Policy not found: " + policyId));
        String normalizedSeverity = requireSeverity(severity);
        PolicyDefinition saved = operationsRepository.savePolicyDefinition(new PolicyDefinition(current.id(), current.name(),
                current.description(), current.category(), normalizedSeverity, enabled));
        audit("POLICY_UPDATED", "POLICY", policyId, actor, requestId);
        invalidateOverview();
        return saved;
    }

    @Transactional
    public List<PolicyEvaluation> evaluatePolicies(UUID clusterId, String actor, String requestId) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
        List<KubernetesResourceSnapshot> resources = currentResourceInventory(clusterId);
        List<PolicyEvaluation> evaluations = policyEvaluator.evaluate(
                cluster, resources, operationsRepository.findPolicyDefinitions());
        operationsRepository.replacePolicyEvaluations(clusterId, evaluations);
        baselineCoordinator.reconcile(cluster, resources);
        audit("POLICIES_EVALUATED", "CLUSTER", clusterId.toString(), actor, requestId);
        invalidateOverview();
        return operationsRepository.findPolicyEvaluations(clusterId, null, null, 500);
    }

    public List<PolicyEvaluation> listPolicyEvaluations(UUID clusterId, String namespace, String result, int limit) {
        return operationsRepository.findPolicyEvaluations(clusterId, namespace, normalizeUpper(result), limit);
    }

    public List<ResourceChange> listChanges(UUID clusterId, String namespace, int limit) {
        return operationsRepository.findResourceChanges(clusterId, namespace, limit);
    }

    public ResourceChange getChange(UUID changeId) {
        return operationsRepository.findResourceChangeById(changeId)
                .orElseThrow(() -> new NoSuchElementException("Resource change not found: " + changeId));
    }

    public List<RunbookTemplate> listRunbooks(String signal, String category) {
        return operationsRepository.findRunbooks(signal, category);
    }

    public RunbookTemplate getRunbook(String runbookId) {
        return operationsRepository.findRunbookById(runbookId)
                .orElseThrow(() -> new NoSuchElementException("Runbook not found: " + runbookId));
    }

    public List<RunbookTemplate> matchRunbooks(String signal, String category, String resourceKind) {
        String normalizedSignal = normalizeToken(signal);
        return operationsRepository.findRunbooks(null, null).stream()
                .filter(runbook -> normalizedSignal == null
                        || normalizeToken(runbook.signal()).contains(normalizedSignal)
                        || normalizedSignal.contains(normalizeToken(runbook.signal())))
                .filter(runbook -> category == null || category.isBlank() || runbook.category().equalsIgnoreCase(category))
                .filter(runbook -> resourceKind == null || resourceKind.isBlank() || runbook.resourceKind() == null
                        || runbook.resourceKind().equalsIgnoreCase(resourceKind))
                .limit(10)
                .toList();
    }

    public AnalysisFeedback getFeedback(UUID analysisId) {
        analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found: " + analysisId));
        return operationsRepository.findAnalysisFeedback(analysisId).orElse(null);
    }

    @Transactional
    public AnalysisFeedback saveFeedback(UUID analysisId, String accuracy, String outcome, boolean dangerous,
                                         String comment, String actualRootCause, String actualResolution,
                                         String validatedResourceKind, String validatedResourceName,
                                         String confidenceExpectation, String actor, String requestId) {
        analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found: " + analysisId));
        String normalizedAccuracy = requireOneOf(accuracy, Set.of("CORRECT", "PARTIAL", "INCORRECT"), "accuracy");
        String normalizedOutcome = requireOneOf(outcome,
                Set.of("RESOLVED", "IMPROVED", "NO_CHANGE", "WORSE", "NOT_TRIED"), "outcome");
        AnalysisFeedback saved = operationsRepository.saveAnalysisFeedback(new AnalysisFeedback(analysisId,
                normalizedAccuracy, normalizedOutcome, dangerous, cleanText(comment, 2000),
                cleanText(actualRootCause, 2000), cleanText(actualResolution, 2000),
                cleanText(validatedResourceKind, 100), cleanText(validatedResourceName, 255),
                normalizeOptional(confidenceExpectation, Set.of("LOW", "MEDIUM", "HIGH")),
                actor, Instant.now()));
        audit("ANALYSIS_FEEDBACK_SAVED", "ANALYSIS", analysisId.toString(), actor, requestId);
        invalidateOverview();
        return saved;
    }

    public AiQualitySummary getAiQuality() {
        return aiQualityQueryService.getQuality();
    }

    public AiCalibrationSummary getAiCalibration() {
        return aiQualityQueryService.getCalibration();
    }

    @Override
    @Transactional
    public void ingestWatchSignal(WatchSignal signal) {
        watchSignalTriageService.ingestWatchSignal(signal);
        invalidateOverview();
    }

    public TriageQueue getTriageQueue(UUID clusterId, String namespace, String state, int limit) {
        return watchSignalTriageService.getQueue(clusterId, namespace, state, limit);
    }

    @Transactional
    public WatchSignalGroup updateSignalGroupState(UUID groupId, String requestedState, String actor, String requestId) {
        WatchSignalGroup saved = watchSignalTriageService.updateState(groupId, requestedState, actor, requestId);
        invalidateOverview();
        return saved;
    }

    public OperationsScorecard getScorecard() {
        return scorecardQueryService.getScorecard();
    }

    public OperationSettings getSettings() {
        return operationsRepository.getOperationSettings();
    }

    @Transactional
    public OperationSettings updateSettings(OperationSettings requested, String actor, String requestId) {
        OperationSettings validated = new OperationSettings(
                range(requested.eventRetentionDays(), 1, 365, "eventRetentionDays"),
                range(requested.analysisRetentionDays(), 7, 730, "analysisRetentionDays"),
                range(requested.jobRetentionDays(), 1, 365, "jobRetentionDays"),
                range(requested.notificationRetentionDays(), 1, 365, "notificationRetentionDays"),
                range(requested.resolvedIncidentRetentionDays(), 7, 730, "resolvedIncidentRetentionDays"),
                range(requested.changeRetentionDays(), 1, 365, "changeRetentionDays"),
                range(requested.auditRetentionDays(), 30, 2555, "auditRetentionDays"),
                range(requested.commandRetentionDays(), 7, 730, "commandRetentionDays"),
                range(requested.notificationSuppressMinutes(), 1, 1440, "notificationSuppressMinutes"),
                range(requested.staleSyncMinutes(), 5, 1440, "staleSyncMinutes"),
                range(requested.longRunningJobSeconds(), 30, 3600, "longRunningJobSeconds"),
                actor, Instant.now());
        OperationSettings saved = operationsRepository.saveOperationSettings(validated);
        audit("OPERATION_SETTINGS_UPDATED", "SETTINGS", "operations", actor, requestId);
        invalidateOverview();
        return saved;
    }

    public CleanupPreview previewCleanup() {
        return operationsRepository.cleanup(getSettings(), false);
    }

    @Transactional
    public CleanupPreview executeCleanup(String actor, String requestId) {
        CleanupPreview result = operationsRepository.cleanup(getSettings(), true);
        audit("OPERATION_DATA_CLEANED", "SETTINGS", "operations", actor, requestId);
        invalidateOverview();
        return result;
    }

    public List<AuditLog> listAuditLogs(String actor, String action, String targetType, String targetId,
                                        String requestId, Instant from, Instant to, int limit) {
        return operationsRepository.findAuditLogs(actor, action, targetType, targetId, requestId, from, to, limit);
    }

    private void reconcileCluster(Cluster cluster, String actor) {
        List<KubernetesResourceSnapshot> resources = currentResourceInventory(cluster.id());
        List<PolicyEvaluation> evaluations = policyEvaluator.evaluate(
                cluster, resources, operationsRepository.findPolicyDefinitions());
        operationsRepository.replacePolicyEvaluations(cluster.id(), evaluations);
        baselineCoordinator.reconcile(cluster, resources);
        incidentSignalCoordinator.reconcile(cluster, actor).forEach(notification ->
                createNotification(notification.type(), notification.severity(), notification.title(),
                        notification.message(), notification.targetPath(), notification.dedupKey()));
        incidentRecoveryCoordinator.reconcile(cluster, resources, actor).forEach(notification ->
                createNotification(notification.type(), notification.severity(), notification.title(),
                        notification.message(), notification.targetPath(), notification.dedupKey()));
    }

   private List<KubernetesResourceSnapshot> currentResourceInventory(UUID clusterId) {
        return syncJobRepository.findLatestByClusterIdAndStatusIn(clusterId, EnumSet.of(SyncJobStatus.SUCCEEDED))
                .map(syncJob -> {
                    int pageNumber = 0;
                    int totalPages;
                    List<KubernetesResourceSnapshot> inventory = new ArrayList<>();
                    do {
                        var page = resourceRepository.findPageBySyncJobId(syncJob.id(), null, null, pageNumber, 200);
                        inventory.addAll(page.items());
                        totalPages = Math.min(page.totalPages(), 50);
                        pageNumber++;
                    } while (pageNumber < totalPages);
                    return List.copyOf(inventory);
                })
                .orElseGet(List::of);
    }

  private void createRuntimeNotifications() {
        OperationSettings settings = getSettings();
        Instant longRunningCutoff = Instant.now().minusSeconds(settings.longRunningJobSeconds());
        for (AsyncJob job : jobRepository.findRecent(100)) {
            if (EnumSet.of(AsyncJobStatus.FAILED, AsyncJobStatus.TIMEOUT).contains(job.status())) {
                createNotification("JOB_FAILED", "MEDIUM", job.type().name() + " 작업 실패",
                        defaultText(job.errorMessage(), job.status().name()), "/", "job:" + job.id());
            } else if (job.status() == AsyncJobStatus.RUNNING && job.startedAt() != null
                    && job.startedAt().isBefore(longRunningCutoff)) {
                createNotification("JOB_LONG_RUNNING", "LOW", job.type().name() + " 작업 지연",
                        "설정된 장시간 기준을 초과했습니다.", "/", "job-long:" + job.id());
            }
        }
    }

    private void createNotification(String type, String severity, String title, String message, String targetPath,
                                    String sourceKey) {
        notificationPublisher.publish(type, severity, title, message, targetPath, sourceKey);
    }

   private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

   private String normalizeUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSeverity(String value) {
        String normalized = normalizeUpper(value);
        return normalized != null && Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO").contains(normalized)
                ? normalized : "MEDIUM";
    }

    private String requireSeverity(String value) {
        String normalized = normalizeUpper(value);
        if (normalized == null || !Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported severity: " + value);
        }
        return normalized;
    }

    private String requireOneOf(String value, Set<String> allowed, String field) {
        String normalized = normalizeUpper(value);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(field + " must be one of " + allowed);
        }
        return normalized;
    }

    private String normalizeOptional(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) return null;
        return requireOneOf(value, allowed, "confidenceExpectation");
    }

    private String urgency(int score) {
        return score >= 65 ? "즉시 확인" : score >= 40 ? "오늘 확인" : "관찰";
    }

    private String cleanText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?i)(token|password|secret)\\s*[=:]\\s*[^\\s,]+", "$1=***");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int range(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
        return value;
    }

    private void audit(String action, String targetType, String targetId, String actor, String requestId) {
        auditRepository.save(AuditLog.create(action, targetType, targetId, actor, requestId));
    }

    private void invalidateOverview() {
        cachedOverview = null;
    }

    private record CachedOverview(OperationsOverview overview, Instant createdAt) {
    }

}
