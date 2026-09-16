package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.in.AnalysisUseCase;
import io.strato.aiops.application.port.in.AnalysisCommandExecuteCommand;
import io.strato.aiops.application.port.in.AnalysisCommandPreviewResult;
import io.strato.aiops.application.port.in.AnalysisCommandUseCase;
import io.strato.aiops.application.port.in.AnalyzeApplicationCommand;
import io.strato.aiops.application.port.in.AnalyzeClusterCommand;
import io.strato.aiops.application.port.in.AnalyzeNamespaceCommand;
import io.strato.aiops.application.port.in.GetNamespaceDiagnosticsUseCase;
import io.strato.aiops.application.port.in.GetPodLogsUseCase;
import io.strato.aiops.application.port.in.NamespaceDiagnosticsResult;
import io.strato.aiops.application.port.in.PodLogsResult;
import io.strato.aiops.application.port.in.StartAnalysisJobResult;
import io.strato.aiops.application.port.in.UpdateAnalysisWorkflowStateCommand;
import io.strato.aiops.application.port.out.AnalysisCommandExecutionRepositoryPort;
import io.strato.aiops.application.port.out.AnalysisExecutorPort;
import io.strato.aiops.application.port.out.AnalysisSessionRepositoryPort;
import io.strato.aiops.application.port.out.AnalysisWorkflowStateRepositoryPort;
import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesEventSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnosticsPort;
import io.strato.aiops.application.port.out.KubernetesPodLogs;
import io.strato.aiops.application.port.out.KubernetesMutationPort;
import io.strato.aiops.application.port.out.KubernetesResourceSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesStateInventory;
import io.strato.aiops.application.port.out.KubernetesStateSyncPort;
import io.strato.aiops.application.port.out.ManagedApplicationRepositoryPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.application.service.KubernetesPortTopologyAnalyzer.PortMismatchSignal;
import io.strato.aiops.domain.analysis.AnalysisCommandExecution;
import io.strato.aiops.domain.analysis.AnalysisCommandSafety;
import io.strato.aiops.domain.analysis.AnalysisSession;
import io.strato.aiops.domain.analysis.SupportedLocale;
import io.strato.aiops.domain.application.ManagedApplication;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.analysis.AnalysisWorkflowState;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobStatus;
import io.strato.aiops.domain.job.AsyncJobType;
import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.Locale;

@Service
public class AnalysisApplicationService implements AnalysisUseCase, GetNamespaceDiagnosticsUseCase, GetPodLogsUseCase,
        AnalysisCommandUseCase {

    private static final AnalysisCommandPolicy COMMAND_POLICY = new AnalysisCommandPolicy();
    private static final AnalysisCommandParser COMMAND_PARSER = new AnalysisCommandParser();

    private static final int ANALYSIS_CONTEXT_RESOURCE_LIMIT = 100;
    private static final int ANALYSIS_CONTEXT_EVENT_LIMIT = 100;
    private static final int ANALYSIS_HISTORY_LIMIT = 100;
    private static final int LIVE_RESOURCE_CONTEXT_LIMIT = 80;
    private static final int LIVE_EVENT_CONTEXT_LIMIT = 50;
    private static final int POD_LOG_CONTEXT_LIMIT = 8;
    private static final int STORED_RESOURCE_CONTEXT_LIMIT = 40;
    private static final int STORED_EVENT_CONTEXT_LIMIT = 40;
    private static final int CLUSTER_RESOURCE_CONTEXT_LIMIT = 60;
    private static final int CLUSTER_EVENT_CONTEXT_LIMIT = 40;
    private static final int CLUSTER_ANALYSIS_CONTEXT_CHAR_LIMIT = 30000;
    private static final int CLUSTER_SECTION_CONTEXT_CHAR_LIMIT = 7000;
    private static final int NANOS_PER_MILLI = 1_000_000;
    private static final Duration PENDING_ANALYSIS_RESUBMIT_THRESHOLD = Duration.ofSeconds(5);

    private final ClusterRepositoryPort clusterRepositoryPort;
    private final ClusterCredentialRepositoryPort clusterCredentialRepositoryPort;
    private final ManagedApplicationRepositoryPort applicationRepositoryPort;
    private final KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort;
    private final KubernetesEventSnapshotRepositoryPort eventSnapshotRepositoryPort;
    private final KubernetesNamespaceDiagnosticsPort kubernetesNamespaceDiagnosticsPort;
    private final KubernetesMutationPort kubernetesMutationPort;
    private final KubernetesStateSyncPort kubernetesStateSyncPort;
    private final AsyncJobRepositoryPort asyncJobRepositoryPort;
    private final AnalysisExecutorPort analysisExecutorPort;
    private final AnalysisSessionRepositoryPort analysisSessionRepositoryPort;
    private final AnalysisCommandExecutionRepositoryPort analysisCommandExecutionRepositoryPort;
    private final AnalysisWorkflowStateRepositoryPort analysisWorkflowStateRepositoryPort;
    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final SecretCryptoPort secretCryptoPort;
    private final ObjectMapper objectMapper;
    private final int maxAnalysisContextChars;
    private final NamespaceAnalysisContextBuilder namespaceAnalysisContextBuilder;
    private final AnalysisResultAssembler analysisResultAssembler;
    private final AnalysisSectionExecutor sectionExecutor;
    private final AnalysisComparisonService analysisComparisonService;
    private final AnalysisCommandExecutor analysisCommandExecutor;
    private final DeterministicRiskTimelineSectionBuilder deterministicRiskTimelineSectionBuilder;
    private final DeterministicPerformanceScalingSectionBuilder deterministicPerformanceScalingSectionBuilder;
    private final KubernetesPerformanceSignalPolicy kubernetesPerformanceSignalPolicy;
    private final KubernetesPortTopologyAnalyzer kubernetesPortTopologyAnalyzer;
    private final AnalysisCollectionDiagnosticsWriter collectionDiagnosticsWriter;
    private final AnalysisLocalePolicy localePolicy;
    private final KubernetesCurrentStateAnalysisGuard currentStateAnalysisGuard;
    private final AnalysisCommandEvidenceMerger commandEvidenceMerger;

    /** AnalysisApplicationService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AnalysisApplicationService(ClusterRepositoryPort clusterRepositoryPort,
                                      ClusterCredentialRepositoryPort clusterCredentialRepositoryPort,
                                      ManagedApplicationRepositoryPort applicationRepositoryPort,
                                      KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort,
                                      KubernetesEventSnapshotRepositoryPort eventSnapshotRepositoryPort,
                                      KubernetesNamespaceDiagnosticsPort kubernetesNamespaceDiagnosticsPort,
                                      KubernetesMutationPort kubernetesMutationPort,
                                      KubernetesStateSyncPort kubernetesStateSyncPort,
                                      AsyncJobRepositoryPort asyncJobRepositoryPort,
                                      AnalysisExecutorPort analysisExecutorPort,
                                      AnalysisSessionRepositoryPort analysisSessionRepositoryPort,
                                      AnalysisCommandExecutionRepositoryPort analysisCommandExecutionRepositoryPort,
                                      AnalysisWorkflowStateRepositoryPort analysisWorkflowStateRepositoryPort,
                                      AuditLogRepositoryPort auditLogRepositoryPort,
                                      SecretCryptoPort secretCryptoPort,
                                      ObjectMapper objectMapper,
                                      @Value("${aiops.ai.analysis.max-context-chars:60000}") int maxAnalysisContextChars,
                                      NamespaceAnalysisContextBuilder namespaceAnalysisContextBuilder,
                                      AnalysisResultAssembler analysisResultAssembler,
                                      AnalysisSectionExecutor sectionExecutor,
                                      AnalysisComparisonService analysisComparisonService,
                                      DeterministicRiskTimelineSectionBuilder deterministicRiskTimelineSectionBuilder,
                                      DeterministicPerformanceScalingSectionBuilder deterministicPerformanceScalingSectionBuilder,
                                      KubernetesPerformanceSignalPolicy kubernetesPerformanceSignalPolicy,
                                      KubernetesPortTopologyAnalyzer kubernetesPortTopologyAnalyzer,
                                      AnalysisCollectionDiagnosticsWriter collectionDiagnosticsWriter,
                                      AnalysisLocalePolicy localePolicy,
                                      KubernetesCurrentStateAnalysisGuard currentStateAnalysisGuard) {
        this.clusterRepositoryPort = clusterRepositoryPort;
        this.clusterCredentialRepositoryPort = clusterCredentialRepositoryPort;
        this.applicationRepositoryPort = applicationRepositoryPort;
        this.resourceSnapshotRepositoryPort = resourceSnapshotRepositoryPort;
        this.eventSnapshotRepositoryPort = eventSnapshotRepositoryPort;
        this.kubernetesNamespaceDiagnosticsPort = kubernetesNamespaceDiagnosticsPort;
        this.kubernetesMutationPort = kubernetesMutationPort;
        this.kubernetesStateSyncPort = kubernetesStateSyncPort;
        this.asyncJobRepositoryPort = asyncJobRepositoryPort;
        this.analysisExecutorPort = analysisExecutorPort;
        this.analysisSessionRepositoryPort = analysisSessionRepositoryPort;
        this.analysisCommandExecutionRepositoryPort = analysisCommandExecutionRepositoryPort;
        this.analysisWorkflowStateRepositoryPort = analysisWorkflowStateRepositoryPort;
        this.auditLogRepositoryPort = auditLogRepositoryPort;
        this.secretCryptoPort = secretCryptoPort;
        this.objectMapper = objectMapper;
        this.maxAnalysisContextChars = maxAnalysisContextChars;
        this.namespaceAnalysisContextBuilder = namespaceAnalysisContextBuilder;
        this.analysisResultAssembler = analysisResultAssembler;
        this.sectionExecutor = sectionExecutor;
        this.analysisComparisonService = analysisComparisonService;
        this.deterministicRiskTimelineSectionBuilder = deterministicRiskTimelineSectionBuilder;
        this.deterministicPerformanceScalingSectionBuilder = deterministicPerformanceScalingSectionBuilder;
        this.kubernetesPerformanceSignalPolicy = kubernetesPerformanceSignalPolicy;
        this.kubernetesPortTopologyAnalyzer = kubernetesPortTopologyAnalyzer;
        this.collectionDiagnosticsWriter = collectionDiagnosticsWriter;
        this.localePolicy = localePolicy;
        this.currentStateAnalysisGuard = currentStateAnalysisGuard;
        this.commandEvidenceMerger = new AnalysisCommandEvidenceMerger(objectMapper);
        this.analysisCommandExecutor = new AnalysisCommandExecutor(kubernetesMutationPort,
                kubernetesNamespaceDiagnosticsPort, this::connectionCredential, this::collectNamespaceDiagnostics);
    }

    /** AnalysisApplicationService의 analyzeApplication 처리의 핵심 작업 흐름을 실행한다. */
    @Override
    @Transactional
    public AnalysisSession analyzeApplication(AnalyzeApplicationCommand command, String actor, String requestId) {
        ManagedApplication application = applicationRepositoryPort.findById(command.applicationId())
                .orElseThrow(() -> new NoSuchElementException("Application not found: " + command.applicationId()));
        String resultJson = withAnalysisComparison(
                analyzeNamespaceWithFallback(application.clusterId(), application.namespace(), application.name(), command.locale()),
                application.clusterId(),
                application.id(),
                application.namespace()
        );
        resultJson = localePolicy.attachLocale(resultJson, command.locale());
        AnalysisSession analysisSession = analysisSessionRepositoryPort.save(AnalysisSession.succeeded(
                application.clusterId(), application.id(), application.namespace(), resultJson,
                resultSummary(resultJson), schemaVersion(resultJson), command.locale(), actor));
        audit("AI_APPLICATION_ANALYSIS_COMPLETED", analysisSession.id(), actor, requestId);
        return analysisSession;
    }

    /** AnalysisApplicationService의 analyzeNamespace 처리의 핵심 작업 흐름을 실행한다. */
    @Override
    @Transactional
    public AnalysisSession analyzeNamespace(AnalyzeNamespaceCommand command, String actor, String requestId) {
        requireCluster(command.clusterId());
        String resultJson = withAnalysisComparison(
                analyzeNamespaceWithFallback(command.clusterId(), command.namespace(), null, command.locale()),
                command.clusterId(),
                null,
                command.namespace()
        );
        resultJson = localePolicy.attachLocale(resultJson, command.locale());
        AnalysisSession analysisSession = analysisSessionRepositoryPort.save(AnalysisSession.succeeded(
                command.clusterId(), null, command.namespace(), resultJson,
                resultSummary(resultJson), schemaVersion(resultJson), command.locale(), actor));
        audit("AI_NAMESPACE_ANALYSIS_COMPLETED", analysisSession.id(), actor, requestId);
        return analysisSession;
    }

    /** AnalysisApplicationService의 analyzeCluster 처리의 핵심 작업 흐름을 실행한다. */
    @Override
    @Transactional
    public AnalysisSession analyzeCluster(AnalyzeClusterCommand command, String actor, String requestId) {
        Cluster cluster = requireCluster(command.clusterId());
        ClusterAnalysisContext analysisContext = buildClusterContext(cluster);
        String resultJson = withAnalysisComparison(
                analyzeClusterWithFallback(cluster, analysisContext, command.locale()),
                command.clusterId(),
                null,
                null
        );
        resultJson = localePolicy.attachLocale(resultJson, command.locale());
        AnalysisSession analysisSession = analysisSessionRepositoryPort.save(AnalysisSession.succeeded(
                command.clusterId(), null, null, resultJson,
                resultSummary(resultJson), schemaVersion(resultJson), command.locale(), actor));
        audit("AI_CLUSTER_ANALYSIS_COMPLETED", analysisSession.id(), actor, requestId);
        return analysisSession;
    }

    /** AnalysisApplicationService의 startApplicationAnalysis 처리에 필요한 업무 로직을 수행한다. */
    @Override
    @Transactional
    public StartAnalysisJobResult startApplicationAnalysis(AnalyzeApplicationCommand command, String actor, String requestId) {
        ManagedApplication application = applicationRepositoryPort.findById(command.applicationId())
                .orElseThrow(() -> new NoSuchElementException("Application not found: " + command.applicationId()));
        return startAnalysisJob(application.clusterId(), application.id(), application.namespace(), command.locale(), actor, requestId);
    }

    /** AnalysisApplicationService의 startNamespaceAnalysis 처리에 필요한 업무 로직을 수행한다. */
    @Override
    @Transactional
    public StartAnalysisJobResult startNamespaceAnalysis(AnalyzeNamespaceCommand command, String actor, String requestId) {
        requireCluster(command.clusterId());
        return startAnalysisJob(command.clusterId(), null, command.namespace(), command.locale(), actor, requestId);
    }

    /** AnalysisApplicationService의 startClusterAnalysis 처리에 필요한 업무 로직을 수행한다. */
    @Override
    @Transactional
    public StartAnalysisJobResult startClusterAnalysis(AnalyzeClusterCommand command, String actor, String requestId) {
        requireCluster(command.clusterId());
        return startAnalysisJob(command.clusterId(), null, null, command.locale(), actor, requestId);
    }

    /** AnalysisApplicationService의 retryAnalysis 처리에 필요한 업무 로직을 수행한다. */
    @Override
    @Transactional
    public StartAnalysisJobResult retryAnalysis(UUID analysisId, String actor, String requestId) {
        AnalysisSession source = analysisSessionRepositoryPort.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found: " + analysisId));
        StartAnalysisJobResult result = startAnalysisJob(source.clusterId(), source.applicationId(), source.namespace(),
                SupportedLocale.fromAcceptLanguage(source.locale()), actor, requestId);
        auditLogRepositoryPort.save(AuditLog.create(
                "AI_ANALYSIS_JOB_RETRIED",
                "ANALYSIS",
                analysisId.toString(),
                actor,
                result.jobId().toString()
        ));
        return result;
    }

    /** AnalysisApplicationService의 runAnalysisJob 처리의 핵심 작업 흐름을 실행한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runAnalysisJob(UUID asyncJobId) {
        AsyncJob asyncJob = asyncJobRepositoryPort.findById(asyncJobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + asyncJobId));
        AnalysisSession analysisSession = analysisSessionRepositoryPort.findByAsyncJobId(asyncJobId)
                .orElseThrow(() -> new NoSuchElementException("Analysis session not found for job: " + asyncJobId));
        if (asyncJob.status() != AsyncJobStatus.PENDING) {
            return;
        }

        asyncJob.markRunning(Instant.now());
        asyncJob = asyncJobRepositoryPort.save(asyncJob);
        try {
            SupportedLocale locale = SupportedLocale.fromAcceptLanguage(analysisSession.locale());
            String resultJson = withAnalysisComparison(
                    executePendingAnalysis(analysisSession),
                    analysisSession.clusterId(),
                    analysisSession.applicationId(),
                    analysisSession.namespace()
            );
            resultJson = localePolicy.attachLocale(resultJson, locale);
            AnalysisSession completed = analysisSession.succeeded(
                    resultJson,
                    resultSummary(resultJson),
                    schemaVersion(resultJson)
            );
            if (isJobCanceled(asyncJobId)) {
                analysisSessionRepositoryPort.save(analysisSession.failed(
                        failedAnalysisJson(new IllegalStateException("AI analysis job was canceled")),
                        "AI analysis canceled"
                ));
                return;
            }
            analysisSessionRepositoryPort.save(completed);
            asyncJob.markSucceeded(Instant.now());
            asyncJobRepositoryPort.save(asyncJob);
            auditLogRepositoryPort.save(AuditLog.create(
                    "AI_ANALYSIS_JOB_COMPLETED",
                    "ANALYSIS",
                    analysisSession.id().toString(),
                    analysisSession.createdBy(),
                    asyncJobId.toString()
            ));
        } catch (RuntimeException exception) {
            AnalysisSession failed = analysisSession.failed(
                    failedAnalysisJson(exception),
                    "AI analysis failed: " + truncate(exception.getMessage(), 300)
            );
            analysisSessionRepositoryPort.save(failed);
            if (asyncJob.status() == AsyncJobStatus.RUNNING) {
                asyncJob.markFailed(Instant.now(), errorCode(exception), truncate(exception.getMessage(), 1000));
                asyncJobRepositoryPort.save(asyncJob);
            }
            auditLogRepositoryPort.save(AuditLog.create(
                    "AI_ANALYSIS_JOB_FAILED",
                    "ANALYSIS",
                    analysisSession.id().toString(),
                    analysisSession.createdBy(),
                    asyncJobId.toString()
            ));
        }
    }

    /** AnalysisApplicationService의 isJobCanceled 처리 조건의 충족 여부를 판단한다. */
    private boolean isJobCanceled(UUID asyncJobId) {
        return asyncJobRepositoryPort.findById(asyncJobId)
                .map(job -> job.status() == AsyncJobStatus.CANCELED)
                .orElse(false);
    }

    /** AnalysisApplicationService의 startAnalysisJob 처리에 필요한 업무 로직을 수행한다. */
    private StartAnalysisJobResult startAnalysisJob(UUID clusterId, UUID applicationId, String namespace,
                                                     SupportedLocale locale, String actor, String requestId) {
        clusterRepositoryPort.lockById(clusterId);
        var running = analysisSessionRepositoryPort.findRunningByScope(clusterId, applicationId, namespace);
        if (running.isPresent() && running.get().asyncJobId() != null
                && locale.tag().equals(running.get().locale())) {
            AnalysisSession analysisSession = running.get();
            resubmitPendingAnalysisJob(analysisSession.asyncJobId(), analysisSession.id(), actor, requestId);
            auditLogRepositoryPort.save(AuditLog.create(
                    "AI_ANALYSIS_JOB_REUSED",
                    "ANALYSIS",
                    analysisSession.id().toString(),
                    actor,
                    requestId
            ));
            return new StartAnalysisJobResult(analysisSession.asyncJobId(), analysisSession.id());
        }

        AsyncJob job = asyncJobRepositoryPort.save(AsyncJob.pending(AsyncJobType.AI_ANALYSIS));
        AnalysisSession analysisSession = analysisSessionRepositoryPort.save(AnalysisSession.running(
                job.id(), clusterId, applicationId, namespace, locale, actor));
        auditLogRepositoryPort.save(AuditLog.create(
                "AI_ANALYSIS_JOB_REQUESTED",
                "ANALYSIS",
                analysisSession.id().toString(),
                actor,
                requestId
        ));
        submitAnalysisAfterCommit(job.id());
        return new StartAnalysisJobResult(job.id(), analysisSession.id());
    }

    /** AnalysisApplicationService의 resubmitPendingAnalysisJob 처리에 필요한 업무 로직을 수행한다. */
    private void resubmitPendingAnalysisJob(UUID jobId, UUID analysisId, String actor, String requestId) {
        asyncJobRepositoryPort.findById(jobId)
                .filter(job -> job.status() == AsyncJobStatus.PENDING
                        && job.createdAt().isBefore(Instant.now().minus(PENDING_ANALYSIS_RESUBMIT_THRESHOLD)))
                .ifPresent(job -> {
                    submitAnalysisAfterCommit(job.id());
                    auditLogRepositoryPort.save(AuditLog.create(
                            "AI_ANALYSIS_JOB_RESUBMITTED",
                            "ANALYSIS",
                            analysisId.toString(),
                            actor,
                            requestId
                    ));
                });
    }

    /** AnalysisApplicationService의 submitAnalysisAfterCommit 처리에 필요한 업무 로직을 수행한다. */
    private void submitAnalysisAfterCommit(UUID jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            analysisExecutorPort.submitAnalysis(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 익명 구현체의 afterCommit 처리에 필요한 업무 로직을 수행한다. */
            @Override
            public void afterCommit() {
                analysisExecutorPort.submitAnalysis(jobId);
            }
        });
    }

    /** AnalysisApplicationService의 executePendingAnalysis 처리의 핵심 작업 흐름을 실행한다. */
    private String executePendingAnalysis(AnalysisSession analysisSession) {
        SupportedLocale locale = SupportedLocale.fromAcceptLanguage(analysisSession.locale());
        if (analysisSession.applicationId() != null) {
            ManagedApplication application = applicationRepositoryPort.findById(analysisSession.applicationId())
                    .orElseThrow(() -> new NoSuchElementException("Application not found: " + analysisSession.applicationId()));
            return analyzeNamespaceWithFallback(application.clusterId(), application.namespace(), application.name(), locale);
        }
        if (analysisSession.namespace() != null && !analysisSession.namespace().isBlank()) {
            return analyzeNamespaceWithFallback(analysisSession.clusterId(), analysisSession.namespace(), null, locale);
        }
        Cluster cluster = requireCluster(analysisSession.clusterId());
        return analyzeClusterWithFallback(cluster, buildClusterContext(cluster), locale);
    }

    /** AnalysisApplicationService의 getAnalysisByJobId 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public AnalysisSession getAnalysisByJobId(UUID jobId) {
        return analysisSessionRepositoryPort.findByAsyncJobId(jobId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found for job: " + jobId));
    }

    /** AnalysisApplicationService의 getAnalysis 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public AnalysisSession getAnalysis(UUID analysisId) {
        return analysisSessionRepositoryPort.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found: " + analysisId));
    }

    /** AnalysisApplicationService의 listHistory 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public List<AnalysisSession> listHistory(UUID clusterId, UUID applicationId, String namespace) {
        if (clusterId != null) {
            requireCluster(clusterId);
        }
        return analysisSessionRepositoryPort.findRecent(clusterId, applicationId, normalizedNamespace(namespace), ANALYSIS_HISTORY_LIMIT);
    }

    /** AnalysisApplicationService의 deleteAnalysis 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    @Transactional
    public void deleteAnalysis(UUID analysisId, String actor, String requestId) {
        AnalysisSession analysisSession = analysisSessionRepositoryPort.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found: " + analysisId));
        analysisCommandExecutionRepositoryPort.deleteByAnalysisId(analysisId);
        analysisWorkflowStateRepositoryPort.deleteByAnalysisId(analysisId);
        analysisSessionRepositoryPort.deleteById(analysisId);
        auditLogRepositoryPort.save(AuditLog.create(
                "AI_ANALYSIS_DELETED",
                "ANALYSIS",
                analysisId.toString(),
                actor,
                requestId
        ));
    }

    /** AnalysisApplicationService의 getNamespaceDiagnostics 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public NamespaceDiagnosticsResult getNamespaceDiagnostics(UUID clusterId, String namespace) {
        requireCluster(clusterId);
        KubernetesNamespaceDiagnostics diagnostics = currentStateAnalysisGuard.reconcile(
                collectNamespaceDiagnostics(clusterId, namespace));
        return diagnosticsResult(clusterId, namespace, diagnostics);
    }

    /** AnalysisApplicationService의 getPodLogs 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public PodLogsResult getPodLogs(UUID clusterId, String namespace, String podName, String containerName, int tailLines) {
        requireCluster(clusterId);
        int sanitizedTailLines = Math.max(10, Math.min(tailLines, 1000));
        KubernetesPodLogs logs = kubernetesNamespaceDiagnosticsPort.collectPodLogs(
                connectionCredential(clusterId), namespace, podName, containerName, sanitizedTailLines, false);
        return podLogsResult(clusterId, logs);
    }

    /** AnalysisApplicationService의 getResourceLogs 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public PodLogsResult getResourceLogs(UUID clusterId, String namespace, String resourceType, String resourceName, String containerName, int tailLines) {
        requireCluster(clusterId);
        int sanitizedTailLines = Math.max(10, Math.min(tailLines, 1000));
        KubernetesPodLogs logs = kubernetesNamespaceDiagnosticsPort.collectResourceLogs(
                connectionCredential(clusterId), namespace, resourceType, resourceName, containerName, sanitizedTailLines, false);
        return podLogsResult(clusterId, logs);
    }

    /** AnalysisApplicationService의 previewCommand 처리에 필요한 업무 로직을 수행한다. */
    @Override
    @Transactional(readOnly = true)
    public AnalysisCommandPreviewResult previewCommand(UUID analysisId, AnalysisCommandExecuteCommand command) {
        AnalysisSession analysis = getAnalysis(analysisId);
        AnalysisCommandParser.ParsedCommand parsed = COMMAND_PARSER.parse(command.command(), analysis.namespace());
        AnalysisCommandExecutor.GuardResult guard = analysisCommandExecutor.guard(analysis.clusterId(), parsed);
        return new AnalysisCommandPreviewResult(parsed.command(), parsed.safety(), guard.executable(), guard.reason(),
                parsed.namespace(), guard.requiresConfirmation(), guard.confirmationText(), guard.rbacAllowed(),
                guard.dryRunPassed(), guard.rollbackGuardPassed(), guard.guardMessage(), guard.dryRunSummary());
    }

    /** AnalysisApplicationService의 executeCommand 처리의 핵심 작업 흐름을 실행한다. */
    @Override
    public AnalysisCommandExecution executeCommand(UUID analysisId, AnalysisCommandExecuteCommand command, String actor,
                                                   String requestId) {
        AnalysisSession analysis = getAnalysis(analysisId);
        AnalysisCommandParser.ParsedCommand parsed = COMMAND_PARSER.parse(command.command(), analysis.namespace());
        AnalysisCommandExecutor.GuardResult guard = analysisCommandExecutor.guard(analysis.clusterId(), parsed);
        if (!guard.executable()) {
            AnalysisCommandExecution blocked = persistCommandExecution(AnalysisCommandExecution.blocked(analysis.id(),
                    analysis.clusterId(), parsed.namespace(), parsed.command(), parsed.safety(), guard.reason(), actor));
            mergeCommandExecutionIntoAnalysis(analysis, blocked, parsed);
            auditCommand("AI_ANALYSIS_COMMAND_BLOCKED", analysis.id(), actor, requestId);
            return blocked;
        }
        if (guard.requiresConfirmation() && !guard.confirmationText().equals(valueOrBlank(command.confirmText()).trim())) {
            AnalysisCommandExecution blocked = persistCommandExecution(AnalysisCommandExecution.blocked(analysis.id(),
                    analysis.clusterId(), parsed.namespace(), parsed.command(), parsed.safety(),
                    "변경 조치 확인 문구가 일치하지 않습니다. 확인 문구: " + guard.confirmationText(), actor));
            mergeCommandExecutionIntoAnalysis(analysis, blocked, parsed);
            auditCommand("AI_ANALYSIS_COMMAND_CONFIRMATION_FAILED", analysis.id(), actor, requestId);
            return blocked;
        }

        long started = System.nanoTime();
        try {
            String stdout = analysisCommandExecutor.execute(analysis.clusterId(), parsed);
            AnalysisCommandExecution execution = persistCommandExecution(AnalysisCommandExecution.succeeded(analysis.id(),
                    analysis.clusterId(), parsed.namespace(), parsed.command(), parsed.safety(), guard.reason(), stdout,
                    elapsedMillis(started), actor));
            mergeCommandExecutionIntoAnalysis(analysis, execution, parsed);
            auditCommand("AI_ANALYSIS_COMMAND_EXECUTED", analysis.id(), actor, requestId);
            return execution;
        } catch (RuntimeException exception) {
            AnalysisCommandExecution execution = persistCommandExecution(AnalysisCommandExecution.failed(analysis.id(),
                    analysis.clusterId(), parsed.namespace(), parsed.command(), parsed.safety(), guard.reason(),
                    exception.getMessage(), elapsedMillis(started), actor));
            mergeCommandExecutionIntoAnalysis(analysis, execution, parsed);
            auditCommand("AI_ANALYSIS_COMMAND_FAILED", analysis.id(), actor, requestId);
            return execution;
        }
    }

    /** AnalysisApplicationService의 listCommandExecutions 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public List<AnalysisCommandExecution> listCommandExecutions(UUID analysisId) {
        getAnalysis(analysisId);
        try {
            return analysisCommandExecutionRepositoryPort.findByAnalysisId(analysisId);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /** AnalysisApplicationService의 updateWorkflowState 처리 대상의 상태를 갱신한다. */
    @Override
    @Transactional
    public AnalysisWorkflowState updateWorkflowState(UUID analysisId, String issueGroupId,
                                                     UpdateAnalysisWorkflowStateCommand command, String actor,
                                                     String requestId) {
        getAnalysis(analysisId);
        String normalizedStatus = normalizeWorkflowStatus(command.status());
        String trimmedIssueGroupId = requireText(issueGroupId, "issueGroupId");
        AnalysisWorkflowState nextState = analysisWorkflowStateRepositoryPort
                .findByAnalysisIdAndIssueGroupId(analysisId, trimmedIssueGroupId)
                .map(existing -> existing.update(normalizedStatus, truncate(command.note(), 1000), actor))
                .orElseGet(() -> AnalysisWorkflowState.create(analysisId, trimmedIssueGroupId, normalizedStatus,
                        truncate(command.note(), 1000), actor));
        AnalysisWorkflowState saved = analysisWorkflowStateRepositoryPort.save(nextState);
        audit("AI_ANALYSIS_WORKFLOW_UPDATED", analysisId, actor, requestId);
        return saved;
    }

    /** AnalysisApplicationService의 listWorkflowStates 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public List<AnalysisWorkflowState> listWorkflowStates(UUID analysisId) {
        getAnalysis(analysisId);
        return analysisWorkflowStateRepositoryPort.findByAnalysisId(analysisId);
    }

    /** AnalysisApplicationService의 podLogsResult 처리에 필요한 업무 로직을 수행한다. */
    private PodLogsResult podLogsResult(UUID clusterId, KubernetesPodLogs logs) {
        return new PodLogsResult(
                clusterId,
                logs.namespace(),
                logs.podName(),
                logs.tailLines(),
                logs.containers().stream()
                        .map(container -> new PodLogsResult.ContainerLogResult(
                                container.containerName(),
                                container.log(),
                                container.truncated()
                        ))
                        .toList(),
                logs.collectedAt()
        );
    }

    /** AnalysisApplicationService의 buildContext 처리에 필요한 결과를 조합해 반환한다. */
    private String buildContext(UUID clusterId, String namespace, String applicationName) {
        List<KubernetesResourceSnapshot> resources = resourceSnapshotRepositoryPort.findLatest(
                clusterId, namespace, null, ANALYSIS_CONTEXT_RESOURCE_LIMIT);
        List<KubernetesEventSnapshot> events = eventSnapshotRepositoryPort.findLatest(
                clusterId, namespace, ANALYSIS_CONTEXT_EVENT_LIMIT);
        KubernetesNamespaceDiagnostics diagnostics = currentStateAnalysisGuard.reconcile(
                collectNamespaceDiagnostics(clusterId, namespace));

        StringBuilder context = new StringBuilder(32768);
        context.append("clusterId=").append(clusterId).append('\n');
        context.append("namespace=").append(namespace).append('\n');
        if (applicationName != null) {
            context.append("applicationName=").append(applicationName).append('\n');
        }
        context.append("analysisScope=namespace-live-diagnostics\n");
        context.append("expectedOutput=JSON with summary, severity, riskScore, findings, rootCauses, logAnalysis, performance, scaling, riskForecast, changeTimeline, runbookActions, recommendations, operationsGuide, nextActions, verificationCommands, evidence\n");
        context.append("resourceCoverage=Pod,Deployment,StatefulSet,DaemonSet,ReplicaSet,Service,Endpoint,Ingress,ConfigMap,SecretMetadata,PVC,ServiceAccount,Job,CronJob,NetworkPolicy,HPA,PDB,ResourceQuota,LimitRange,Event,PodLog\n");
        context.append("metricsPolicy=Prometheus is not integrated in this phase. Do not invent CPU/memory usage. Use Kubernetes API spec/status signals only.\n");
        context.append("analysisDimensions:\n");
        context.append("- RCA: correlate unhealthy resources, events, container states, restart counts, and logs.\n");
        context.append("- Log analysis: extract repeated error/warn patterns without exposing secrets.\n");
        context.append("- Performance: infer bottlenecks from restart loops, pending pods, unready endpoints, PVC state, rollout health, probes, requests/limits, quotas, and limit ranges.\n");
        context.append("- Scaling: identify scale-up/HPA candidates, missing resource requests, HPA status issues, PDB constraints, quota pressure, and capacity readiness.\n");
        context.append("- Operations consultation: recommend short-term stabilizing actions and medium-term cluster operating improvements.\n");
        context.append("- Risk forecasting: predict near-term operational risks only from Kubernetes API evidence, not invented metrics.\n");
        context.append("- Change correlation: infer when degradation likely started by correlating events, resource states, and logs.\n");
        context.append("- Runbook: provide verification-first commands before any mutation command.\n");
        context.append("- Safety: only recommend commands for inspection or controlled remediation; mark destructive actions clearly.\n");
        context.append("qualityRules:\n");
        context.append("- Evidence first: cite the exact resource, event, or log signal for each finding.\n");
        context.append("- Avoid overclaiming: do not say pods/services are healthy without ready pod/container and endpoint evidence.\n");
        context.append("- Metrics caveat: Prometheus/metrics-server data is unavailable, so CPU/memory usage conclusions must be marked as unavailable.\n");
        context.append("- Log precision: if a log says Debug mode: off, do not state that debug mode is enabled; report only the Flask development server warning.\n");
        context.append("- Prefer verification commands before mutation commands.\n");
        context.append("diagnosticCounts resources=").append(diagnostics.resources().size())
                .append(" events=").append(diagnostics.events().size())
                .append(" podLogs=").append(diagnostics.podLogs().size())
                .append(" collectedAt=").append(diagnostics.collectedAt())
                .append('\n');
        context.append('\n');

        NamespaceDiagnosticsResult.RiskForecast forecast = riskForecast(diagnostics);
        context.append("riskForecastSignals overallRisk=").append(forecast.overallRisk())
                .append(" riskLevel=").append(forecast.riskLevel())
                .append(" horizon=").append(forecast.horizon())
                .append(" summary=").append(forecast.summary())
                .append('\n');
        forecast.predictions().stream()
                .limit(12)
                .forEach(prediction -> context.append("- ")
                        .append(prediction.severity()).append(' ')
                        .append(prediction.category())
                        .append(" probability=").append(prediction.probability()).append("%")
                        .append(" target=").append(valueOrBlank(prediction.resourceKind())).append('/')
                        .append(valueOrBlank(prediction.resourceName()))
                        .append(" signal=").append(valueOrBlank(prediction.signal()))
                        .append(" impact=").append(valueOrBlank(prediction.impact()))
                        .append(" recommendation=").append(valueOrBlank(prediction.recommendation()))
                        .append(" evidence=").append(valueOrBlank(prediction.evidence()))
                        .append('\n'));
        context.append('\n');

        context.append("changeCorrelationTimeline:\n");
        changeTimeline(diagnostics).stream()
                .limit(15)
                .forEach(item -> context.append("- ")
                        .append(item.occurredAt()).append(' ')
                        .append(item.severity()).append(' ')
                        .append(item.category())
                        .append(" target=").append(valueOrBlank(item.resourceKind())).append('/')
                        .append(valueOrBlank(item.resourceName()))
                        .append(" title=").append(valueOrBlank(item.title()))
                        .append(" suspectedChange=").append(valueOrBlank(item.suspectedChange()))
                        .append(" recommendation=").append(valueOrBlank(item.recommendation()))
                        .append('\n'));
        context.append("runbookActions:\n");
        runbookActions(diagnostics).stream()
                .limit(12)
                .forEach(action -> context.append("- ")
                        .append(action.priority()).append(' ')
                        .append(action.targetKind()).append('/')
                        .append(action.targetName())
                        .append(" title=").append(action.title())
                        .append(" reason=").append(action.reason())
                        .append(" command=").append(action.command())
                        .append('\n'));
        context.append('\n');

        context.append("resourceKindCounts:\n");
        resourceKindCounts(diagnostics.resources()).forEach(kind -> context.append("- ")
                .append(kind.resourceType()).append('=').append(kind.count())
                .append('\n'));

        context.append("problemResourceSignals:\n");
        diagnostics.resources().stream()
                .filter(this::isProblemResource)
                .filter(resource -> applicationName == null || resource.resourceName().contains(applicationName))
                .limit(20)
                .forEach(resource -> appendResource(context, resource));

        context.append("warningEventSignals:\n");
        diagnostics.events().stream()
                .filter(this::isWarningEvent)
                .limit(20)
                .forEach(event -> appendEvent(context, event, 500));

        context.append("highSignalPodLogs:\n");
        diagnostics.podLogs().stream()
                .filter(log -> applicationName == null || log.podName().contains(applicationName))
                .filter(this::isHighSignalLog)
                .limit(POD_LOG_CONTEXT_LIMIT)
                .forEach(log -> appendPodLog(context, log, 900));

        context.append('\n');
        context.append("liveResources:\n");
        prioritizedResources(diagnostics.resources()).stream()
                .filter(resource -> applicationName == null || resource.resourceName().contains(applicationName))
                .limit(LIVE_RESOURCE_CONTEXT_LIMIT)
                .forEach(resource -> appendResource(context, resource));
        context.append("liveEvents:\n");
        prioritizedEvents(diagnostics.events()).stream()
                .limit(LIVE_EVENT_CONTEXT_LIMIT)
                .forEach(event -> appendEvent(context, event, 700));
        context.append("podLogs:\n");
        prioritizedPodLogs(diagnostics.podLogs()).stream()
                .filter(log -> applicationName == null || log.podName().contains(applicationName))
                .limit(POD_LOG_CONTEXT_LIMIT)
                .forEach(log -> appendPodLog(context, log, 1200));

        context.append('\n');
        context.append("storedSnapshotResources:\n");
        resources.stream()
                .filter(resource -> applicationName == null || resource.resourceName().contains(applicationName))
                .limit(STORED_RESOURCE_CONTEXT_LIMIT)
                .forEach(resource -> context.append("- ")
                        .append(resource.resourceType()).append('/')
                        .append(resource.resourceName())
                        .append(" status=").append(resource.status())
                        .append(" summary=").append(resource.summaryJson())
                        .append('\n'));
        context.append("storedSnapshotEvents:\n");
        events.stream()
                .limit(STORED_EVENT_CONTEXT_LIMIT)
                .forEach(event -> context.append("- ")
                        .append(event.type()).append(' ')
                        .append(event.reason()).append(' ')
                        .append(event.involvedKind()).append('/')
                        .append(event.involvedName())
                        .append(" message=").append(truncate(event.message(), 500))
                        .append('\n'));
        return limitContext(context.toString());
    }

    /** AnalysisApplicationService의 buildClusterContext 처리에 필요한 결과를 조합해 반환한다. */
    private ClusterAnalysisContext buildClusterContext(Cluster cluster) {
        KubernetesStateInventory inventory = kubernetesStateSyncPort.collectClusterInventory(connectionCredential(cluster.id()));
        List<KubernetesResourceSnapshot.CollectedResource> resources = inventory.resources();
        List<KubernetesEventSnapshot.CollectedEvent> events = inventory.events();
        List<KubernetesResourceSnapshot> storedResources = resourceSnapshotRepositoryPort.findLatest(
                cluster.id(), null, null, ANALYSIS_CONTEXT_RESOURCE_LIMIT);
        List<KubernetesEventSnapshot> storedEvents = eventSnapshotRepositoryPort.findLatest(
                cluster.id(), null, ANALYSIS_CONTEXT_EVENT_LIMIT);

        StringBuilder context = new StringBuilder(32768);
        context.append("clusterId=").append(cluster.id()).append('\n');
        context.append("clusterName=").append(cluster.name()).append('\n');
        context.append("environment=").append(cluster.environment()).append('\n');
        context.append("provider=").append(cluster.provider()).append('\n');
        context.append("region=").append(valueOrBlank(cluster.region())).append('\n');
        context.append("analysisScope=cluster-live-inventory\n");
        context.append("expectedOutput=JSON with summary, severity, riskScore, findings, rootCauses, logAnalysis, performance, scaling, riskForecast, changeTimeline, runbookActions, recommendations, operationsGuide, nextActions, verificationCommands, evidence\n");
        context.append("resourceCoverage=cluster-wide Namespace,Pod,Deployment,ReplicaSet,Service,Endpoint,Ingress,PVC,PV,Event plus latest stored snapshots when available\n");
        context.append("metricsPolicy=Prometheus is not integrated in this phase. Do not invent CPU/memory usage. Use Kubernetes API spec/status/event signals only.\n");
        context.append("analysisDimensions:\n");
        context.append("- Cluster posture: identify unhealthy namespaces, workload hotspots, storage issues, traffic/resource policy gaps, and recurring warning events.\n");
        context.append("- RCA: correlate resource states and warning events across namespaces.\n");
        context.append("- Performance and scaling: infer only from replicas, Pending resources, PVC/PV state, endpoint availability, requests/limits policy presence in summaries, and HPA/Quota signals if present.\n");
        context.append("- Operations consultation: separate immediate stabilization from medium-term cluster operating direction.\n");
        context.append("- Risk forecasting: predict namespace or cluster-level risks only from Kubernetes API evidence.\n");
        context.append("- Runbook: provide verification-first cluster/namespace scoped kubectl commands before any mutation command.\n");
        context.append("qualityRules:\n");
        context.append("- Evidence first: cite namespace, resource kind/name, event reason, and status for each finding.\n");
        context.append("- Avoid healthy claims without ready workload and event evidence.\n");
        context.append("- Mark unavailable metrics explicitly instead of estimating CPU/memory utilization.\n");
        context.append("- Prioritize cross-namespace or repeated signals over isolated low-risk signals.\n");
        context.append("- Do not recommend destructive commands without clear risk marking.\n\n");

        long warningEvents = events.stream().filter(this::isWarningCollectedEvent).count();
        long problemResources = resources.stream().filter(this::isProblemCollectedResource).count();
        context.append("clusterDiagnosticCounts resources=").append(resources.size())
                .append(" events=").append(events.size())
                .append(" warningEvents=").append(warningEvents)
                .append(" problemResources=").append(problemResources)
                .append('\n');

        context.append("resourceKindCounts:\n");
        collectedResourceKindCounts(resources).forEach((kind, count) -> context.append("- ")
                .append(kind).append('=').append(count).append('\n'));

        context.append("namespaceHotspots:\n");
        namespaceHotspots(resources, events).entrySet().stream()
                .limit(20)
                .forEach(entry -> context.append("- namespace=").append(entry.getKey())
                        .append(' ').append(entry.getValue()).append('\n'));

        context.append("problemResourceSignals:\n");
        resources.stream()
                .filter(this::isProblemCollectedResource)
                .limit(40)
                .forEach(resource -> appendCollectedResource(context, resource));

        context.append("warningEventSignals:\n");
        events.stream()
                .filter(this::isWarningCollectedEvent)
                .sorted(Comparator.comparing(event -> event.count() == null ? 0 : event.count(), Comparator.reverseOrder()))
                .limit(CLUSTER_EVENT_CONTEXT_LIMIT)
                .forEach(event -> appendCollectedEvent(context, event, 600));

        context.append("clusterRunbookSeed:\n");
        context.append("- P1 Cluster/event hotspots command=kubectl get events -A --sort-by=.lastTimestamp\n");
        context.append("- P1 Problem pods command=kubectl get pods -A --field-selector=status.phase!=Running\n");
        context.append("- P2 Workload rollout status command=kubectl get deploy,statefulset,daemonset -A\n");
        context.append("- P2 Storage status command=kubectl get pvc,pv -A\n");
        context.append("- P3 Namespace inventory command=kubectl get ns\n");

        context.append("liveClusterResources:\n");
        resources.stream()
                .sorted(Comparator.comparing(this::isProblemCollectedResource).reversed())
                .limit(CLUSTER_RESOURCE_CONTEXT_LIMIT)
                .forEach(resource -> appendCollectedResource(context, resource));

        context.append("storedSnapshotResources:\n");
        storedResources.stream()
                .limit(STORED_RESOURCE_CONTEXT_LIMIT)
                .forEach(resource -> context.append("- namespace=").append(valueOrBlank(resource.namespace()))
                        .append(' ').append(resource.resourceType()).append('/')
                        .append(resource.resourceName())
                        .append(" status=").append(valueOrBlank(resource.status()))
                        .append(" summary=").append(valueOrBlank(resource.summaryJson()))
                        .append('\n'));

        context.append("storedSnapshotEvents:\n");
        storedEvents.stream()
                .limit(STORED_EVENT_CONTEXT_LIMIT)
                .forEach(event -> context.append("- namespace=").append(valueOrBlank(event.namespace()))
                        .append(' ').append(valueOrBlank(event.type()))
                        .append(' ').append(valueOrBlank(event.reason()))
                        .append(' ').append(valueOrBlank(event.involvedKind()))
                        .append('/').append(valueOrBlank(event.involvedName()))
                        .append(" message=").append(truncate(event.message(), 500))
                        .append('\n'));

        return new ClusterAnalysisContext(
                limitContext(context.toString(), Math.min(maxAnalysisContextChars, CLUSTER_ANALYSIS_CONTEXT_CHAR_LIMIT)),
                resources,
                events
        );
    }

    /** AnalysisApplicationService의 limitContext 처리에 필요한 업무 로직을 수행한다. */
    private String limitContext(String context) {
        return limitContext(context, maxAnalysisContextChars);
    }

    /** AnalysisApplicationService의 limitContext 처리에 필요한 업무 로직을 수행한다. */
    private String limitContext(String context, int maxChars) {
        if (context.length() <= maxChars) {
            return context;
        }
        return context.substring(0, maxChars)
                + "\n[context truncated: originalChars=" + context.length()
                + ", maxChars=" + maxChars + "]\n";
    }

    /** AnalysisApplicationService의 analyzeNamespaceWithFallback 처리의 핵심 작업 흐름을 실행한다. */
    private String analyzeNamespaceWithFallback(UUID clusterId, String namespace, String applicationName,
                                                SupportedLocale locale) {
        long startedNanos = System.nanoTime();
        KubernetesNamespaceDiagnostics diagnostics = currentStateAnalysisGuard.reconcile(
                collectNamespaceDiagnostics(clusterId, namespace));
        try {
            return analyzeNamespaceBySections(clusterId, namespace, applicationName, diagnostics, locale);
        } catch (RuntimeException exception) {
            if (!isTimeoutFailure(exception)) {
                throw exception;
            }
            return namespaceTimeoutFallbackJson(clusterId, namespace, applicationName, diagnostics, exception,
                    startedNanos, locale);
        }
    }

    /** AnalysisApplicationService의 analyzeNamespaceBySections 처리의 핵심 작업 흐름을 실행한다. */
    private String analyzeNamespaceBySections(UUID clusterId, String namespace, String applicationName,
                                              KubernetesNamespaceDiagnostics diagnostics, SupportedLocale locale) {
        long totalStartedNanos = System.nanoTime();
        String rcaContext = namespaceSectionContext(clusterId, namespace, applicationName, diagnostics, "root-cause");
        String logContext = namespaceSectionContext(clusterId, namespace, applicationName, diagnostics, "log-analysis");
        String runbookOpsContext = namespaceSectionContext(clusterId, namespace, applicationName, diagnostics, "runbook-operations");
        JsonNode reusableResult = applicationName == null
                ? analysisSessionRepositoryPort.findLatestSucceededByScope(clusterId, null, namespace)
                .map(AnalysisSession::resultJson).map(analysisComparisonService::parse)
                .orElse(objectMapper.createObjectNode())
                : objectMapper.createObjectNode();

        UUID tenantId = clusterRepositoryPort.findById(clusterId).orElseThrow().tenantId();
        CompletableFuture<AnalysisSectionExecutor.Result> rca = sectionExecutor.executeOrReuse(tenantId,
                "root-cause",
                """
                        Return JSON with fields: summary, severity, riskScore, findings, rootCauses.
                        Focus only on RCA. Correlate unhealthy resources, warning events, and high-signal logs.
                        Do not include performance, scaling, runbook, or operations guide.
                        %s
                        """.formatted(localePolicy.instruction(locale)),
                rcaContext,
                reusableResult,
                locale
        );
        CompletableFuture<AnalysisSectionExecutor.Result> logs = sectionExecutor.executeOrReuse(tenantId,
                "log-analysis",
                """
                        Return JSON with field: logAnalysis.
                        Each logAnalysis item must include podName, containerName, signal, pattern, evidence, interpretation.
                        Focus only on repeated error/warn patterns and production-readiness warnings.
                        %s
                        """.formatted(localePolicy.instruction(locale)),
                logContext,
                reusableResult,
                locale
        );
        CompletableFuture<AnalysisSectionExecutor.Result> runbookOps = sectionExecutor.executeOrReuse(tenantId,
                "runbook-operations",
                """
                        Return JSON with fields: runbookActions, recommendations, operationsGuide, nextActions, verificationCommands.
                        Split commands into category values: verification, diagnosis, safe-action, destructive.
                        Prefer read-only verification/diagnosis commands before mutation commands.
                        Mark destructive actions clearly and include why each command should be executed.
                        Focus on operator workflow and short/medium term stabilization guidance.
                        %s
                        """.formatted(localePolicy.instruction(locale)),
                runbookOpsContext,
                reusableResult,
                locale
        );

        ObjectNode root = parseObject(namespaceTimeoutFallbackJson(clusterId, namespace, applicationName, diagnostics,
                new RuntimeException("section analysis fallback"), totalStartedNanos, locale));
        root.put("locale", locale.tag());
        AnalysisSectionExecutor.Result performanceScalingSection = deterministicPerformanceScalingSection(diagnostics);
        AnalysisSectionExecutor.Result riskTimelineSection = deterministicRiskTimelineSection(diagnostics);
        List<AnalysisSectionExecutor.Result> sections = List.of(rca.join(), logs.join(), performanceScalingSection, riskTimelineSection, runbookOps.join());
        long successfulSections = sections.stream().filter(AnalysisSectionExecutor.Result::successful).count();
        long aiSectionCount = sections.stream().filter(section -> !section.deterministic()).count();
        long successfulAiSections = sections.stream()
                .filter(section -> !section.deterministic())
                .filter(AnalysisSectionExecutor.Result::successful)
                .count();
        NamespaceDiagnosticsResult.RiskForecast forecast = riskForecast(diagnostics);

        root.put("analysisMode", successfulSections == sections.size()
                ? "sectioned-ai-analysis"
                : "sectioned-ai-analysis-partial-fallback");
        root.put("summary", successfulSections == 0
                ? "All AI analysis sections failed, so this result was generated from Kubernetes API evidence."
                : "Namespace analysis completed with " + successfulAiSections + "/" + aiSectionCount
                + " AI sections and deterministic Kubernetes risk/timeline evidence.");
        root.put("severity", severityFromRiskScore(forecast.overallRisk()));
        root.put("riskScore", forecast.overallRisk());
        root.put("confidence", successfulSections == sections.size() ? 0.72 : successfulSections == 0 ? 0.35 : 0.55);

        analysisResultAssembler.mergeNamespaceSections(root, sections);
        appendLogIntelligenceAnalysis(root, diagnostics);
        ArrayNode problemCards = problemCards(diagnostics);
        ArrayNode issueGroups = issueGroups(diagnostics);
        root.set("problemCards", problemCards);
        root.set("issueGroups", issueGroups);
        root.set("logIntelligence", logIntelligence(diagnostics));
        ObjectNode actionRecommendations = actionRecommendations(diagnostics, issueGroups);
        root.set("actionRecommendations", actionRecommendations);
        appendActionRecommendationNextActions(root, actionRecommendations);
        root.set("remediationPlan", remediationPlan(diagnostics, issueGroups));
        enrichRootCauseEvidence(root, diagnostics);
        root.set("confidenceValidation", confidenceValidation(diagnostics, problemCards, issueGroups));
        root.set("evidenceLedger", evidenceLedger(diagnostics, issueGroups, problemCards));
        root.set("eventNoiseReduction", eventNoiseReduction(diagnostics));
        root.set("correlationMap", correlationMap(diagnostics, issueGroups));
        root.set("issueGroupDeepDives", issueGroupDeepDives(diagnostics, issueGroups));
        root.set("actionWorkflow", actionWorkflow(issueGroups));
        root.set("conclusionValidation", conclusionValidation(root, issueGroups));
        root.set("reanalysisPlan", reanalysisPlan(diagnostics, issueGroups));

        analysisResultAssembler.writeSectionTelemetry(root, "namespace-sectioned", namespace,
                elapsedMillis(totalStartedNanos), sections,
                List.of(rcaContext, logContext, runbookOpsContext)
                        .stream().mapToInt(String::length).sum(), null);
        collectionDiagnosticsWriter.write(root, diagnostics);
        analysisResultAssembler.writeIncrementalMetadata(root, applicationName == null, sections);
        enrichRunbookActions(root);
        root.set("commandSafety", commandSafety(root));
        root.set("analysisQuality", analysisQuality(root, diagnostics, issueGroups, actionRecommendations));
        currentStateAnalysisGuard.enforce(root, diagnostics, locale, forecast.overallRisk(),
                severityFromRiskScore(forecast.overallRisk()));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to merge namespace section analysis result", exception);
        }
    }

    /** AnalysisApplicationService의 problemCards 처리에 필요한 업무 로직을 수행한다. */
    private ArrayNode problemCards(KubernetesNamespaceDiagnostics diagnostics) {
        ArrayNode cards = objectMapper.createArrayNode();
        diagnostics.resources().stream()
                .filter(this::isProblemResource)
                .limit(8)
                .forEach(resource -> addProblemResourceCard(cards, diagnostics, resource));
        if (!cards.isEmpty()) {
            return cards;
        }
        diagnostics.events().stream()
                .filter(this::isWarningEvent)
                .limit(6)
                .forEach(event -> addWarningEventCard(cards, event));
        if (!cards.isEmpty()) {
            return cards;
        }
        diagnostics.podLogs().stream()
                .filter(this::isHighSignalLog)
                .limit(4)
                .forEach(log -> addLogSignalCard(cards, log));
        return cards;
    }

    /** AnalysisApplicationService의 issueGroups 처리 조건의 충족 여부를 판단한다. */
    private ArrayNode issueGroups(KubernetesNamespaceDiagnostics diagnostics) {
        Map<String, IssueGroupAccumulator> groups = new LinkedHashMap<>();
        portMismatchSignals(diagnostics).forEach(signal -> {
            boolean startupSignal = isPortStartupSignal(signal);
            String key = startupSignal
                    ? "port-startup:" + String.join(",", signal.matchedResources())
                    : "port-mismatch:Service/" + signal.serviceName() + ":" + signal.targetPort();
            IssueGroupAccumulator group = groups.computeIfAbsent(key, ignored -> issueGroupFromPortMismatch(key, signal));
            if (!startupSignal) {
                group.addAffectedResource("Service", signal.serviceName());
            }
            signal.matchedResources().forEach(resource -> {
                int separator = resource.indexOf('/');
                if (separator > 0 && separator < resource.length() - 1) {
                    group.addAffectedResource(resource.substring(0, separator), resource.substring(separator + 1));
                }
            });
            group.addEvidence(signal.reason());
            if (!startupSignal) {
                group.addEvidence("Service/" + signal.serviceName() + " port=" + signal.servicePort()
                        + " targetPort=" + signal.targetPort() + " selector=" + signal.selector());
            }
            group.addEvidence("matchedResources=" + signal.matchedResources() + " declaredContainerPorts=" + signal.declaredContainerPorts());
        });
        diagnostics.events().stream()
                .filter(this::isWarningEvent)
                .forEach(event -> {
                    String key = issueGroupKey(event);
                    IssueGroupAccumulator group = groups.computeIfAbsent(key, ignored -> issueGroupFromEvent(key, event));
                    group.addEvent(event);
                });
        diagnostics.resources().stream()
                .filter(this::isProblemResource)
                .forEach(resource -> {
                    boolean added = false;
                    for (IssueGroupAccumulator group : groups.values()) {
                        if (group.matches(resource.resourceType(), resource.resourceName())) {
                            group.addResource(resource);
                            added = true;
                        }
                    }
                    if (!added) {
                        String key = "resource:" + resource.resourceType() + "/" + resource.resourceName()
                                + ":" + statusCategory(resource.status());
                        IssueGroupAccumulator group = groups.computeIfAbsent(key, ignored -> issueGroupFromResource(key, resource));
                        group.addResource(resource);
                    }
                });
        diagnostics.podLogs().stream()
                .filter(this::isHighSignalLog)
                .forEach(log -> {
                    boolean added = false;
                    for (IssueGroupAccumulator group : groups.values()) {
                        if (group.matches("Pod", log.podName())) {
                            group.addLog(log);
                            added = true;
                        }
                    }
                    if (!added) {
                        LogInsight insight = logInsight(log);
                        String key = insight.matched()
                                ? "log:" + insight.category() + ":Pod/" + log.podName() + ":" + insight.signal()
                                : "log:Pod/" + log.podName() + ":" + firstLogSignal(log.log());
                        IssueGroupAccumulator group = groups.computeIfAbsent(key, ignored -> issueGroupFromLog(key, log));
                        group.addLog(log);
                    }
                });

        ArrayNode result = objectMapper.createArrayNode();
        List<IssueGroupAccumulator> sortedGroups = groups.values().stream()
                .sorted(Comparator.comparing(IssueGroupAccumulator::score).reversed())
                .limit(10)
                .toList();
        for (int index = 0; index < sortedGroups.size(); index++) {
            result.add(issueGroupJson(sortedGroups.get(index), index + 1));
        }
        return result;
    }

    /** AnalysisApplicationService의 issueGroupFromPortMismatch 처리 조건의 충족 여부를 판단한다. */
    private IssueGroupAccumulator issueGroupFromPortMismatch(String key, PortMismatchSignal signal) {
        IssueGroupAccumulator group = new IssueGroupAccumulator(key);
        boolean startupSignal = isPortStartupSignal(signal);
        group.category = startupSignal ? "port-startup" : "port-mismatch";
        group.title = startupSignal
                ? "컨테이너 포트 바인딩 실패 · " + representativePodName(signal)
                : "Service 포트 매핑 확인 필요 · " + signal.serviceName();
        group.severity = signal.strongSignal() ? "HIGH" : "MEDIUM";
        group.fixReadiness = "NEEDS_VERIFICATION";
        group.rootCause = signal.reason();
        group.recommendedNextAction = startupSignal
                ? "previous 로그에서 bind/listen 실패 포트와 컨테이너 보안 컨텍스트, image 기본 listen 포트를 함께 확인하세요."
                : "Service targetPort와 선택된 Pod/Workload의 containerPort, 애플리케이션 listen 포트를 함께 대조하세요.";
        group.resourceKind = startupSignal ? "Pod" : "Service";
        group.resourceName = startupSignal ? representativePodName(signal) : signal.serviceName();
        group.namespace = signal.namespace();
        if (!startupSignal) {
            group.addReference("Service", signal.serviceName(), "targetPort 매핑 검증");
        }
        signal.matchedResources().forEach(resource -> {
            int separator = resource.indexOf('/');
            if (separator > 0 && separator < resource.length() - 1) {
                group.addReference(resource.substring(0, separator), resource.substring(separator + 1),
                        startupSignal ? "포트 바인딩 실패 로그 대상" : "Service selector 대상");
            }
        });
        return group;
    }

    /** AnalysisApplicationService의 isPortStartupSignal 처리 조건의 충족 여부를 판단한다. */
    private boolean isPortStartupSignal(PortMismatchSignal signal) {
        return valueOrBlank(signal.serviceName()).isBlank() || "-".equals(signal.serviceName());
    }

    /** AnalysisApplicationService의 representativePodName 처리에 필요한 업무 로직을 수행한다. */
    private String representativePodName(PortMismatchSignal signal) {
        return signal.matchedResources().stream()
                .filter(resource -> resource.startsWith("Pod/"))
                .map(resource -> resource.substring("Pod/".length()))
                .findFirst()
                .orElse("-");
    }

    /** AnalysisApplicationService의 portMismatchSignals 처리에 필요한 업무 로직을 수행한다. */
    private List<PortMismatchSignal> portMismatchSignals(KubernetesNamespaceDiagnostics diagnostics) {
        boolean hasPortStartupLog = diagnostics.podLogs().stream().anyMatch(this::isPortStartupLog);
        List<PortMismatchSignal> signals = new ArrayList<>(
                kubernetesPortTopologyAnalyzer.analyze(diagnostics, hasPortStartupLog));
        diagnostics.podLogs().stream()
                .filter(this::isPortStartupLog)
                .forEach(log -> signals.add(new PortMismatchSignal(
                        valueOrBlank(log.namespace()),
                        "-",
                        "-",
                        "-",
                        "-",
                        List.of("Pod/" + log.podName()),
                        List.of(),
                        "Pod/" + log.podName() + " 시작 로그에서 포트 바인딩/listen 오류 패턴이 감지되었습니다: " + firstLogSignal(log.log()),
                        true
                )));
        return signals.stream().limit(12).toList();
    }

    /** AnalysisApplicationService의 isPortStartupLog 처리 조건의 충족 여부를 판단한다. */
    private boolean isPortStartupLog(KubernetesNamespaceDiagnostics.DiagnosticPodLog log) {
        String category = logInsight(log).category();
        return "port-startup".equals(category) || "port-conflict".equals(category);
    }

    /** AnalysisApplicationService의 logIntelligence 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode logIntelligence(KubernetesNamespaceDiagnostics diagnostics) {
        List<LogInsight> insights = logInsights(diagnostics);
        ObjectNode intelligence = objectMapper.createObjectNode();
        intelligence.put("summary", insights.isEmpty()
                ? "운영상 의미 있는 로그 위험 신호가 감지되지 않았습니다."
                : insights.size() + "개의 로그 위험 신호를 원인 후보별로 정규화했습니다.");
        intelligence.put("beginnerSummary", "로그 원문을 그대로 읽기보다, 치명도 높은 한 줄과 운영자가 확인할 명령을 먼저 보여줍니다.");
        intelligence.put("totalSignals", insights.size());
        intelligence.put("highSeveritySignals", insights.stream().filter(insight -> "HIGH".equals(insight.severity())).count());
        intelligence.put("previousLogSignals", insights.stream().filter(LogInsight::previousLog).count());
        ArrayNode items = intelligence.putArray("items");
        insights.stream().limit(12).forEach(insight -> {
            ObjectNode item = items.addObject();
            item.put("category", insight.category());
            item.put("severity", insight.severity());
            item.put("priority", insight.priority());
            item.put("title", insight.title());
            item.put("podName", insight.podName());
            item.put("containerName", insight.containerName());
            item.put("signal", insight.signal());
            item.put("operatorMeaning", insight.operatorMeaning());
            item.put("beginnerExplanation", insight.beginnerExplanation());
            item.put("recommendedNextAction", insight.recommendedNextAction());
            item.put("verificationCommand", insight.verificationCommand());
            item.put("previousLog", insight.previousLog());
            item.set("qualityGate", logQualityGate(insight));
            item.set("actionCandidates", logActionCandidates(insight));
            ArrayNode patterns = item.putArray("matchedPatterns");
            insight.matchedPatterns().forEach(patterns::add);
        });
        return intelligence;
    }

    /** AnalysisApplicationService의 appendLogIntelligenceAnalysis 처리에 필요한 업무 로직을 수행한다. */
    private void appendLogIntelligenceAnalysis(ObjectNode root, KubernetesNamespaceDiagnostics diagnostics) {
        ArrayNode logAnalysis = arrayField(root, "logAnalysis");
        List<String> existingKeys = new ArrayList<>();
        for (JsonNode item : logAnalysis) {
            existingKeys.add(valueOrBlank(item.path("podName").asText()) + "|"
                    + valueOrBlank(item.path("containerName").asText()) + "|"
                    + valueOrBlank(item.path("category").asText()) + "|"
                    + valueOrBlank(item.path("pattern").asText()));
        }
        for (LogInsight insight : logInsights(diagnostics).stream().limit(10).toList()) {
            String key = insight.podName() + "|" + insight.containerName() + "|" + insight.category() + "|" + insight.signal();
            if (existingKeys.stream().anyMatch(key::equals)) {
                continue;
            }
            ObjectNode item = logAnalysis.addObject();
            item.put("signal", logSignalLabel(insight));
            item.put("severity", insight.severity());
            item.put("category", insight.category());
            item.put("podName", insight.podName());
            item.put("containerName", insight.containerName());
            item.put("pattern", insight.signal());
            item.put("message", insight.signal());
            item.put("interpretation", insight.operatorMeaning());
            item.put("analysis", insight.beginnerExplanation());
            item.put("recommendation", insight.recommendedNextAction());
            item.put("verificationCommand", insight.verificationCommand());
            item.put("previousLog", insight.previousLog());
        }
    }

    /** AnalysisApplicationService의 logSignalLabel 처리에 필요한 업무 로직을 수행한다. */
    private String logSignalLabel(LogInsight insight) {
        if ("HIGH".equals(insight.severity())) {
            return "ERROR";
        }
        if ("LOW".equals(insight.severity())) {
            return "INFO";
        }
        return "WARN";
    }

    /** AnalysisApplicationService의 logInsights 처리에 필요한 업무 로직을 수행한다. */
    private List<LogInsight> logInsights(KubernetesNamespaceDiagnostics diagnostics) {
        return diagnostics.podLogs().stream()
                .map(this::logInsight)
                .filter(LogInsight::matched)
                .sorted(Comparator.comparingInt(LogInsight::priority)
                        .thenComparing(LogInsight::podName)
                        .thenComparing(LogInsight::containerName))
                .toList();
    }

    /** AnalysisApplicationService의 logInsight 처리에 필요한 업무 로직을 수행한다. */
    private LogInsight logInsight(KubernetesNamespaceDiagnostics.DiagnosticPodLog log) {
        List<LogInsight> candidates = valueOrBlank(log.log()).lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> classifyLogLine(log, line))
                .filter(LogInsight::matched)
                .sorted(Comparator.comparingInt(LogInsight::priority))
                .toList();
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }
        return LogInsight.none(log);
    }

    /** AnalysisApplicationService의 classifyLogLine 처리에 필요한 업무 로직을 수행한다. */
    private LogInsight classifyLogLine(KubernetesNamespaceDiagnostics.DiagnosticPodLog log, String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        boolean previous = valueOrBlank(log.log()).toLowerCase(Locale.ROOT).contains("previous terminated container log");
        String verificationCommand = logVerificationCommand(log, previous);
        if ((lower.contains("bind()") || lower.contains("cannot bind") || lower.contains("failed to bind"))
                && lower.contains("permission denied")) {
            return logInsight(log, "port-startup", "HIGH", 0, "컨테이너 포트 바인딩 권한 오류",
                    line,
                    "애플리케이션이 포트를 열지 못해 컨테이너가 종료되고 있습니다. non-root 실행과 1024 미만 포트 사용을 같이 확인해야 합니다.",
                    "컨테이너가 80 같은 낮은 포트를 열려면 권한이 필요할 수 있습니다. 권한이 없으면 앱이 뜨기 전에 종료됩니다.",
                    "previous 로그, securityContext, containerPort, 애플리케이션 listen 포트를 함께 확인하세요.",
                    verificationCommand, previous, List.of("bind-permission-denied", "previous-log"));
        }
        if (lower.contains("address already in use") || lower.contains("eaddrinuse") || lower.contains("port is already allocated")) {
            return logInsight(log, "port-conflict", "HIGH", 1, "포트 충돌",
                    line,
                    "동일 컨테이너/프로세스 내부에서 이미 사용 중인 포트를 다시 열려고 합니다.",
                    "앱 안에서 같은 포트를 두 번 쓰거나, 사이드카/기본 설정과 포트가 겹칠 때 발생합니다.",
                    "컨테이너 시작 명령, 앱 설정, sidecar 포트, Service targetPort를 대조하세요.",
                    verificationCommand, previous, List.of("address-already-in-use", "port-conflict"));
        }
        if (lower.contains("failed to listen") || lower.contains("listen tcp") || lower.contains("cannot listen")) {
            return logInsight(log, "port-startup", "HIGH", 2, "애플리케이션 listen 실패",
                    line,
                    "애플리케이션이 네트워크 listen 단계에서 실패했습니다. 포트, 권한, 바인딩 주소를 우선 봐야 합니다.",
                    "앱이 요청을 받기 위한 문을 열지 못한 상태입니다.",
                    "previous 로그와 앱 listen 설정을 확인하고, Service 포트 매핑과 분리해서 판단하세요.",
                    verificationCommand, previous, List.of("listen-failure", "startup"));
        }
        if (lower.contains("oomkilled") || lower.contains("out of memory") || lower.contains("java.lang.outofmemoryerror")
                || lower.contains("cannot allocate memory")) {
            return logInsight(log, "memory-pressure", "HIGH", 3, "메모리 부족 또는 OOM",
                    line,
                    "컨테이너가 메모리 부족으로 종료되었거나 곧 종료될 수 있습니다. requests/limits와 실제 메모리 사용 패턴을 봐야 합니다.",
                    "앱이 사용할 수 있는 메모리보다 더 많이 쓰고 있습니다.",
                    "Pod describe의 lastState, restart count, memory limit, JVM 옵션을 확인하세요.",
                    verificationCommand, previous, List.of("oom", "memory"));
        }
        if (lower.contains("read-only file system") || lower.contains("permission denied")) {
            return logInsight(log, "filesystem-permission", "HIGH", 4, "파일시스템/권한 오류",
                    line,
                    "애플리케이션이 필요한 파일을 쓰거나 읽지 못합니다. volumeMount, securityContext, 파일 권한을 확인해야 합니다.",
                    "앱이 필요한 파일이나 디렉터리에 접근하지 못해 실패할 수 있습니다.",
                    "volumeMount, readOnly 설정, runAsUser/fsGroup, image 내부 경로 권한을 확인하세요.",
                    verificationCommand, previous, List.of("permission-denied", "filesystem"));
        }
        if (lower.contains("no such file") || lower.contains("file not found") || lower.contains("config") && lower.contains("not found")
                || lower.contains("cannot find") || lower.contains("missing required")) {
            return logInsight(log, "missing-config", "HIGH", 5, "설정/파일 누락",
                    line,
                    "애플리케이션 시작에 필요한 설정 파일, 환경변수, Secret/ConfigMap 참조가 누락되었을 가능성이 큽니다.",
                    "앱이 필요한 설정을 찾지 못했습니다.",
                    "ConfigMap/Secret/env/volumeMount 이름과 namespace를 확인하세요.",
                    verificationCommand, previous, List.of("missing-config", "file-not-found"));
        }
        if (lower.contains("certificate") || lower.contains("x509") || lower.contains("tls") || lower.contains("ssl")) {
            return logInsight(log, "certificate-tls", "HIGH", 6, "인증서/TLS 오류",
                    line,
                    "인증서 만료, 신뢰 체인, SNI, TLS 설정 문제일 수 있습니다.",
                    "앱이 보안 연결을 만들 때 인증서를 신뢰하지 못하고 있습니다.",
                    "Secret의 인증서 만료일, CA bundle, 대상 endpoint TLS 설정을 확인하세요.",
                    verificationCommand, previous, List.of("tls", "certificate"));
        }
        if (lower.contains("connection refused") || lower.contains("connection timed out") || lower.contains("no route to host")
                || lower.contains("network is unreachable") || lower.contains("i/o timeout")) {
            return logInsight(log, "dependency-network", "MEDIUM", 7, "외부/내부 의존성 연결 실패",
                    line,
                    "애플리케이션이 의존 서비스에 연결하지 못합니다. Service, Endpoint, NetworkPolicy, DNS를 확인해야 합니다.",
                    "앱이 다른 서비스에 접속하려 했지만 연결되지 않았습니다.",
                    "대상 Service/Endpoint 존재 여부와 NetworkPolicy, DNS 해석을 확인하세요.",
                    verificationCommand, previous, List.of("network", "dependency"));
        }
        if (lower.contains("no such host") || lower.contains("unknown host") || lower.contains("temporary failure in name resolution")
                || lower.contains("dns")) {
            return logInsight(log, "dns-resolution", "MEDIUM", 8, "DNS 해석 실패",
                    line,
                    "서비스 이름이나 외부 도메인을 DNS로 해석하지 못하고 있습니다.",
                    "앱이 접속할 주소의 IP를 찾지 못했습니다.",
                    "Service 이름, namespace, CoreDNS 상태, search domain을 확인하세요.",
                    verificationCommand, previous, List.of("dns", "name-resolution"));
        }
        if (lower.contains("authentication failed") || lower.contains("access denied") || lower.contains("unauthorized")
                || lower.contains("forbidden") || lower.contains("invalid password")) {
            return logInsight(log, "dependency-auth", "MEDIUM", 9, "의존성 인증 실패",
                    line,
                    "DB/API/메시지브로커 같은 외부 의존성 인증정보가 맞지 않을 수 있습니다.",
                    "앱이 다른 시스템에 로그인하지 못했습니다.",
                    "Secret/env 값, 계정 권한, 토큰 만료 여부를 확인하세요.",
                    verificationCommand, previous, List.of("auth", "secret"));
        }
        if (lower.contains("liveness probe") || lower.contains("readiness probe") || lower.contains("health check")) {
            return logInsight(log, "probe-health", "MEDIUM", 10, "Probe/헬스체크 문제",
                    line,
                    "애플리케이션 상태와 Kubernetes probe 설정이 맞지 않을 수 있습니다.",
                    "Kubernetes가 앱이 준비됐는지 검사하는 과정에서 실패 신호가 있습니다.",
                    "probe path, port, initialDelaySeconds, timeoutSeconds를 확인하세요.",
                    verificationCommand, previous, List.of("probe", "health"));
        }
        if (lower.contains("traceback") || lower.contains("exception") || lower.contains("panic") || lower.contains("fatal")
                || lower.contains("emerg") || lower.contains("error") || lower.contains("failed") || lower.contains("crash")) {
            return logInsight(log, "application-error", "MEDIUM", 20, "애플리케이션 오류",
                    line,
                    "애플리케이션 내부 예외 또는 실패 신호입니다. Kubernetes 이벤트와 같은 시간대인지 확인해야 합니다.",
                    "앱 내부에서 오류가 발생했습니다. 이 오류가 Pod 재시작과 연결되는지 확인해야 합니다.",
                    "로그 범위를 늘리고 직전 배포/설정 변경, 관련 이벤트를 함께 확인하세요.",
                    verificationCommand, previous, List.of("application-error"));
        }
        if (lower.contains("warn") || lower.contains("development server")) {
            return logInsight(log, "runtime-hardening", "LOW", 30, "운영 설정 경고",
                    line,
                    "즉시 장애 원인보다는 운영 설정 또는 보안/품질 경고일 가능성이 큽니다.",
                    "앱은 동작할 수 있지만 운영 환경에 맞지 않은 설정일 수 있습니다.",
                    "프로덕션 서버 설정, debug mode, 보안 옵션을 확인하세요.",
                    verificationCommand, previous, List.of("warning", "hardening"));
        }
        return LogInsight.none(log);
    }

    /** AnalysisApplicationService의 logInsight 처리에 필요한 업무 로직을 수행한다. */
    private LogInsight logInsight(KubernetesNamespaceDiagnostics.DiagnosticPodLog log, String category, String severity,
                                  int priority, String title, String signal, String operatorMeaning,
                                  String beginnerExplanation, String recommendedNextAction,
                                  String verificationCommand, boolean previousLog, List<String> matchedPatterns) {
        return new LogInsight(true, category, severity, priority, title, valueOrBlank(log.namespace()),
                valueOrBlank(log.podName()), valueOrBlank(log.containerName()), truncate(signal, 260),
                operatorMeaning, beginnerExplanation, recommendedNextAction, verificationCommand, previousLog,
                matchedPatterns);
    }

    /** AnalysisApplicationService의 logVerificationCommand 처리에 필요한 업무 로직을 수행한다. */
    private String logVerificationCommand(KubernetesNamespaceDiagnostics.DiagnosticPodLog log, boolean previous) {
        return "kubectl logs pod/" + valueOrBlank(log.podName())
                + " -n " + valueOrBlank(log.namespace())
                + (valueOrBlank(log.containerName()).isBlank() ? "" : " -c " + valueOrBlank(log.containerName()))
                + (previous ? " --previous" : "")
                + " --tail=500";
    }

    /** AnalysisApplicationService의 logQualityGate 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode logQualityGate(LogInsight insight) {
        ObjectNode gate = objectMapper.createObjectNode();
        int score = switch (insight.category()) {
            case "port-startup", "memory-pressure", "missing-config", "certificate-tls" -> 88;
            case "port-conflict", "filesystem-permission" -> 84;
            case "dependency-network", "dns-resolution", "dependency-auth", "probe-health" -> 72;
            case "application-error" -> 62;
            default -> 48;
        };
        if (insight.previousLog()) {
            score = Math.min(95, score + 6);
        }
        gate.put("score", score);
        gate.put("level", score >= 85 ? "HIGH" : score >= 65 ? "MEDIUM" : "LOW");
        gate.put("validationStatus", score >= 85 ? "LIKELY_VERIFIED" : score >= 65 ? "LIKELY" : "NEEDS_MORE_EVIDENCE");
        gate.put("evidenceStrength", insight.previousLog() ? "previous-container-log" : "current-log-tail");
        gate.put("falsePositiveRisk", score >= 85 ? "LOW" : score >= 65 ? "MEDIUM" : "HIGH");
        ArrayNode missingChecks = gate.putArray("missingChecks");
        if (!insight.previousLog()) {
            missingChecks.add("이전 종료 컨테이너 로그");
        }
        if (!"port-startup".equals(insight.category()) && !"memory-pressure".equals(insight.category())) {
            missingChecks.add("같은 시간대 Kubernetes event");
        }
        missingChecks.add("대상 리소스 describe 결과");
        return gate;
    }

    /** AnalysisApplicationService의 logActionCandidates 처리에 필요한 업무 로직을 수행한다. */
    private ArrayNode logActionCandidates(LogInsight insight) {
        ArrayNode candidates = objectMapper.createArrayNode();
        String pod = insight.podName().isBlank() ? "-" : insight.podName();
        String namespace = insight.namespace().isBlank() ? "${namespace}" : insight.namespace();
        String container = insight.containerName().isBlank() ? "" : " -c " + insight.containerName();
        String logCommand = "kubectl logs pod/" + pod + " -n " + namespace + container
                + (insight.previousLog() ? " --previous" : "") + " --tail=500";
        String describePod = "kubectl describe pod/" + pod + " -n " + namespace;
        switch (insight.category()) {
            case "port-startup" -> {
                addActionCandidate(candidates, "P1", "VERIFY", "READ_ONLY",
                        "이전 종료 로그와 보안 컨텍스트 확인",
                        "포트 바인딩 실패가 실제 컨테이너 종료 원인인지 확인합니다.",
                        "previous 로그, runAsUser/runAsNonRoot, capabilities, containerPort/listen port를 함께 확인하세요.",
                        List.of(logCommand, describePod),
                        List.of("kubectl get pod/" + pod + " -n " + namespace + " -o yaml"),
                        List.of("containerPort가 80이면 애플리케이션 listen port를 8080 이상으로 바꾸는 방안을 우선 검토",
                                "80 포트를 유지해야 하면 NET_BIND_SERVICE capability 또는 rootless image 정책을 보안팀 기준으로 검토"));
                addActionCandidate(candidates, "P1", "CHANGE_PLAN", "CHANGE_REQUIRES_REVIEW",
                        "privileged port 사용 방식 수정",
                        "non-root 컨테이너가 1024 미만 포트를 열지 못해 CrashLoop가 반복될 수 있습니다.",
                        "권한 상승보다 앱 listen port 변경과 Service targetPort 조정을 우선 검토하세요.",
                        List.of("kubectl get deploy -n " + namespace + " -o yaml", "kubectl get svc -n " + namespace + " -o wide"),
                        List.of("kubectl rollout status deploy/<deployment> -n " + namespace, logCommand),
                        List.of("Deployment container port/listen port와 Service targetPort를 같은 값으로 정렬",
                                "securityContext.runAsNonRoot=true 환경에서는 8080 같은 high port 권장"));
            }
            case "port-conflict" -> addActionCandidate(candidates, "P1", "CHANGE_PLAN", "CHANGE_REQUIRES_REVIEW",
                    "중복 listen 포트 제거",
                    "같은 컨테이너 또는 sidecar가 동일 포트를 점유하고 있을 수 있습니다.",
                    "애플리케이션 설정, sidecar 포트, container args/env의 listen port를 대조하세요.",
                    List.of(logCommand, describePod),
                    List.of("kubectl rollout status deploy/<deployment> -n " + namespace),
                    List.of("sidecar와 main container 포트 분리", "앱 설정의 listen port 단일화"));
            case "memory-pressure" -> addActionCandidate(candidates, "P1", "CHANGE_PLAN", "CHANGE_REQUIRES_REVIEW",
                    "메모리 limit/JVM 옵션 검토",
                    "OOM 또는 메모리 할당 실패는 재시작과 성능 저하로 이어집니다.",
                    "requests/limits, lastState, JVM heap, 최근 배포 변경을 함께 확인하세요.",
                    List.of(describePod, "kubectl get pod/" + pod + " -n " + namespace + " -o yaml"),
                    List.of("kubectl rollout status deploy/<deployment> -n " + namespace),
                    List.of("limit 상향 전 memory leak 여부 확인", "JVM이면 -Xmx가 container limit을 넘지 않게 조정"));
            case "missing-config" -> addActionCandidate(candidates, "P1", "CHANGE_PLAN", "CHANGE_REQUIRES_REVIEW",
                    "누락 ConfigMap/Secret/env/volume 확인",
                    "애플리케이션이 시작에 필요한 설정을 찾지 못하고 있습니다.",
                    "volumeMount, envFrom, Secret/ConfigMap 이름과 namespace를 먼저 확인하세요.",
                    List.of(describePod, "kubectl get configmap,secret -n " + namespace),
                    List.of(logCommand, "kubectl describe pod/" + pod + " -n " + namespace),
                    List.of("누락된 ConfigMap/Secret 생성 또는 참조명 수정", "optional 설정 여부 검토"));
            case "filesystem-permission" -> addActionCandidate(candidates, "P2", "CHANGE_PLAN", "CHANGE_REQUIRES_REVIEW",
                    "파일 권한과 volume mount 정책 검토",
                    "컨테이너가 필요한 경로를 읽거나 쓰지 못하고 있습니다.",
                    "readOnly mount, runAsUser/fsGroup, image 내부 디렉터리 권한을 확인하세요.",
                    List.of(logCommand, describePod),
                    List.of(logCommand),
                    List.of("writable path를 emptyDir/PVC로 분리", "fsGroup 또는 initContainer 권한 조정 검토"));
            case "certificate-tls" -> addActionCandidate(candidates, "P2", "VERIFY", "READ_ONLY",
                    "인증서/CA bundle 확인",
                    "TLS 신뢰 체인 또는 인증서 만료 가능성이 있습니다.",
                    "Secret에 저장된 인증서, CA bundle, 대상 endpoint TLS 설정을 확인하세요.",
                    List.of(logCommand, "kubectl get secret -n " + namespace),
                    List.of(logCommand),
                    List.of("만료 인증서 교체", "CA bundle mount/환경변수 경로 확인"));
            case "dependency-network", "dns-resolution", "dependency-auth" -> addActionCandidate(candidates, "P2", "VERIFY", "READ_ONLY",
                    "의존 서비스 연결 조건 확인",
                    "애플리케이션이 외부 또는 내부 의존성에 연결하지 못합니다.",
                    "Service/Endpoint, DNS, NetworkPolicy, Secret 인증정보를 순서대로 확인하세요.",
                    List.of(logCommand, "kubectl get svc,endpoints,networkpolicy -n " + namespace),
                    List.of(logCommand),
                    List.of("Service name/namespace 수정", "Secret rotation 또는 NetworkPolicy allow rule 검토"));
            case "probe-health" -> addActionCandidate(candidates, "P2", "CHANGE_PLAN", "CHANGE_REQUIRES_REVIEW",
                    "Probe 경로와 지연 시간 조정 검토",
                    "앱은 뜨지만 Kubernetes health check와 맞지 않을 수 있습니다.",
                    "probe path/port, initialDelaySeconds, timeoutSeconds, 앱 준비 시간을 확인하세요.",
                    List.of(describePod, logCommand),
                    List.of("kubectl rollout status deploy/<deployment> -n " + namespace),
                    List.of("readiness/liveness path 수정", "초기 기동 시간이 길면 startupProbe 추가"));
            default -> addActionCandidate(candidates, "P3", "VERIFY", "READ_ONLY",
                    "로그 범위 확대와 이벤트 상관 확인",
                    "로그 신호만으로는 변경 조치를 결정하기 부족합니다.",
                    "tail row를 늘리고 같은 시간대 이벤트와 배포 변경을 비교하세요.",
                    List.of(logCommand, describePod),
                    List.of(logCommand),
                    List.of("반복 패턴이 확인되면 애플리케이션 설정 또는 최근 배포 diff 확인"));
        }
        return candidates;
    }

    /** AnalysisApplicationService의 addActionCandidate 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addActionCandidate(ArrayNode candidates, String priority, String actionType, String safetyLevel,
                                    String title, String rationale, String recommendedChange,
                                    List<String> preflightCommands, List<String> validationCommands,
                                    List<String> manifestHints) {
        ObjectNode candidate = candidates.addObject();
        candidate.put("priority", priority);
        candidate.put("actionType", actionType);
        candidate.put("safetyLevel", safetyLevel);
        candidate.put("title", title);
        candidate.put("rationale", rationale);
        candidate.put("recommendedChange", recommendedChange);
        ArrayNode preflight = candidate.putArray("preflightCommands");
        preflightCommands.forEach(preflight::add);
        ArrayNode validation = candidate.putArray("validationCommands");
        validationCommands.forEach(validation::add);
        ArrayNode hints = candidate.putArray("manifestHints");
        manifestHints.forEach(hints::add);
    }

    /** AnalysisApplicationService의 actionRecommendations 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode actionRecommendations(KubernetesNamespaceDiagnostics diagnostics, ArrayNode issueGroups) {
        ObjectNode recommendations = objectMapper.createObjectNode();
        List<LogInsight> insights = logInsights(diagnostics);
        recommendations.put("summary", insights.isEmpty()
                ? "현재 로그 기반 변경 조치 후보는 없습니다. 리소스 상태와 이벤트 기준선 점검을 우선하세요."
                : "로그 위험 신호를 기반으로 조치 후보를 검증 명령, 변경 가이드, 조치 후 확인으로 나눴습니다.");
        recommendations.put("beginnerSummary", "바로 변경하기보다 먼저 조회 명령으로 원인을 확인하고, 안전한 변경 방향만 후보로 보여줍니다.");
        recommendations.put("totalCandidates", insights.stream().mapToInt(insight -> logActionCandidates(insight).size()).sum());
        recommendations.put("issueGroupCount", issueGroups.size());
        ArrayNode items = recommendations.putArray("items");
        for (LogInsight insight : insights.stream().limit(6).toList()) {
            for (JsonNode candidateNode : logActionCandidates(insight)) {
                if (!(candidateNode instanceof ObjectNode candidate)) {
                    continue;
                }
                ObjectNode item = items.addObject();
                item.put("source", "log-intelligence");
                item.put("category", insight.category());
                item.put("severity", insight.severity());
                item.put("podName", insight.podName());
                item.put("containerName", insight.containerName());
                item.put("signal", insight.signal());
                item.setAll(candidate);
                item.set("qualityGate", logQualityGate(insight));
            }
        }
        for (JsonNode group : issueGroups) {
            if (items.size() >= 12) {
                break;
            }
            ObjectNode item = items.addObject();
            item.put("source", "issue-group");
            item.put("priority", severityToPriority(group.path("severity").asText("LOW")));
            item.put("actionType", "VERIFY");
            item.put("safetyLevel", "READ_ONLY");
            item.put("category", group.path("category").asText("resource-health"));
            item.put("severity", group.path("severity").asText("INFO"));
            item.put("title", group.path("title").asText("Issue group 검증"));
            item.put("rationale", group.path("rootCause").asText("원인 후보를 검증합니다."));
            item.put("recommendedChange", group.path("recommendedNextAction").asText("관련 리소스와 이벤트를 먼저 확인하세요."));
            item.put("podName", group.path("representativeResourceName").asText("-"));
            item.put("containerName", "");
            item.put("signal", group.path("rootCause").asText(""));
            ArrayNode preflight = item.putArray("preflightCommands");
            preflight.add(describeCommand(group.path("representativeResourceKind").asText("Pod"),
                    group.path("representativeResourceName").asText("-")));
            ArrayNode validation = item.putArray("validationCommands");
            validation.add("kubectl get events -n ${namespace} --sort-by=.lastTimestamp");
            item.putArray("manifestHints");
            ObjectNode gate = item.putObject("qualityGate");
            gate.put("score", "HIGH".equals(confidenceForGroup(group)) ? 84 : "MEDIUM".equals(confidenceForGroup(group)) ? 66 : 42);
            gate.put("level", confidenceForGroup(group));
            gate.put("validationStatus", "HIGH".equals(confidenceForGroup(group)) ? "LIKELY_VERIFIED" : "NEEDS_MORE_EVIDENCE");
        }
        return recommendations;
    }

    /** AnalysisApplicationService의 appendActionRecommendationNextActions 처리에 필요한 업무 로직을 수행한다. */
    private void appendActionRecommendationNextActions(ObjectNode root, ObjectNode actionRecommendations) {
        ArrayNode nextActions = arrayField(root, "nextActions");
        int added = 0;
        for (JsonNode item : actionRecommendations.path("items")) {
            if (added >= 4) {
                return;
            }
            ObjectNode action = nextActions.addObject();
            action.put("priority", item.path("priority").asText("P2"));
            action.put("ownerHint", "operator");
            action.put("action", item.path("title").asText("조치 후보 검증"));
            action.put("verification", item.path("recommendedChange").asText("-"));
            action.put("source", "actionRecommendations");
            added++;
        }
    }

    /** AnalysisApplicationService의 analysisQuality 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode analysisQuality(ObjectNode root, KubernetesNamespaceDiagnostics diagnostics,
                                       ArrayNode issueGroups, ObjectNode actionRecommendations) {
        long problemResourceCount = diagnostics.resources().stream().filter(this::isProblemResource).count();
        long warningEventCount = diagnostics.events().stream().filter(this::isWarningEvent).count();
        List<LogInsight> insights = logInsights(diagnostics);
        int actionCount = arraySize(actionRecommendations.path("items"));
        int evidenceAlignment = Math.min(95, 30
                + (problemResourceCount > 0 ? 18 : 0)
                + (warningEventCount > 0 ? 18 : 0)
                + (!insights.isEmpty() ? 22 : 0)
                + (!issueGroups.isEmpty() ? 15 : 0));
        int logSpecificity = insights.isEmpty() ? 45 : Math.max(55, 96 - insights.get(0).priority() * 4);
        int actionability = actionCount == 0 ? 38 : Math.min(95, 55 + actionCount * 5);
        int coverage = Math.min(95, 35
                + (diagnostics.resources().isEmpty() ? 0 : 20)
                + (diagnostics.events().isEmpty() ? 0 : 20)
                + (diagnostics.podLogs().isEmpty() ? 0 : 15)
                + (root.path("commandSafety").isMissingNode() ? 0 : 5));
        int overall = (evidenceAlignment + logSpecificity + actionability + coverage) / 4;
        ObjectNode quality = objectMapper.createObjectNode();
        quality.put("score", overall);
        quality.put("level", overall >= 82 ? "HIGH" : overall >= 62 ? "MEDIUM" : "LOW");
        quality.put("summary", "Evidence alignment, log specificity, actionability, coverage 기준으로 분석 품질을 계산했습니다.");
        quality.put("beginnerSummary", "AI 답변이 실제 Kubernetes 상태, 이벤트, 로그, 실행 가능한 확인 방법과 얼마나 잘 연결되는지 본 점수입니다.");
        ArrayNode dimensions = quality.putArray("dimensions");
        addQualityDimension(dimensions, "Evidence Alignment", evidenceAlignment,
                "문제 리소스, Warning 이벤트, 로그, issue group이 같은 방향을 가리키는지 평가합니다.");
        addQualityDimension(dimensions, "Log Specificity", logSpecificity,
                "로그가 일반 warning인지, 종료 원인에 가까운 previous/emerg/fatal/OOM 신호인지 평가합니다.");
        addQualityDimension(dimensions, "Actionability", actionability,
                "운영자가 바로 검증하거나 변경 계획으로 전환할 수 있는 후보가 충분한지 평가합니다.");
        addQualityDimension(dimensions, "Coverage", coverage,
                "리소스, 이벤트, 로그가 함께 수집되어 사각지대가 적은지 평가합니다.");
        ArrayNode risks = quality.putArray("qualityRisks");
        if (insights.isEmpty()) {
            risks.add("명확한 로그 신호가 없어 이벤트/리소스 상태 중심으로만 판단합니다.");
        }
        if (warningEventCount == 0) {
            risks.add("Warning 이벤트가 없어 Kubernetes가 기록한 반복 증거가 제한적입니다.");
        }
        if (actionCount == 0) {
            risks.add("즉시 실행 가능한 조치 후보가 낮아 추가 수동 진단이 필요합니다.");
        }
        if (risks.isEmpty()) {
            risks.add("주요 evidence와 action candidate가 연결되어 운영 판단에 사용할 수 있습니다.");
        }
        return quality;
    }

    /** AnalysisApplicationService의 addQualityDimension 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addQualityDimension(ArrayNode dimensions, String name, int score, String explanation) {
        ObjectNode item = dimensions.addObject();
        item.put("name", name);
        item.put("score", score);
        item.put("level", score >= 82 ? "HIGH" : score >= 62 ? "MEDIUM" : "LOW");
        item.put("explanation", explanation);
    }

    /** AnalysisApplicationService의 remediationPlan 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode remediationPlan(KubernetesNamespaceDiagnostics diagnostics, ArrayNode issueGroups) {
        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("summary", issueGroups.isEmpty()
                ? "현재 Kubernetes API evidence 기준 즉시 처리할 issue group이 없습니다. 기준선 점검을 먼저 수행하세요."
                : "중복 이벤트와 문제 리소스를 " + issueGroups.size() + "개 issue group으로 압축하고, 검증 우선 순서로 처리 계획을 생성했습니다.");
        plan.put("strategy", "VERIFY_BEFORE_CHANGE");
        plan.put("estimatedRisk", issueGroups.isEmpty() ? "LOW" : highestSeverity(issueGroups));
        plan.put("safetyNote", "자동 변경 명령은 제공하지 않습니다. 모든 단계는 조회/검증 중심이며, 실제 수정은 운영 승인 절차에서 수행하세요.");
        ArrayNode stages = plan.putArray("stages");
        if (issueGroups.isEmpty()) {
            ObjectNode stage = stages.addObject();
            stage.put("order", 1);
            stage.put("title", "Namespace 기준선 확인");
            stage.put("objective", "현재 리소스, 이벤트, 로그 기준선을 확보합니다.");
            stage.put("riskLevel", "LOW");
            stage.put("expectedResult", "즉시 조치할 반복 Warning 또는 비정상 리소스가 없는지 확인합니다.");
            stage.put("rollbackNote", "조회 명령만 포함되어 rollback이 필요하지 않습니다.");
            stage.putArray("relatedIssueGroupIds");
            ArrayNode commands = stage.putArray("commands");
            String namespace = diagnostics.resources().stream()
                    .map(KubernetesNamespaceDiagnostics.DiagnosticResource::namespace)
                    .filter(value -> !valueOrBlank(value).isBlank())
                    .findFirst()
                    .orElse("${namespace}");
            addPlanCommand(commands, "리소스 기준선 확인", "kubectl get all -n " + namespace,
                    "namespace 내 주요 workload와 service 상태를 확인합니다.", false);
            addPlanCommand(commands, "최근 이벤트 확인", "kubectl get events -n " + namespace + " --sort-by=.lastTimestamp",
                    "최근 Warning 이벤트가 증가 중인지 확인합니다.", false);
            return plan;
        }

        int order = 1;
        for (JsonNode group : issueGroups) {
            if (order > 5) {
                break;
            }
            ObjectNode stage = stages.addObject();
            String issueGroupId = group.path("issueGroupId").asText("IG-" + order);
            String resourceKind = group.path("representativeResourceKind").asText("");
            String resourceName = group.path("representativeResourceName").asText("");
            String namespace = group.path("namespace").asText("${namespace}");
            if (namespace.isBlank()) {
                namespace = "${namespace}";
            }
            stage.put("order", order);
            stage.put("title", order + ". " + group.path("title").asText("Issue group 검증"));
            stage.put("objective", group.path("fixReadiness").asText("").equals("READY_TO_FIX")
                    ? "명확히 식별된 참조/설정 문제를 조치하기 전에 누락 대상과 영향 범위를 검증합니다."
                    : "변경 전 원인을 좁히고 영향 범위를 확인합니다.");
            stage.put("riskLevel", group.path("severity").asText("MEDIUM"));
            stage.put("expectedResult", "원인 후보, 영향 리소스, 반복 이벤트 증가 여부가 확인됩니다.");
            stage.put("rollbackNote", "이 단계는 조회/검증 명령만 포함합니다. 실제 수정 후에는 아래 조치 후 검증 명령으로 회복 여부를 확인하세요.");
            stage.putArray("relatedIssueGroupIds").add(issueGroupId);
            ArrayNode commands = stage.putArray("commands");
            if (!resourceKind.isBlank() && !resourceName.isBlank() && !"-".equals(resourceName)) {
                addPlanCommand(commands, "대상 리소스 상세", describeCommand(resourceKind, resourceName).replace("${namespace}", namespace),
                        "condition, event, volume, image, probe, replica 상태를 한 번에 확인합니다.", false);
                addPlanCommand(commands, "대상 이벤트 추적",
                        "kubectl get events -n " + namespace + " --field-selector involvedObject.name=" + resourceName + " --sort-by=.lastTimestamp",
                        "같은 원인이 반복 중인지, count가 증가하는지 확인합니다.", false);
                if ("Pod".equals(resourceKind)) {
                    addPlanCommand(commands, "Pod 로그 확인", "kubectl logs pod/" + resourceName + " -n " + namespace + " --tail=300",
                            "상태/이벤트와 애플리케이션 로그가 같은 원인을 가리키는지 확인합니다.", false);
                }
            } else {
                addPlanCommand(commands, "Namespace 이벤트 확인", "kubectl get events -n " + namespace + " --sort-by=.lastTimestamp",
                        "대상 리소스가 불명확한 반복 이벤트를 시간순으로 확인합니다.", false);
            }
            if (arraySize(group.path("relatedReferences")) > 0) {
                addPlanCommand(commands, "참조 리소스 확인", "kubectl get configmap,secret,pvc -n " + namespace,
                        "FailedMount 또는 volume 참조 오류에서 누락된 ConfigMap/Secret/PVC가 있는지 확인합니다.", false);
            }
            if ("port-mismatch".equals(group.path("category").asText("")) && !resourceName.isBlank()) {
                addPlanCommand(commands, "Service 포트 매핑 확인", "kubectl describe svc/" + resourceName + " -n " + namespace,
                        "Service port/targetPort, selector, endpoint 연결 상태를 한 번에 확인합니다.", false);
                addPlanCommand(commands, "Endpoint 연결 확인", "kubectl get endpoints/" + resourceName + " -n " + namespace + " -o wide",
                        "Service가 실제 Pod IP와 포트로 연결되는지 확인합니다.", false);
                addPlanCommand(commands, "선택된 Pod 포트 확인", "kubectl get pods -n " + namespace + " --show-labels -o wide",
                        "Service selector에 잡힌 Pod와 실제 컨테이너 listen 포트를 대조합니다.", false);
            }
            addPlanCommand(commands, "조치 후 상태 확인",
                    resourceKind.isBlank() || resourceName.isBlank() || "-".equals(resourceName)
                            ? "kubectl get pods -n " + namespace + " -o wide"
                            : "kubectl get " + kubectlResourceName(resourceKind) + "/" + resourceName + " -n " + namespace,
                    "수정 후 status/ready 상태가 개선됐는지 확인합니다.", false);
            order++;
        }
        return plan;
    }

    /** AnalysisApplicationService의 addProblemResourceCard 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addProblemResourceCard(ArrayNode cards, KubernetesNamespaceDiagnostics diagnostics,
                                        KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        List<KubernetesNamespaceDiagnostics.DiagnosticEvent> matchingEvents = diagnostics.events().stream()
                .filter(this::isWarningEvent)
                .filter(event -> eventMatchesResource(event, resource.resourceType(), resource.resourceName()))
                .limit(5)
                .toList();
        List<KubernetesNamespaceDiagnostics.DiagnosticPodLog> matchingLogs = diagnostics.podLogs().stream()
                .filter(this::isHighSignalLog)
                .filter(log -> "Pod".equals(resource.resourceType()) && log.podName().equals(resource.resourceName()))
                .limit(3)
                .toList();
        ObjectNode card = cards.addObject();
        card.put("title", problemTitle(resource, matchingEvents));
        card.put("severity", problemSeverity(resource, matchingEvents));
        card.put("resourceKind", resource.resourceType());
        card.put("resourceName", resource.resourceName());
        card.put("namespace", valueOrBlank(resource.namespace()));
        card.put("status", valueOrBlank(resource.status()));
        card.put("rootCause", rootCauseSummary(resource, matchingEvents));
        card.put("recommendedNextAction", recommendedNextAction(resource, matchingEvents));
        putConfidence(card, resource, matchingEvents, matchingLogs);
        putFixReadiness(card, resource, matchingEvents);
        putEvidenceTrace(card, resource, matchingEvents, matchingLogs);
        putBeforeAfterCommands(card, resource, matchingEvents);
        putRelatedReferences(card, resource, matchingEvents);
    }

    /** AnalysisApplicationService의 addWarningEventCard 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addWarningEventCard(ArrayNode cards, KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        ObjectNode card = cards.addObject();
        card.put("title", valueOrBlank(event.reason()) + " 반복 이벤트");
        card.put("severity", event.count() != null && event.count() >= 5 ? "HIGH" : "MEDIUM");
        card.put("resourceKind", valueOrBlank(event.involvedKind()));
        card.put("resourceName", valueOrBlank(event.involvedName()));
        card.put("namespace", valueOrBlank(event.namespace()));
        card.put("status", "Warning count=" + (event.count() == null ? 0 : event.count()));
        card.put("rootCause", valueOrBlank(event.reason()) + " 이벤트가 반복되어 운영 영향 가능성이 있습니다.");
        card.put("recommendedNextAction", performanceEventRecommendation(event));
        card.put("confidenceLevel", event.count() != null && event.count() >= 3 ? "MEDIUM" : "LOW");
        card.put("confidenceScore", event.count() != null && event.count() >= 3 ? 65 : 45);
        card.put("confidenceReason", "Warning event signal only. 리소스 상태와 로그를 추가 확인하세요.");
        card.put("fixReadiness", fixReadinessForEvent(event));
        card.put("fixReadinessReason", fixReadinessReasonForEvent(event));
        ArrayNode trace = card.putArray("evidenceTrace");
        addTrace(trace, "Event", valueOrBlank(event.reason()), "count=" + (event.count() == null ? 0 : event.count())
                + " message=" + truncate(event.message(), 260));
        putBeforeAfterCommands(card, valueOrBlank(event.namespace()), valueOrBlank(event.involvedKind()),
                valueOrBlank(event.involvedName()), List.of(event));
        putRelatedReferences(card, null, List.of(event));
    }

    /** AnalysisApplicationService의 addLogSignalCard 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addLogSignalCard(ArrayNode cards, KubernetesNamespaceDiagnostics.DiagnosticPodLog log) {
        LogInsight insight = logInsight(log);
        ObjectNode card = cards.addObject();
        card.put("title", valueOrBlank(insight.title()).isBlank() ? "Pod 로그 위험 신호" : insight.title());
        card.put("severity", insight.severity());
        card.put("resourceKind", "Pod");
        card.put("resourceName", log.podName());
        card.put("namespace", valueOrBlank(log.namespace()));
        card.put("status", "Log category=" + insight.category());
        card.put("rootCause", insight.signal());
        card.put("recommendedNextAction", insight.recommendedNextAction());
        card.put("beginnerExplanation", insight.beginnerExplanation());
        card.put("confidenceLevel", insight.priority() <= 2 ? "MEDIUM" : "LOW");
        card.put("confidenceScore", insight.priority() <= 2 ? 70 : 45);
        card.put("confidenceReason", insight.operatorMeaning());
        card.put("fixReadiness", "NEEDS_VERIFICATION");
        card.put("fixReadinessReason", "로그 인사이트는 원인 후보입니다. 변경 전 상태, 이벤트, previous 로그를 함께 검증하세요.");
        ArrayNode trace = card.putArray("evidenceTrace");
        addTrace(trace, "Log", "Pod/" + log.podName() + " container=" + log.containerName(), insight.signal());
        putBeforeAfterCommands(card, valueOrBlank(log.namespace()), "Pod", log.podName(), List.of());
    }

    /** AnalysisApplicationService의 putConfidence 처리에 필요한 업무 로직을 수행한다. */
    private void putConfidence(ObjectNode card, KubernetesNamespaceDiagnostics.DiagnosticResource resource,
                               List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events,
                               List<KubernetesNamespaceDiagnostics.DiagnosticPodLog> logs) {
        int score = 45;
        String level = "LOW";
        String reason = "단일 Kubernetes status 신호 기반입니다.";
        if (!events.isEmpty() && !logs.isEmpty()) {
            score = 90;
            level = "HIGH";
            reason = "리소스 상태, Warning event, high-signal log가 같은 대상을 가리킵니다.";
        } else if (!events.isEmpty()) {
            score = 78;
            level = "HIGH";
            reason = "리소스 비정상 상태와 Warning event가 같은 대상을 가리킵니다.";
        } else if (isProblemResource(resource)) {
            score = 60;
            level = "MEDIUM";
            reason = "리소스 status가 비정상이지만 event/log 상관관계는 추가 확인이 필요합니다.";
        }
        card.put("confidenceLevel", level);
        card.put("confidenceScore", score);
        card.put("confidenceReason", reason);
    }

    /** AnalysisApplicationService의 putFixReadiness 처리에 필요한 업무 로직을 수행한다. */
    private void putFixReadiness(ObjectNode card, KubernetesNamespaceDiagnostics.DiagnosticResource resource,
                                 List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events) {
        String readiness = events.stream().anyMatch(this::isClearlyFixableEvent) ? "READY_TO_FIX"
                : events.stream().anyMatch(this::requiresVerificationEvent) ? "NEEDS_VERIFICATION"
                : valueOrBlank(resource.status()).toLowerCase().contains("pending") ? "NEEDS_VERIFICATION"
                : "OBSERVE";
        card.put("fixReadiness", readiness);
        card.put("fixReadinessReason", switch (readiness) {
            case "READY_TO_FIX" -> "이벤트 메시지에서 누락된 ConfigMap/Secret/PVC 등 구체적인 조치 대상이 확인됩니다.";
            case "NEEDS_VERIFICATION" -> "스케줄링, 볼륨, 이미지, 프로브, 노드 상태 중 원인을 추가 검증해야 합니다.";
            default -> "즉시 변경보다 상태 추적과 추가 evidence 확보가 우선입니다.";
        });
    }

    /** AnalysisApplicationService의 putEvidenceTrace 처리에 필요한 업무 로직을 수행한다. */
    private void putEvidenceTrace(ObjectNode card, KubernetesNamespaceDiagnostics.DiagnosticResource resource,
                                  List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events,
                                  List<KubernetesNamespaceDiagnostics.DiagnosticPodLog> logs) {
        ArrayNode trace = card.putArray("evidenceTrace");
        addTrace(trace, "Resource", resource.resourceType() + "/" + resource.resourceName(),
                "status=" + valueOrBlank(resource.status()) + " summary=" + truncate(resource.summaryJson(), 220));
        events.forEach(event -> addTrace(trace, "Event", valueOrBlank(event.reason()),
                "count=" + (event.count() == null ? 0 : event.count()) + " message=" + truncate(event.message(), 260)));
        logs.forEach(log -> addTrace(trace, "Log", "Pod/" + log.podName() + " container=" + log.containerName(),
                firstLogSignal(log.log())));
    }

    /** AnalysisApplicationService의 putBeforeAfterCommands 처리에 필요한 업무 로직을 수행한다. */
    private void putBeforeAfterCommands(ObjectNode card, KubernetesNamespaceDiagnostics.DiagnosticResource resource,
                                        List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events) {
        putBeforeAfterCommands(card, valueOrBlank(resource.namespace()), resource.resourceType(), resource.resourceName(), events);
    }

    /** AnalysisApplicationService의 putBeforeAfterCommands 처리에 필요한 업무 로직을 수행한다. */
    private void putBeforeAfterCommands(ObjectNode card, String namespace, String resourceKind, String resourceName,
                                        List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events) {
        String ns = namespace.isBlank() ? "${namespace}" : namespace;
        ArrayNode before = card.putArray("beforeCommands");
        addCommand(before, "리소스 상세 확인", describeCommand(resourceKind, resourceName).replace("${namespace}", ns),
                "현재 status, condition, volume, image, probe, event 연결을 한 번에 확인합니다.");
        if (!resourceName.isBlank() && !"-".equals(resourceName)) {
            addCommand(before, "관련 이벤트 확인",
                    "kubectl get events -n " + ns + " --field-selector involvedObject.name=" + resourceName + " --sort-by=.lastTimestamp",
                    "동일 원인이 반복 중인지, 최초/최근 발생 시점을 확인합니다.");
        } else {
            addCommand(before, "Namespace 이벤트 확인", "kubectl get events -n " + ns + " --sort-by=.lastTimestamp",
                    "namespace 전반의 Warning event 흐름을 확인합니다.");
        }
        if ("Pod".equals(resourceKind)) {
            addCommand(before, "Pod 로그 확인", "kubectl logs pod/" + resourceName + " -n " + ns + " --tail=200",
                    "상태/이벤트와 애플리케이션 로그가 같은 원인을 가리키는지 확인합니다.");
        }
        ArrayNode after = card.putArray("afterCommands");
        addCommand(after, "상태 정상화 확인", "kubectl get " + kubectlResourceName(resourceKind) + "/" + resourceName + " -n " + ns,
                "조치 후 status와 ready 상태가 개선됐는지 확인합니다.");
        addCommand(after, "이벤트 재발 여부 확인",
                "kubectl get events -n " + ns + " --field-selector involvedObject.name=" + resourceName + " --sort-by=.lastTimestamp",
                "동일 Warning event가 더 이상 증가하지 않는지 확인합니다.");
        if (events.stream().anyMatch(event -> valueOrBlank(event.reason()).equalsIgnoreCase("FailedMount"))) {
            addCommand(after, "Mount 참조 리소스 확인", "kubectl get configmap,secret,pvc -n " + ns,
                    "FailedMount 조치 후 참조 리소스가 namespace에 존재하는지 확인합니다.");
        }
    }

    /** AnalysisApplicationService의 putRelatedReferences 처리에 필요한 업무 로직을 수행한다. */
    private void putRelatedReferences(ObjectNode card, KubernetesNamespaceDiagnostics.DiagnosticResource resource,
                                      List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events) {
        ArrayNode references = card.putArray("relatedReferences");
        if (resource != null) {
            JsonNode summary = safeReadTree(resource.summaryJson());
            JsonNode volumes = summary.path("volumes");
            if (volumes.isArray()) {
                volumes.forEach(volume -> {
                    String type = valueOrBlank(volume.path("type").asText());
                    String sourceName = valueOrBlank(volume.path("sourceName").asText());
                    if ((type.equals("ConfigMap") || type.equals("Secret") || type.equals("PersistentVolumeClaim"))
                            && !sourceName.isBlank()) {
                        addReference(references, type, sourceName, valueOrBlank(volume.path("name").asText()) + " volume 참조");
                    }
                });
            }
        }
        events.forEach(event -> addReferencesFromEventMessage(references, event));
    }

    /** AnalysisApplicationService의 enrichRootCauseEvidence 처리에 필요한 업무 로직을 수행한다. */
    private void enrichRootCauseEvidence(ObjectNode root, KubernetesNamespaceDiagnostics diagnostics) {
        JsonNode rootCauses = root.get("rootCauses");
        if (rootCauses == null || !rootCauses.isArray()) {
            return;
        }
        for (JsonNode node : rootCauses) {
            if (!(node instanceof ObjectNode rootCause)) {
                continue;
            }
            KubernetesNamespaceDiagnostics.DiagnosticResource resource = findResourceForAnalysisNode(rootCause, diagnostics);
            if (resource == null) {
                continue;
            }
            List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events = diagnostics.events().stream()
                    .filter(this::isWarningEvent)
                    .filter(event -> eventMatchesResource(event, resource.resourceType(), resource.resourceName()))
                    .limit(5)
                    .toList();
            List<KubernetesNamespaceDiagnostics.DiagnosticPodLog> logs = diagnostics.podLogs().stream()
                    .filter(this::isHighSignalLog)
                    .filter(log -> "Pod".equals(resource.resourceType()) && log.podName().equals(resource.resourceName()))
                    .limit(3)
                    .toList();
            rootCause.put("resourceKind", resource.resourceType());
            rootCause.put("resourceName", resource.resourceName());
            putConfidence(rootCause, resource, events, logs);
            putFixReadiness(rootCause, resource, events);
            putEvidenceTrace(rootCause, resource, events, logs);
        }
    }

    /** AnalysisApplicationService의 issueGroupFromEvent 처리 조건의 충족 여부를 판단한다. */
    private IssueGroupAccumulator issueGroupFromEvent(String key, KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        IssueGroupAccumulator group = new IssueGroupAccumulator(key);
        group.category = issueCategoryFromEvent(event);
        group.title = valueOrBlank(event.reason()) + " · " + valueOrBlank(event.involvedKind()) + "/" + valueOrBlank(event.involvedName());
        group.severity = event.count() != null && event.count() >= 5 ? "HIGH" : "MEDIUM";
        group.fixReadiness = fixReadinessForEvent(event);
        group.rootCause = valueOrBlank(event.reason()) + ": " + truncate(event.message(), 240);
        group.recommendedNextAction = performanceEventRecommendation(event);
        group.resourceKind = valueOrBlank(event.involvedKind());
        group.resourceName = valueOrBlank(event.involvedName());
        group.namespace = valueOrBlank(event.namespace());
        return group;
    }

    /** AnalysisApplicationService의 issueGroupFromResource 처리 조건의 충족 여부를 판단한다. */
    private IssueGroupAccumulator issueGroupFromResource(String key, KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        IssueGroupAccumulator group = new IssueGroupAccumulator(key);
        group.category = categoryForResource(resource.resourceType());
        group.title = resource.resourceType() + "/" + resource.resourceName() + " 비정상 상태";
        group.severity = problemSeverity(resource, List.of());
        group.fixReadiness = valueOrBlank(resource.status()).toLowerCase().contains("pending") ? "NEEDS_VERIFICATION" : "OBSERVE";
        group.rootCause = "status=" + valueOrBlank(resource.status());
        group.recommendedNextAction = performanceRecommendation(resource);
        group.resourceKind = resource.resourceType();
        group.resourceName = resource.resourceName();
        group.namespace = valueOrBlank(resource.namespace());
        return group;
    }

    /** AnalysisApplicationService의 issueGroupFromLog 처리 조건의 충족 여부를 판단한다. */
    private IssueGroupAccumulator issueGroupFromLog(String key, KubernetesNamespaceDiagnostics.DiagnosticPodLog log) {
        LogInsight insight = logInsight(log);
        IssueGroupAccumulator group = new IssueGroupAccumulator(key);
        group.category = insight.matched() ? insight.category() : "log-pattern";
        group.title = (insight.matched() ? insight.title() : "Pod 로그 위험 신호") + " · " + log.podName();
        group.severity = insight.matched() ? insight.severity() : "MEDIUM";
        group.fixReadiness = "NEEDS_VERIFICATION";
        group.rootCause = insight.matched() ? insight.signal() : firstLogSignal(log.log());
        group.recommendedNextAction = insight.matched()
                ? insight.recommendedNextAction()
                : "로그 row 수를 늘려 반복 여부를 확인하고 애플리케이션 설정/의존성 상태를 점검하세요.";
        group.resourceKind = "Pod";
        group.resourceName = log.podName();
        group.namespace = valueOrBlank(log.namespace());
        return group;
    }

    /** AnalysisApplicationService의 issueGroupJson 처리 조건의 충족 여부를 판단한다. */
    private ObjectNode issueGroupJson(IssueGroupAccumulator group, int index) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("issueGroupId", "IG-" + index);
        node.put("groupKey", group.key);
        node.put("title", valueOrBlank(group.title));
        node.put("category", valueOrBlank(group.category));
        node.put("severity", valueOrBlank(group.severity));
        node.put("fixReadiness", valueOrBlank(group.fixReadiness));
        node.put("rootCause", valueOrBlank(group.rootCause));
        node.put("recommendedNextAction", valueOrBlank(group.recommendedNextAction));
        node.put("representativeResourceKind", valueOrBlank(group.resourceKind));
        node.put("representativeResourceName", valueOrBlank(group.resourceName));
        node.put("namespace", valueOrBlank(group.namespace));
        node.put("affectedResourceCount", group.affectedResources.size());
        node.put("eventOccurrenceCount", group.eventOccurrences);
        node.put("logSignalCount", group.logSignals);
        node.put("score", group.score());
        ArrayNode resources = node.putArray("affectedResources");
        group.affectedResources.stream().limit(8).forEach(resources::add);
        ArrayNode evidence = node.putArray("evidenceSummary");
        group.evidence.stream().limit(8).forEach(evidence::add);
        ArrayNode references = node.putArray("relatedReferences");
        group.relatedReferences.forEach(reference -> {
            ObjectNode item = references.addObject();
            item.put("kind", reference.kind());
            item.put("name", reference.name());
            item.put("reason", reference.reason());
        });
        return node;
    }

    /** AnalysisApplicationService의 issueGroupKey 처리 조건의 충족 여부를 판단한다. */
    private String issueGroupKey(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String reason = valueOrBlank(event.reason()).toLowerCase();
        String reference = firstReferenceSignature(event.message());
        if (!reference.isBlank()) {
            return "event:" + reason + ":" + reference.toLowerCase();
        }
        return "event:" + reason + ":" + valueOrBlank(event.involvedKind()).toLowerCase()
                + "/" + valueOrBlank(event.involvedName()).toLowerCase();
    }

    /** AnalysisApplicationService의 firstReferenceSignature 처리에 필요한 업무 로직을 수행한다. */
    private String firstReferenceSignature(String message) {
        String configMap = extractReferenceName(message, "configmap \"?([A-Za-z0-9_.-]+)\"?");
        if (!configMap.isBlank()) {
            return "ConfigMap/" + configMap;
        }
        String secret = extractReferenceName(message, "secret \"?([A-Za-z0-9_.-]+)\"?");
        if (!secret.isBlank()) {
            return "Secret/" + secret;
        }
        String pvc = extractReferenceName(message, "(?:persistentvolumeclaim|pvc) \"?([A-Za-z0-9_.-]+)\"?");
        if (!pvc.isBlank()) {
            return "PersistentVolumeClaim/" + pvc;
        }
        return "";
    }

    /** AnalysisApplicationService의 extractReferenceName 처리에 필요한 업무 로직을 수행한다. */
    private String extractReferenceName(String message, String regex) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(valueOrBlank(message));
        return matcher.find() ? matcher.group(1) : "";
    }

    /** AnalysisApplicationService의 statusCategory 처리에 필요한 업무 로직을 수행한다. */
    private String statusCategory(String status) {
        String value = valueOrBlank(status).toLowerCase();
        if (value.contains("pending")) {
            return "pending";
        }
        if (value.contains("crash") || value.contains("backoff")) {
            return "crashloop";
        }
        if (value.contains("failed") || value.contains("error")) {
            return "failed";
        }
        if (value.contains("unknown")) {
            return "unknown";
        }
        return value.isBlank() ? "unknown" : "unhealthy";
    }

    /** AnalysisApplicationService의 issueCategoryFromEvent 처리 조건의 충족 여부를 판단한다. */
    private String issueCategoryFromEvent(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String reason = valueOrBlank(event.reason()).toLowerCase();
        String message = valueOrBlank(event.message()).toLowerCase();
        if (reason.contains("failedmount") || message.contains("volume") || message.contains("pvc")
                || message.contains("configmap") || message.contains("secret")) {
            return "storage-config";
        }
        if (reason.contains("failedscheduling")) {
            return "scheduling-capacity";
        }
        if (reason.contains("backoff") || reason.contains("pull") || reason.contains("failed")) {
            return "runtime-startup";
        }
        if (reason.contains("unhealthy")) {
            return "probe-health";
        }
        return eventCategory(event);
    }

    /** AnalysisApplicationService의 categoryForResource 처리에 필요한 업무 로직을 수행한다. */
    private String categoryForResource(String resourceType) {
        return switch (valueOrBlank(resourceType)) {
            case "Pod", "Deployment", "ReplicaSet", "StatefulSet", "DaemonSet" -> "workload-health";
            case "PersistentVolumeClaim" -> "storage-config";
            case "Endpoint", "Service", "Ingress" -> "traffic-routing";
            case "HorizontalPodAutoscaler" -> "scaling";
            default -> "resource-health";
        };
    }

    /** AnalysisApplicationService의 addPlanCommand 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addPlanCommand(ArrayNode commands, String label, String command, String why, boolean destructive) {
        ObjectNode item = commands.addObject();
        item.put("label", valueOrBlank(label));
        item.put("command", valueOrBlank(command));
        item.put("why", valueOrBlank(why));
        item.put("destructive", destructive);
        item.put("commandType", destructive ? "destructive" : "verification");
    }

    /** AnalysisApplicationService의 highestSeverity 처리에 필요한 업무 로직을 수행한다. */
    private String highestSeverity(ArrayNode groups) {
        boolean hasCritical = false;
        boolean hasHigh = false;
        boolean hasMedium = false;
        for (JsonNode group : groups) {
            String severity = valueOrBlank(group.path("severity").asText()).toUpperCase();
            hasCritical = hasCritical || "CRITICAL".equals(severity);
            hasHigh = hasHigh || "HIGH".equals(severity);
            hasMedium = hasMedium || "MEDIUM".equals(severity);
        }
        if (hasCritical) {
            return "CRITICAL";
        }
        if (hasHigh) {
            return "HIGH";
        }
        if (hasMedium) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /** AnalysisApplicationService의 arraySize 처리에 필요한 업무 로직을 수행한다. */
    private int arraySize(JsonNode node) {
        return node != null && node.isArray() ? node.size() : 0;
    }

    /** AnalysisApplicationService의 confidenceValidation 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode confidenceValidation(KubernetesNamespaceDiagnostics diagnostics, ArrayNode problemCards, ArrayNode issueGroups) {
        long problemResourceCount = diagnostics.resources().stream().filter(this::isProblemResource).count();
        long warningEventCount = diagnostics.events().stream().filter(this::isWarningEvent).count();
        long highSignalLogCount = diagnostics.podLogs().stream().filter(this::isHighSignalLog).count();
        int confidenceScore = 35
                + (problemResourceCount > 0 ? 20 : 0)
                + (warningEventCount > 0 ? 20 : 0)
                + (highSignalLogCount > 0 ? 15 : 0)
                + (!issueGroups.isEmpty() ? 10 : 0);
        if (problemResourceCount == 0 && warningEventCount == 0 && highSignalLogCount == 0) {
            confidenceScore = 60;
        }
        confidenceScore = Math.min(95, confidenceScore);
        String level = confidenceScore >= 80 ? "HIGH" : confidenceScore >= 55 ? "MEDIUM" : "LOW";

        ObjectNode validation = objectMapper.createObjectNode();
        validation.put("score", confidenceScore);
        validation.put("level", level);
        validation.put("summary", "Kubernetes API fact, event, log signal, issue grouping 근거로 분석 신뢰도를 계산했습니다.");
        validation.put("beginnerSummary", "AI 의견만 본 것이 아니라 Pod 상태, 이벤트, 로그가 서로 같은 문제를 가리키는지 확인한 점수입니다.");
        validation.put("problemResourceCount", problemResourceCount);
        validation.put("warningEventCount", warningEventCount);
        validation.put("highSignalLogCount", highSignalLogCount);
        validation.put("issueGroupCount", issueGroups.size());
        validation.put("problemCardCount", problemCards.size());
        ArrayNode checks = validation.putArray("checks");
        addConfidenceCheck(checks, "리소스 상태", problemResourceCount > 0 ? "CONFIRMED" : "MISSING",
                problemResourceCount + "개 비정상 Kubernetes 리소스가 감지되었습니다.",
                problemResourceCount > 0
                        ? "Pod나 Service 같은 리소스 자체가 건강하지 않다는 직접 증거입니다."
                        : "리소스 상태만 보면 큰 이상은 보이지 않습니다.");
        addConfidenceCheck(checks, "Warning 이벤트", warningEventCount > 0 ? "CONFIRMED" : "MISSING",
                warningEventCount + "개 Warning event가 수집되었습니다.",
                warningEventCount > 0
                        ? "Kubernetes가 문제를 이벤트로 남겼기 때문에 원인 추적에 강한 근거가 됩니다."
                        : "Kubernetes 이벤트에서 반복 경고가 보이지 않습니다.");
        addConfidenceCheck(checks, "로그 위험 신호", highSignalLogCount > 0 ? "CONFIRMED" : "MISSING",
                highSignalLogCount + "개 high-signal log가 감지되었습니다.",
                highSignalLogCount > 0
                        ? "애플리케이션 로그에도 warn/error 패턴이 있어 리소스 상태와 같이 확인해야 합니다."
                        : "수집된 로그에서는 명확한 error/warn 패턴이 낮습니다.");
        addConfidenceCheck(checks, "원인 그룹화", issueGroups.isEmpty() ? "INFO" : "CONFIRMED",
                issueGroups.size() + "개 issue group으로 중복 신호를 압축했습니다.",
                issueGroups.isEmpty()
                        ? "반복되는 원인 후보가 낮아 기준선 점검 중심으로 보세요."
                        : "비슷한 이벤트와 리소스를 묶어 가장 먼저 볼 대상을 줄였습니다.");
        addConfidenceCheck(checks, "AI 사용 범위", "INFO",
                "성능/위험/타임라인/근거 섹션은 Kubernetes API 기반 deterministic 분석으로 보강됩니다.",
                "LLM이 timeout되어도 핵심 운영 근거는 API 데이터로 계속 표시됩니다.");
        return validation;
    }

    /** AnalysisApplicationService의 addConfidenceCheck 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addConfidenceCheck(ArrayNode checks, String name, String status, String explanation, String beginnerExplanation) {
        ObjectNode check = checks.addObject();
        check.put("name", name);
        check.put("status", status);
        check.put("explanation", explanation);
        check.put("beginnerExplanation", beginnerExplanation);
    }

    /** AnalysisApplicationService의 evidenceLedger 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode evidenceLedger(KubernetesNamespaceDiagnostics diagnostics, ArrayNode issueGroups, ArrayNode problemCards) {
        ObjectNode ledger = objectMapper.createObjectNode();
        ledger.put("summary", "분석 판단에 사용된 사실과 추론을 분리해 표시합니다.");
        ledger.put("beginnerSummary", "FACT는 실제 Kubernetes 상태이고, INFERENCE는 그 사실에서 추론한 후보입니다.");
        ledger.put("problemCardCount", problemCards.size());
        ArrayNode items = ledger.putArray("items");
        int[] index = {1};
        diagnostics.resources().stream()
                .filter(this::isProblemResource)
                .limit(8)
                .forEach(resource -> addEvidenceLedgerItem(items, index[0]++, "KUBERNETES_FACT", "HIGH",
                        resource.resourceType() + "/" + resource.resourceName(),
                        "status=" + valueOrBlank(resource.status()) + " " + truncate(resource.summaryJson(), 260),
                        "Kubernetes가 현재 이 리소스를 정상 상태로 보지 않는다는 직접 증거입니다.",
                        describeCommand(resource.resourceType(), resource.resourceName()).replace("${namespace}", valueOrBlank(resource.namespace()))));
        diagnostics.events().stream()
                .filter(this::isWarningEvent)
                .limit(8)
                .forEach(event -> addEvidenceLedgerItem(items, index[0]++, "EVENT_SIGNAL", event.count() != null && event.count() >= 3 ? "HIGH" : "MEDIUM",
                        "Event/" + valueOrBlank(event.reason()),
                        valueOrBlank(event.involvedKind()) + "/" + valueOrBlank(event.involvedName()) + " count="
                                + (event.count() == null ? 0 : event.count()) + " " + truncate(event.message(), 260),
                        "Kubernetes가 같은 문제를 이벤트로 기록했습니다. count가 높으면 반복 문제일 가능성이 큽니다.",
                        "kubectl get events -n " + valueOrBlank(event.namespace()) + " --field-selector involvedObject.name="
                                + valueOrBlank(event.involvedName()) + " --sort-by=.lastTimestamp"));
        logInsights(diagnostics).stream()
                .limit(5)
                .forEach(insight -> addEvidenceLedgerItem(items, index[0]++, "LOG_INTELLIGENCE_SIGNAL",
                        insight.severity(),
                        "Log/Pod/" + insight.podName(),
                        insight.category() + " · " + insight.signal(),
                        insight.beginnerExplanation(),
                        insight.verificationCommand()));
        portMismatchSignals(diagnostics).stream()
                .limit(6)
                .forEach(signal -> addEvidenceLedgerItem(items, index[0]++, "PORT_MAPPING_SIGNAL", signal.strongSignal() ? "HIGH" : "MEDIUM",
                        "Service/" + signal.serviceName(),
                        signal.reason() + " selector=" + signal.selector() + " declaredContainerPorts=" + signal.declaredContainerPorts(),
                        "Service가 Pod로 트래픽을 보낼 때 targetPort와 애플리케이션 listen 포트가 맞지 않으면 Pod가 떠도 통신이 실패하거나 시작 오류로 이어질 수 있습니다.",
                        "kubectl describe svc/" + signal.serviceName() + " -n " + signal.namespace()));
        for (JsonNode group : issueGroups) {
            if (index[0] > 28) {
                break;
            }
            addEvidenceLedgerItem(items, index[0]++, "AI_INFERENCE", confidenceForGroup(group),
                    "IssueGroup/" + group.path("issueGroupId").asText("-"),
                    group.path("title").asText("-") + " · " + group.path("rootCause").asText("-"),
                    "위 사실들을 같은 원인 후보로 묶은 운영 관점의 추론입니다. 변경 전에는 검증 명령으로 확인하세요.",
                    describeCommand(group.path("representativeResourceKind").asText(""), group.path("representativeResourceName").asText(""))
                            .replace("${namespace}", group.path("namespace").asText("${namespace}")));
        }
        return ledger;
    }

    /** AnalysisApplicationService의 addEvidenceLedgerItem 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addEvidenceLedgerItem(ArrayNode items, int index, String type, String confidence, String source,
                                       String message, String beginnerExplanation, String verificationCommand) {
        ObjectNode item = items.addObject();
        item.put("evidenceId", "E-" + index);
        item.put("evidenceType", type);
        item.put("confidence", confidence);
        item.put("source", valueOrBlank(source));
        item.put("message", valueOrBlank(message));
        item.put("beginnerExplanation", valueOrBlank(beginnerExplanation));
        item.put("verificationCommand", valueOrBlank(verificationCommand));
    }

    /** AnalysisApplicationService의 confidenceForGroup 처리에 필요한 업무 로직을 수행한다. */
    private String confidenceForGroup(JsonNode group) {
        int score = group.path("score").asInt(0);
        int events = group.path("eventOccurrenceCount").asInt(0);
        int resources = group.path("affectedResourceCount").asInt(0);
        if (score >= 280 || events >= 5 || resources >= 2) {
            return "HIGH";
        }
        if (score >= 150 || events > 0 || resources > 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /** AnalysisApplicationService의 eventNoiseReduction 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode eventNoiseReduction(KubernetesNamespaceDiagnostics diagnostics) {
        ObjectNode result = objectMapper.createObjectNode();
        List<KubernetesNamespaceDiagnostics.DiagnosticEvent> warnings = diagnostics.events().stream()
                .filter(this::isWarningEvent)
                .toList();
        Map<String, EventNoiseAccumulator> groups = new LinkedHashMap<>();
        warnings.forEach(event -> {
            String key = valueOrBlank(event.reason()) + "|" + valueOrBlank(event.involvedKind()) + "|" + valueOrBlank(event.involvedName());
            groups.computeIfAbsent(key, ignored -> new EventNoiseAccumulator(event)).add(event);
        });
        int totalOccurrences = groups.values().stream().mapToInt(EventNoiseAccumulator::occurrenceCount).sum();
        String noiseLevel = totalOccurrences >= 30 || groups.size() >= 8 ? "HIGH" : totalOccurrences >= 5 ? "MEDIUM" : "LOW";
        result.put("summary", groups.isEmpty()
                ? "Warning 이벤트 노이즈가 낮습니다."
                : "Warning 이벤트를 " + groups.size() + "개 반복 패턴으로 압축했습니다.");
        result.put("beginnerSummary", "이벤트가 많이 쌓이면 같은 문제가 여러 줄로 보입니다. 같은 이유와 같은 리소스를 한 묶음으로 줄여 가장 중요한 것부터 보여줍니다.");
        result.put("totalEvents", diagnostics.events().size());
        result.put("warningEvents", warnings.size());
        result.put("compressedWarningGroups", groups.size());
        result.put("warningOccurrences", totalOccurrences);
        result.put("noiseLevel", noiseLevel);
        ArrayNode nodes = result.putArray("groups");
        groups.values().stream()
                .sorted(Comparator.comparing(EventNoiseAccumulator::priorityWeight).reversed())
                .limit(10)
                .forEach(group -> {
                    ObjectNode node = nodes.addObject();
                    node.put("reason", group.reason);
                    node.put("targetKind", group.targetKind);
                    node.put("targetName", group.targetName);
                    node.put("occurrenceCount", group.occurrenceCount());
                    node.put("priority", group.priority());
                    node.put("message", truncate(group.message, 260));
                    node.put("operatorMeaning", eventMeaning(group.reason, group.message));
                    node.put("beginnerExplanation", "같은 리소스에서 " + group.reason + " 이벤트가 반복됩니다. 먼저 이 리소스의 describe와 이벤트 타임라인을 확인하세요.");
                });
        return result;
    }

    /** AnalysisApplicationService의 eventMeaning 처리에 필요한 업무 로직을 수행한다. */
    private String eventMeaning(String reason, String message) {
        String value = (valueOrBlank(reason) + " " + valueOrBlank(message)).toLowerCase();
        if (value.contains("failedmount") || value.contains("configmap") || value.contains("secret") || value.contains("volume")) {
            return "Pod가 필요한 설정/볼륨을 붙이지 못해 시작이 지연될 수 있습니다.";
        }
        if (value.contains("failedscheduling")) {
            return "Pod를 배치할 Node 조건이나 용량이 맞지 않을 수 있습니다.";
        }
        if (value.contains("backoff") || value.contains("pull")) {
            return "컨테이너 이미지 또는 실행 시작 과정에서 반복 실패가 발생할 수 있습니다.";
        }
        if (value.contains("unhealthy")) {
            return "readiness/liveness probe 또는 애플리케이션 응답 상태를 확인해야 합니다.";
        }
        return "반복 이벤트입니다. 같은 시간대 리소스 상태와 로그를 함께 확인하세요.";
    }

    /** AnalysisApplicationService의 correlationMap 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode correlationMap(KubernetesNamespaceDiagnostics diagnostics, ArrayNode issueGroups) {
        ObjectNode map = objectMapper.createObjectNode();
        map.put("summary", "문제 리소스, 이벤트, 로그, 참조 리소스의 연결 관계를 구성했습니다.");
        map.put("beginnerSummary", "Pod 하나가 실패해도 원인은 ConfigMap, Secret, PVC, Node, 이미지, Probe일 수 있습니다. 이 맵은 서로 연결된 대상을 한눈에 보여줍니다.");
        ArrayNode nodes = map.putArray("nodes");
        ArrayNode edges = map.putArray("edges");
        ArrayNode rootHints = map.putArray("rootHints");
        Map<String, Boolean> seenNodes = new LinkedHashMap<>();
        Map<String, Boolean> seenEdges = new LinkedHashMap<>();

        addCorrelationNode(nodes, seenNodes, "namespace", "Namespace", namespaceFromDiagnostics(diagnostics), "분석 범위", "INFO");
        diagnostics.resources().stream()
                .filter(this::isProblemResource)
                .limit(12)
                .forEach(resource -> addCorrelationNode(nodes, seenNodes,
                        nodeId(resource.resourceType(), resource.resourceName()), resource.resourceType(), resource.resourceName(),
                        "status=" + valueOrBlank(resource.status()), problemSeverity(resource, List.of())));
        diagnostics.events().stream()
                .filter(this::isWarningEvent)
                .limit(16)
                .forEach(event -> {
                    String eventId = "event:" + valueOrBlank(event.reason()) + ":" + valueOrBlank(event.involvedKind()) + ":" + valueOrBlank(event.involvedName());
                    addCorrelationNode(nodes, seenNodes, eventId, "Event", valueOrBlank(event.reason()),
                            "count=" + (event.count() == null ? 0 : event.count()), event.count() != null && event.count() >= 5 ? "HIGH" : "MEDIUM");
                    String targetId = nodeId(event.involvedKind(), event.involvedName());
                    addCorrelationEdge(edges, seenEdges, targetId, eventId, "HAS_EVENT", truncate(event.message(), 180));
                    addReferencesToCorrelation(nodes, edges, seenNodes, seenEdges, targetId, event);
                });
        diagnostics.podLogs().stream()
                .filter(this::isHighSignalLog)
                .limit(10)
                .forEach(log -> {
                    String logId = "log:" + log.podName() + ":" + log.containerName();
                    addCorrelationNode(nodes, seenNodes, logId, "Log", log.podName() + "/" + log.containerName(),
                            firstLogSignal(log.log()), "MEDIUM");
                    addCorrelationEdge(edges, seenEdges, nodeId("Pod", log.podName()), logId, "HAS_LOG", firstLogSignal(log.log()));
                });
        for (JsonNode group : issueGroups) {
            ObjectNode hint = rootHints.addObject();
            hint.put("title", group.path("title").asText("-"));
            hint.put("target", group.path("representativeResourceKind").asText("-") + "/" + group.path("representativeResourceName").asText("-"));
            hint.put("reason", group.path("rootCause").asText("-"));
            hint.put("confidence", confidenceForGroup(group));
            hint.put("nextAction", group.path("recommendedNextAction").asText("-"));
        }
        return map;
    }

    /** AnalysisApplicationService의 namespaceFromDiagnostics 처리에 필요한 업무 로직을 수행한다. */
    private String namespaceFromDiagnostics(KubernetesNamespaceDiagnostics diagnostics) {
        return diagnostics.resources().stream()
                .map(KubernetesNamespaceDiagnostics.DiagnosticResource::namespace)
                .filter(value -> !valueOrBlank(value).isBlank())
                .findFirst()
                .orElseGet(() -> diagnostics.events().stream()
                        .map(KubernetesNamespaceDiagnostics.DiagnosticEvent::namespace)
                        .filter(value -> !valueOrBlank(value).isBlank())
                        .findFirst()
                        .orElse("${namespace}"));
    }

    /** AnalysisApplicationService의 addReferencesToCorrelation 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addReferencesToCorrelation(ArrayNode nodes, ArrayNode edges, Map<String, Boolean> seenNodes,
                                            Map<String, Boolean> seenEdges, String targetId,
                                            KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String message = valueOrBlank(event.message());
        addReferenceToCorrelation(nodes, edges, seenNodes, seenEdges, targetId, "ConfigMap",
                extractReferenceName(message, "configmap \"?([A-Za-z0-9_.-]+)\"?"), event.reason());
        addReferenceToCorrelation(nodes, edges, seenNodes, seenEdges, targetId, "Secret",
                extractReferenceName(message, "secret \"?([A-Za-z0-9_.-]+)\"?"), event.reason());
        addReferenceToCorrelation(nodes, edges, seenNodes, seenEdges, targetId, "PersistentVolumeClaim",
                extractReferenceName(message, "(?:persistentvolumeclaim|pvc) \"?([A-Za-z0-9_.-]+)\"?"), event.reason());
    }

    /** AnalysisApplicationService의 addReferenceToCorrelation 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addReferenceToCorrelation(ArrayNode nodes, ArrayNode edges, Map<String, Boolean> seenNodes,
                                           Map<String, Boolean> seenEdges, String fromId,
                                           String kind, String name, String reason) {
        if (valueOrBlank(name).isBlank()) {
            return;
        }
        String id = nodeId(kind, name);
        addCorrelationNode(nodes, seenNodes, id, kind, name, "참조 리소스", "MEDIUM");
        addCorrelationEdge(edges, seenEdges, fromId, id, "REFERENCES", valueOrBlank(reason));
    }

    /** AnalysisApplicationService의 addCorrelationNode 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addCorrelationNode(ArrayNode nodes, Map<String, Boolean> seen, String id, String type, String label,
                                    String detail, String severity) {
        String normalizedId = valueOrBlank(id);
        if (normalizedId.isBlank() || seen.containsKey(normalizedId)) {
            return;
        }
        seen.put(normalizedId, true);
        ObjectNode node = nodes.addObject();
        node.put("id", normalizedId);
        node.put("type", valueOrBlank(type));
        node.put("label", valueOrBlank(label));
        node.put("detail", valueOrBlank(detail));
        node.put("severity", valueOrBlank(severity));
    }

    /** AnalysisApplicationService의 addCorrelationEdge 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addCorrelationEdge(ArrayNode edges, Map<String, Boolean> seen, String from, String to, String relation, String evidence) {
        String normalizedFrom = valueOrBlank(from);
        String normalizedTo = valueOrBlank(to);
        String key = normalizedFrom + "->" + normalizedTo + ":" + valueOrBlank(relation);
        if (normalizedFrom.isBlank() || normalizedTo.isBlank() || seen.containsKey(key)) {
            return;
        }
        seen.put(key, true);
        ObjectNode edge = edges.addObject();
        edge.put("from", normalizedFrom);
        edge.put("to", normalizedTo);
        edge.put("relation", valueOrBlank(relation));
        edge.put("evidence", truncate(evidence, 180));
    }

    /** AnalysisApplicationService의 nodeId 처리에 필요한 업무 로직을 수행한다. */
    private String nodeId(String kind, String name) {
        return valueOrBlank(kind) + ":" + valueOrBlank(name);
    }

    /** AnalysisApplicationService의 commandSafety 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode commandSafety(JsonNode root) {
        ObjectNode safety = objectMapper.createObjectNode();
        ArrayNode commands = safety.putArray("commands");
        collectSafetyCommands(commands, root.path("remediationPlan").path("stages"), "remediationPlan");
        collectSafetyCommands(commands, root.path("runbookActions"), "runbook");
        collectSafetyCommands(commands, root.path("verificationCommands"), "verification");
        int readOnly = 0;
        int change = 0;
        int destructive = 0;
        for (JsonNode command : commands) {
            String level = command.path("safetyLevel").asText();
            if ("READ_ONLY".equals(level)) {
                readOnly++;
            } else if ("DESTRUCTIVE".equals(level)) {
                destructive++;
            } else {
                change++;
            }
        }
        safety.put("summary", "분석 결과에 포함된 명령을 읽기 전용/변경/위험 조치로 분류했습니다.");
        safety.put("beginnerSummary", "처음에는 읽기 전용 명령만 실행하세요. apply, patch, delete 같은 명령은 클러스터 상태를 바꿀 수 있어 승인 후 실행해야 합니다.");
        safety.put("readOnlyCount", readOnly);
        safety.put("changeCount", change);
        safety.put("destructiveCount", destructive);
        return safety;
    }

    /** AnalysisApplicationService의 collectSafetyCommands 처리의 핵심 작업 흐름을 실행한다. */
    private void collectSafetyCommands(ArrayNode target, JsonNode source, String sourceName) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return;
        }
        if (source.isArray()) {
            for (JsonNode item : source) {
                if (item.has("commands")) {
                    collectSafetyCommands(target, item.path("commands"), sourceName + ":" + item.path("title").asText("stage"));
                } else {
                    addSafetyCommand(target, sourceName, item);
                }
            }
        } else {
            addSafetyCommand(target, sourceName, source);
        }
    }

    /** AnalysisApplicationService의 addSafetyCommand 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addSafetyCommand(ArrayNode commands, String sourceName, JsonNode item) {
        String command = item.isTextual() ? item.asText() : item.path("command").asText("");
        if (valueOrBlank(command).isBlank()) {
            return;
        }
        String level = COMMAND_POLICY.safetyLevel(command);
        ObjectNode node = commands.addObject();
        node.put("source", valueOrBlank(sourceName));
        node.put("label", item.isTextual() ? "검증 명령" : item.path("label").asText(item.path("title").asText("명령")));
        node.put("command", command);
        node.put("safetyLevel", level);
        node.put("requiresApproval", "RISKY_CHANGE".equals(level) || "DESTRUCTIVE".equals(level));
        node.put("why", item.isTextual() ? "분석 결과를 검증하기 위한 명령입니다." : item.path("why").asText(item.path("reason").asText("분석 결과 확인 명령입니다.")));
        node.put("beginnerExplanation", COMMAND_POLICY.beginnerExplanation(level));
    }

    /** AnalysisApplicationService의 issueGroupDeepDives 처리 조건의 충족 여부를 판단한다. */
    private ArrayNode issueGroupDeepDives(KubernetesNamespaceDiagnostics diagnostics, ArrayNode issueGroups) {
        ArrayNode deepDives = objectMapper.createArrayNode();
        for (JsonNode group : issueGroups) {
            ObjectNode detail = deepDives.addObject();
            String issueGroupId = group.path("issueGroupId").asText("-");
            String category = group.path("category").asText("");
            String resourceKind = group.path("representativeResourceKind").asText("");
            String resourceName = group.path("representativeResourceName").asText("");
            String namespace = group.path("namespace").asText(namespaceFromDiagnostics(diagnostics));
            String rootCause = group.path("rootCause").asText("");
            detail.put("issueGroupId", issueGroupId);
            detail.put("title", group.path("title").asText("Issue group detail"));
            detail.put("category", category);
            detail.put("resourceKind", resourceKind);
            detail.put("resourceName", resourceName);
            detail.put("namespace", namespace);
            detail.put("summary", deepDiveSummary(category, rootCause));
            detail.put("beginnerSummary", beginnerDeepDiveSummary(category));
            ArrayNode checks = detail.putArray("checks");
            addBaseDeepDiveChecks(checks, namespace, resourceKind, resourceName);
            addCategoryDeepDiveChecks(checks, namespace, category, rootCause, resourceKind, resourceName);
            ObjectNode logOptions = detail.putObject("logOptions");
            logOptions.put("enabled", "Pod".equals(resourceKind));
            logOptions.put("recommendedTailLines", "log-pattern".equals(category) ? 1000 : 300);
            logOptions.put("containerSelection", "Pod".equals(resourceKind) ? "사용자가 컨테이너를 선택하거나 전체 컨테이너를 조회합니다." : "직접 로그가 없는 리소스는 관련 Pod를 먼저 찾아야 합니다.");
            ObjectNode reanalysis = detail.putObject("reanalysis");
            reanalysis.put("scope", "ISSUE_GROUP_GUIDED");
            reanalysis.put("recommendedTrigger", "검증 명령을 실행하거나 조치 후 같은 namespace를 재분석하세요.");
            reanalysis.put("contextHint", group.path("title").asText("") + " / " + truncate(rootCause, 220));
        }
        return deepDives;
    }

    /** AnalysisApplicationService의 deepDiveSummary 처리에 필요한 업무 로직을 수행한다. */
    private String deepDiveSummary(String category, String rootCause) {
        String normalized = valueOrBlank(category);
        if ("storage-config".equals(normalized)) {
            return "Mount/volume/config 참조를 우선 확인해야 하는 문제입니다: " + truncate(rootCause, 220);
        }
        if ("scheduling-capacity".equals(normalized)) {
            return "Pod 배치 조건, Node 용량, taint/toleration, PVC binding을 함께 확인해야 합니다.";
        }
        if ("runtime-startup".equals(normalized)) {
            return "컨테이너 이미지, 시작 실패, CrashLoop/BackOff, 이전 로그를 확인해야 합니다.";
        }
        if ("port-startup".equals(normalized)) {
            return "컨테이너가 지정 포트에 바인딩하지 못해 시작에 실패한 문제입니다. previous 로그와 보안 컨텍스트를 함께 확인해야 합니다.";
        }
        if ("probe-health".equals(normalized)) {
            return "Readiness/Liveness probe와 애플리케이션 응답 상태를 비교해야 합니다.";
        }
        if ("log-pattern".equals(normalized)) {
            return "애플리케이션 로그 패턴이 원인 후보입니다. 로그 범위와 반복 여부를 확인해야 합니다.";
        }
        return truncate(rootCause, 260);
    }

    /** AnalysisApplicationService의 beginnerDeepDiveSummary 처리에 필요한 업무 로직을 수행한다. */
    private String beginnerDeepDiveSummary(String category) {
        return switch (valueOrBlank(category)) {
            case "storage-config" -> "Pod가 필요한 설정 파일이나 볼륨을 찾지 못하면 시작하지 못합니다. 이름과 namespace가 맞는지 먼저 확인하세요.";
            case "scheduling-capacity" -> "Pod는 아무 Node에나 올라가지 않습니다. 용량, 조건, 정책이 맞아야 배치됩니다.";
            case "runtime-startup" -> "이미지를 가져오지 못하거나 컨테이너가 바로 종료되면 Pod가 반복 실패 상태가 됩니다.";
            case "port-startup" -> "애플리케이션이 쓰려는 포트가 권한/충돌/설정 문제로 열리지 않으면 컨테이너가 바로 종료될 수 있습니다.";
            case "probe-health" -> "Kubernetes가 애플리케이션이 준비됐는지 검사하는 probe가 실패할 수 있습니다.";
            case "log-pattern" -> "로그는 애플리케이션 내부 사정을 보여줍니다. Kubernetes 이벤트와 같은 시간대인지 확인하세요.";
            default -> "상태, 이벤트, 로그를 같은 리소스 기준으로 차례대로 확인하세요.";
        };
    }

    /** AnalysisApplicationService의 addBaseDeepDiveChecks 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addBaseDeepDiveChecks(ArrayNode checks, String namespace, String resourceKind, String resourceName) {
        addDeepDiveCheck(checks, "상태 상세 확인", "VERIFY", "현재 condition, event, spec 참조를 확인합니다.",
                describeCommand(resourceKind, resourceName).replace("${namespace}", namespace),
                "resource 상태와 event가 같은 원인을 가리키는지 확인합니다.");
        if (!valueOrBlank(resourceName).isBlank() && !"-".equals(resourceName)) {
            addDeepDiveCheck(checks, "이벤트 반복 확인", "VERIFY", "같은 이벤트 count가 계속 증가하는지 확인합니다.",
                    "kubectl get events -n " + namespace + " --field-selector involvedObject.name=" + resourceName + " --sort-by=.lastTimestamp",
                    "반복 이벤트면 일시적 현상이 아니라 지속 문제일 가능성이 큽니다.");
        }
    }

    /** AnalysisApplicationService의 addCategoryDeepDiveChecks 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addCategoryDeepDiveChecks(ArrayNode checks, String namespace, String category, String rootCause,
                                           String resourceKind, String resourceName) {
        String lowerCause = valueOrBlank(rootCause).toLowerCase();
        if ("storage-config".equals(category) || lowerCause.contains("failedmount")) {
            addDeepDiveCheck(checks, "ConfigMap/Secret/PVC 존재 확인", "VERIFY", "FailedMount 참조 대상이 namespace에 실제 존재하는지 확인합니다.",
                    "kubectl get configmap,secret,pvc -n " + namespace,
                    "없으면 Pod가 volume을 붙이지 못합니다. 있으면 이름/namespace/volumeMount를 다시 봅니다.");
            addDeepDiveCheck(checks, "Pod volume 설정 확인", "DIAGNOSE", "Pod가 어떤 volume과 volumeMount를 참조하는지 확인합니다.",
                    "kubectl describe pod/" + resourceName + " -n " + namespace,
                    "volume 이름, mountPath, 참조 리소스 이름이 이벤트 메시지와 일치해야 합니다.");
        } else if ("scheduling-capacity".equals(category) || lowerCause.contains("failedscheduling")) {
            addDeepDiveCheck(checks, "Node 용량 확인", "VERIFY", "Pod를 올릴 수 있는 Node가 있는지 확인합니다.",
                    "kubectl describe nodes",
                    "CPU/Memory, taint, disk pressure 같은 조건 때문에 스케줄링이 막힐 수 있습니다.");
            addDeepDiveCheck(checks, "PVC binding 확인", "VERIFY", "PVC가 Bound인지 확인합니다.",
                    "kubectl get pvc -n " + namespace,
                    "PVC가 Pending이면 Pod도 함께 Pending에 머물 수 있습니다.");
        } else if ("port-startup".equals(category)) {
            addDeepDiveCheck(checks, "이전 컨테이너 로그 확인", "DIAGNOSE", "재시작 직전 포트 바인딩 오류를 확인합니다.",
                    "kubectl logs pod/" + resourceName + " -n " + namespace + " --previous --tail=300",
                    "포트 바인딩 실패는 컨테이너가 종료된 직전 로그에 남는 경우가 많습니다.");
            addDeepDiveCheck(checks, "보안 컨텍스트/포트 권한 확인", "VERIFY", "non-root 컨테이너가 privileged port를 열려고 하는지 확인합니다.",
                    "kubectl describe pod/" + resourceName + " -n " + namespace,
                    "Linux에서 non-root 프로세스가 1024 미만 포트를 열지 못하면 Permission denied가 발생할 수 있습니다.");
            addDeepDiveCheck(checks, "Service와 컨테이너 포트 대조", "VERIFY", "Service targetPort와 containerPort/listen 포트가 일치하는지 확인합니다.",
                    "kubectl get service -n " + namespace + " -o wide",
                    "Service 매핑 문제와 컨테이너 내부 listen 실패는 서로 다른 원인이므로 분리해서 봅니다.");
        } else if ("runtime-startup".equals(category) || lowerCause.contains("backoff") || lowerCause.contains("pull")) {
            addDeepDiveCheck(checks, "이전 컨테이너 로그 확인", "DIAGNOSE", "재시작 직전 로그를 확인합니다.",
                    "kubectl logs pod/" + resourceName + " -n " + namespace + " --previous --tail=300",
                    "CrashLoopBackOff 원인은 현재 로그보다 previous 로그에 더 잘 남는 경우가 많습니다.");
            addDeepDiveCheck(checks, "이미지/환경 설정 확인", "VERIFY", "image, env, secret/configmap 참조를 확인합니다.",
                    "kubectl describe pod/" + resourceName + " -n " + namespace,
                    "이미지 pull, 환경변수, secret 참조 오류가 시작 실패로 이어질 수 있습니다.");
        } else if ("probe-health".equals(category) || lowerCause.contains("unhealthy")) {
            addDeepDiveCheck(checks, "Probe 설정 확인", "VERIFY", "readiness/liveness probe 경로와 지연 시간을 확인합니다.",
                    "kubectl describe pod/" + resourceName + " -n " + namespace,
                    "애플리케이션 시작 시간이 probe보다 길면 정상 앱도 실패로 보일 수 있습니다.");
        } else if ("log-pattern".equals(category)) {
            addDeepDiveCheck(checks, "로그 범위 확대", "DIAGNOSE", "최근 로그만으로 부족하면 row 수를 늘려 반복 패턴을 확인합니다.",
                    "kubectl logs pod/" + resourceName + " -n " + namespace + " --tail=1000",
                    "한 번의 warning인지 반복 warning인지에 따라 운영 판단이 달라집니다.");
        }
    }

    /** AnalysisApplicationService의 addDeepDiveCheck 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addDeepDiveCheck(ArrayNode checks, String title, String checkType, String objective, String command, String successCriteria) {
        ObjectNode check = checks.addObject();
        check.put("title", valueOrBlank(title));
        check.put("checkType", valueOrBlank(checkType));
        check.put("objective", valueOrBlank(objective));
        check.put("command", valueOrBlank(command));
        check.put("successCriteria", valueOrBlank(successCriteria));
        check.put("safetyLevel", COMMAND_POLICY.safetyLevel(command));
    }

    /** AnalysisApplicationService의 actionWorkflow 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode actionWorkflow(ArrayNode issueGroups) {
        ObjectNode workflow = objectMapper.createObjectNode();
        workflow.put("summary", issueGroups.isEmpty()
                ? "추적할 issue group이 없습니다."
                : "Issue group별 운영 상태를 추적하고, 조치 후 재분석으로 닫는 흐름입니다.");
        workflow.put("beginnerSummary", "문제를 찾는 것에서 끝내지 않고 확인, 조치, 재분석까지 표시해 운영자가 무엇을 했는지 남길 수 있습니다.");
        ArrayNode statuses = workflow.putArray("statuses");
        addWorkflowStatus(statuses, "OPEN", "확인 필요", "아직 검증하지 않은 문제입니다.");
        addWorkflowStatus(statuses, "INVESTIGATING", "확인 중", "명령과 로그로 원인을 좁히는 중입니다.");
        addWorkflowStatus(statuses, "ACTION_PENDING", "조치 대기", "원인은 확인했지만 변경 승인이 필요합니다.");
        addWorkflowStatus(statuses, "FIXED", "조치 완료", "조치했으며 재분석으로 확인해야 합니다.");
        addWorkflowStatus(statuses, "ACCEPTED", "관찰/보류", "즉시 조치하지 않고 관찰하기로 한 상태입니다.");
        ArrayNode items = workflow.putArray("items");
        for (JsonNode group : issueGroups) {
            ObjectNode item = items.addObject();
            item.put("issueGroupId", group.path("issueGroupId").asText("-"));
            item.put("title", group.path("title").asText("Issue group"));
            item.put("defaultStatus", "OPEN");
            item.put("recommendedNextStatus", "READY_TO_FIX".equals(group.path("fixReadiness").asText(""))
                    ? "ACTION_PENDING" : "INVESTIGATING");
            item.put("nextAction", group.path("recommendedNextAction").asText("-"));
        }
        return workflow;
    }

    /** AnalysisApplicationService의 addWorkflowStatus 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addWorkflowStatus(ArrayNode statuses, String value, String label, String description) {
        ObjectNode status = statuses.addObject();
        status.put("value", value);
        status.put("label", label);
        status.put("description", description);
    }

    /** AnalysisApplicationService의 conclusionValidation 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode conclusionValidation(ObjectNode root, ArrayNode issueGroups) {
        ObjectNode validation = objectMapper.createObjectNode();
        validation.put("summary", "Root Cause와 Issue Group이 실제 evidence로 검증 가능한지 분류했습니다.");
        validation.put("beginnerSummary", "검증됨은 Kubernetes 상태/이벤트/로그 근거가 충분하다는 뜻이고, 추정은 변경 전 추가 확인이 필요하다는 뜻입니다.");
        ArrayNode conclusions = validation.putArray("conclusions");
        int validatedCount = 0;
        int needsEvidenceCount = 0;
        for (JsonNode group : issueGroups) {
            ObjectNode item = conclusions.addObject();
            String confidence = confidenceForGroup(group);
            String validationStatus = "HIGH".equals(confidence) ? "VERIFIED" : "MEDIUM".equals(confidence) ? "INFERRED" : "NEEDS_EVIDENCE";
            if ("VERIFIED".equals(validationStatus)) {
                validatedCount++;
            } else {
                needsEvidenceCount++;
            }
            item.put("targetType", "IssueGroup");
            item.put("targetId", group.path("issueGroupId").asText("-"));
            item.put("issueGroupId", group.path("issueGroupId").asText("-"));
            item.put("title", group.path("title").asText("-"));
            item.put("validationStatus", validationStatus);
            item.put("status", validationStatus);
            item.put("confidence", confidence);
            item.put("confidenceScore", "HIGH".equals(confidence) ? 85 : "MEDIUM".equals(confidence) ? 62 : 38);
            item.put("evidenceCount", arraySize(group.path("evidenceSummary")));
            ArrayNode missingEvidence = item.putArray("missingEvidence");
            if (!"HIGH".equals(confidence)) {
                missingEvidence.add("관련 리소스 describe 결과");
                missingEvidence.add("최근 이벤트 반복 여부");
            }
            item.put("operatorMeaning", "HIGH".equals(confidence)
                    ? "근거가 충분하므로 조치 전 영향 범위를 확인하세요."
                    : "근거를 더 확인한 뒤 조치하세요.");
            item.put("beginnerExplanation", "VERIFIED".equals(validationStatus)
                    ? "Kubernetes 이벤트와 리소스 상태가 같은 원인을 가리키고 있습니다."
                    : "AI가 가능성 높은 원인을 제시했지만, 변경 전에 조회 명령으로 한 번 더 확인해야 합니다.");
        }
        validation.put("validatedCount", validatedCount);
        validation.put("needsEvidenceCount", needsEvidenceCount);
        ArrayNode rootCauses = validation.putArray("rootCauseChecks");
        for (JsonNode rootCause : root.path("rootCauses")) {
            ObjectNode item = rootCauses.addObject();
            item.put("title", rootCause.path("cause").asText(rootCause.path("title").asText("-")));
            item.put("validationStatus", rootCause.path("confidenceLevel").asText("LOW").equals("HIGH") ? "VERIFIED" : "INFERRED");
            item.put("resourceKind", rootCause.path("resourceKind").asText("-"));
            item.put("resourceName", rootCause.path("resourceName").asText("-"));
        }
        return validation;
    }

    /** AnalysisApplicationService의 reanalysisPlan 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode reanalysisPlan(KubernetesNamespaceDiagnostics diagnostics, ArrayNode issueGroups) {
        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("summary", "조치 전후로 같은 namespace를 재분석해 해결/지속/악화 여부를 비교합니다.");
        plan.put("beginnerSummary", "명령을 실행하거나 설정을 바꾼 뒤에는 같은 범위를 다시 분석해야 문제가 정말 사라졌는지 확인할 수 있습니다.");
        plan.put("defaultScope", "NAMESPACE");
        plan.put("recommendedLogTailLines", diagnostics.podLogs().stream().anyMatch(this::isHighSignalLog) ? 1000 : 300);
        ArrayNode options = plan.putArray("options");
        addReanalysisOption(options, "FULL_NAMESPACE", "전체 namespace 재분석", "조치 후 전체 위험도와 issue group 변화를 비교합니다.");
        if (!issueGroups.isEmpty()) {
            addReanalysisOption(options, "ISSUE_GROUP_FOCUS", "선택 issue 중심 확인", "Issue Group 상세의 검증 명령을 실행한 뒤 같은 namespace를 재분석합니다.");
        }
        if (diagnostics.podLogs().stream().anyMatch(this::isHighSignalLog)) {
            addReanalysisOption(options, "LOG_EXPANDED", "로그 범위 확장 후 재분석", "컨테이너와 row 수를 늘려 반복 로그인지 확인한 뒤 재분석합니다.");
        }
        return plan;
    }

    /** AnalysisApplicationService의 addReanalysisOption 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addReanalysisOption(ArrayNode options, String value, String label, String description) {
        ObjectNode option = options.addObject();
        option.put("value", value);
        option.put("label", label);
        option.put("description", description);
    }

    /** AnalysisApplicationService의 deterministicPerformanceScalingSection 처리에 필요한 업무 로직을 수행한다. */
    private AnalysisSectionExecutor.Result deterministicPerformanceScalingSection(KubernetesNamespaceDiagnostics diagnostics) {
        long startedNanos = System.nanoTime();
        DeterministicPerformanceScalingSectionBuilder.Input input = kubernetesPerformanceSignalPolicy.select(diagnostics);
        ObjectNode result = deterministicPerformanceScalingSectionBuilder.build(input);

        return new AnalysisSectionExecutor.Result("performance-scaling", result, 0, elapsedMillis(startedNanos), null);
    }

    /** AnalysisApplicationService의 deterministicRiskTimelineSection 처리에 필요한 업무 로직을 수행한다. */
    private AnalysisSectionExecutor.Result deterministicRiskTimelineSection(KubernetesNamespaceDiagnostics diagnostics) {
        long startedNanos = System.nanoTime();
        NamespaceDiagnosticsResult.RiskForecast forecast = riskForecast(diagnostics);
        ObjectNode result = deterministicRiskTimelineSectionBuilder.build(forecast, changeTimeline(diagnostics));

        return new AnalysisSectionExecutor.Result("risk-timeline", result, 0, elapsedMillis(startedNanos), null);
    }

    /** AnalysisApplicationService의 parseObject 처리 데이터를 필요한 표현으로 변환한다. */
    private ObjectNode parseObject(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode objectNode) {
                return objectNode;
            }
            throw new IllegalArgumentException("JSON object expected");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse section analysis JSON", exception);
        }
    }

    /** AnalysisApplicationService의 putSingleCallAnalysisDiagnostics 처리에 필요한 업무 로직을 수행한다. */
    private void putSingleCallAnalysisDiagnostics(ObjectNode root, String mode, String scope, long startedNanos,
                                                  int contextChars, String failureReason) {
        analysisResultAssembler.writeSingleCallTelemetry(root, mode, scope, elapsedMillis(startedNanos),
                new AnalysisSectionExecutor.Result("full-context", objectMapper.createObjectNode(), contextChars,
                        elapsedMillis(startedNanos), failureReason),
                contextChars,
                failureReason);
    }

    /** AnalysisApplicationService의 elapsedMillis 처리에 필요한 업무 로직을 수행한다. */
    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / NANOS_PER_MILLI);
    }

    /** AnalysisApplicationService의 namespaceSectionContext 처리에 필요한 업무 로직을 수행한다. */
    private String namespaceSectionContext(UUID clusterId, String namespace, String applicationName,
                                           KubernetesNamespaceDiagnostics diagnostics, String sectionName) {
        int contextLimit = Math.min(maxAnalysisContextChars, 14_000);
        return namespaceAnalysisContextBuilder.build(
                clusterId,
                namespace,
                applicationName,
                diagnostics,
                sectionName,
                contextLimit,
                context -> {
                    switch (sectionName) {
                        case "root-cause" -> appendRcaSignals(context, diagnostics, applicationName);
                        case "log-analysis" -> appendLogSignals(context, diagnostics, applicationName);
                        case "performance-scaling" -> appendPerformanceScalingSignals(context, diagnostics, applicationName);
                        case "risk-timeline" -> appendRiskTimelineSignals(context, diagnostics);
                        case "runbook-operations" -> appendRunbookSignals(context, diagnostics);
                        default -> appendRcaSignals(context, diagnostics, applicationName);
                    }
                });
    }

    /** AnalysisApplicationService의 appendRcaSignals 처리에 필요한 업무 로직을 수행한다. */
    private void appendRcaSignals(StringBuilder context, KubernetesNamespaceDiagnostics diagnostics, String applicationName) {
        context.append("problemResourceSignals:\n");
        diagnostics.resources().stream()
                .filter(this::isProblemResource)
                .filter(resource -> applicationName == null || resource.resourceName().contains(applicationName))
                .limit(12)
                .forEach(resource -> appendResource(context, resource));
        context.append("warningEventSignals:\n");
        diagnostics.events().stream()
                .filter(this::isWarningEvent)
                .limit(12)
                .forEach(event -> appendEvent(context, event, 500));
        context.append("highSignalPodLogs:\n");
        diagnostics.podLogs().stream()
                .filter(this::isHighSignalLog)
                .filter(log -> applicationName == null || log.podName().contains(applicationName))
                .limit(5)
                .forEach(log -> appendPodLog(context, log, 700));
    }

    /** AnalysisApplicationService의 appendLogSignals 처리에 필요한 업무 로직을 수행한다. */
    private void appendLogSignals(StringBuilder context, KubernetesNamespaceDiagnostics diagnostics, String applicationName) {
        context.append("podLogs:\n");
        prioritizedPodLogs(diagnostics.podLogs()).stream()
                .filter(log -> applicationName == null || log.podName().contains(applicationName))
                .limit(POD_LOG_CONTEXT_LIMIT)
                .forEach(log -> appendPodLog(context, log, 1200));
    }

    /** AnalysisApplicationService의 appendPerformanceScalingSignals 처리에 필요한 업무 로직을 수행한다. */
    private void appendPerformanceScalingSignals(StringBuilder context, KubernetesNamespaceDiagnostics diagnostics, String applicationName) {
        context.append("resourceKindCounts:\n");
        resourceKindCounts(diagnostics.resources()).forEach(kind -> context.append("- ")
                .append(kind.resourceType()).append('=').append(kind.count()).append('\n'));
        context.append("resourceSignals:\n");
        prioritizedResources(diagnostics.resources()).stream()
                .filter(resource -> applicationName == null || resource.resourceName().contains(applicationName))
                .limit(30)
                .forEach(resource -> appendResource(context, resource));
        context.append("warningEventSignals:\n");
        prioritizedEvents(diagnostics.events()).stream()
                .limit(16)
                .forEach(event -> appendEvent(context, event, 500));
    }

    /** AnalysisApplicationService의 appendRiskTimelineSignals 처리에 필요한 업무 로직을 수행한다. */
    private void appendRiskTimelineSignals(StringBuilder context, KubernetesNamespaceDiagnostics diagnostics) {
        NamespaceDiagnosticsResult.RiskForecast forecast = riskForecast(diagnostics);
        context.append("riskForecastSeed overallRisk=").append(forecast.overallRisk())
                .append(" riskLevel=").append(forecast.riskLevel())
                .append(" horizon=").append(forecast.horizon())
                .append(" summary=").append(forecast.summary())
                .append('\n');
        forecast.predictions().stream()
                .limit(12)
                .forEach(prediction -> context.append("- ")
                        .append(prediction.severity()).append(' ')
                        .append(prediction.category())
                        .append(" probability=").append(prediction.probability()).append("%")
                        .append(" target=").append(valueOrBlank(prediction.resourceKind())).append('/')
                        .append(valueOrBlank(prediction.resourceName()))
                        .append(" signal=").append(valueOrBlank(prediction.signal()))
                        .append(" recommendation=").append(valueOrBlank(prediction.recommendation()))
                        .append(" evidence=").append(valueOrBlank(prediction.evidence()))
                        .append('\n'));
        context.append("changeTimelineSeed:\n");
        changeTimeline(diagnostics).stream()
                .limit(16)
                .forEach(item -> context.append("- ")
                        .append(item.occurredAt()).append(' ')
                        .append(item.severity()).append(' ')
                        .append(item.category())
                        .append(" target=").append(valueOrBlank(item.resourceKind())).append('/')
                        .append(valueOrBlank(item.resourceName()))
                        .append(" title=").append(valueOrBlank(item.title()))
                        .append(" suspectedChange=").append(valueOrBlank(item.suspectedChange()))
                        .append('\n'));
    }

    /** AnalysisApplicationService의 appendRunbookSignals 처리에 필요한 업무 로직을 수행한다. */
    private void appendRunbookSignals(StringBuilder context, KubernetesNamespaceDiagnostics diagnostics) {
        context.append("runbookSeed:\n");
        runbookActions(diagnostics).stream()
                .limit(14)
                .forEach(action -> context.append("- ")
                        .append(action.priority()).append(' ')
                        .append(action.targetKind()).append('/')
                        .append(action.targetName())
                        .append(" title=").append(action.title())
                        .append(" reason=").append(action.reason())
                        .append(" command=").append(action.command())
                        .append(" destructive=").append(action.destructive())
                        .append('\n'));
        context.append("topWarningEvents:\n");
        diagnostics.events().stream()
                .filter(this::isWarningEvent)
                .limit(8)
                .forEach(event -> appendEvent(context, event, 400));
    }

    /** AnalysisApplicationService의 analyzeClusterWithFallback 처리의 핵심 작업 흐름을 실행한다. */
    private String analyzeClusterWithFallback(Cluster cluster, ClusterAnalysisContext analysisContext,
                                              SupportedLocale locale) {
        long startedNanos = System.nanoTime();
        String rootCauseContext = clusterSectionContext(cluster, analysisContext, "cluster-root-cause");
        String riskPostureContext = clusterSectionContext(cluster, analysisContext, "cluster-risk-posture");
        String runbookContext = clusterSectionContext(cluster, analysisContext, "cluster-runbook-operations");

        CompletableFuture<AnalysisSectionExecutor.Result> rootCause = sectionExecutor.execute(cluster.tenantId(),
                "cluster-root-cause",
                """
                        Return JSON with fields: summary, severity, riskScore, findings, rootCauses, evidence.
                        Identify cross-namespace hotspots and correlate unhealthy resources with repeated warning events.
                        Do not generate performance, scaling, risk forecast, or runbook content.
                        %s
                        """.formatted(localePolicy.instruction(locale)),
                rootCauseContext
        );
        CompletableFuture<AnalysisSectionExecutor.Result> riskPosture = sectionExecutor.execute(cluster.tenantId(),
                "cluster-risk-posture",
                """
                        Return JSON with fields: performance, scaling, riskForecast, changeTimeline.
                        Use only Kubernetes API status, topology, policy, replica, endpoint, storage, and event evidence.
                        Explicitly state that CPU and memory utilization are unavailable without metrics.
                        Do not generate root cause or runbook content.
                        %s
                        """.formatted(localePolicy.instruction(locale)),
                riskPostureContext
        );
        CompletableFuture<AnalysisSectionExecutor.Result> runbook = sectionExecutor.execute(cluster.tenantId(),
                "cluster-runbook-operations",
                """
                        Return JSON with fields: runbookActions, recommendations, operationsGuide, nextActions, verificationCommands.
                        Prefer cluster-wide and namespace-scoped read-only verification before safe changes.
                        Mark destructive commands explicitly and explain why every command is useful.
                        %s
                        """.formatted(localePolicy.instruction(locale)),
                runbookContext
        );

        List<AnalysisSectionExecutor.Result> sections = List.of(rootCause.join(), riskPosture.join(), runbook.join());
        ObjectNode result = parseObject(clusterFallbackJson(cluster, analysisContext,
                new RuntimeException("section analysis baseline"), startedNanos, false));
        long successfulSections = sections.stream().filter(AnalysisSectionExecutor.Result::successful).count();
        String analysisMode = successfulSections == sections.size()
                ? "cluster-sectioned"
                : "cluster-sectioned-partial-fallback";
        result.put("analysisMode", analysisMode);
        result.put("locale", locale.tag());
        result.put("confidence", successfulSections == sections.size() ? 0.72 : successfulSections == 0 ? 0.35 : 0.55);

        analysisResultAssembler.mergeClusterSections(result, sections);
        analysisResultAssembler.writeSectionTelemetry(result, analysisMode, cluster.name(),
                elapsedMillis(startedNanos), sections,
                rootCauseContext.length() + riskPostureContext.length() + runbookContext.length(), null);
        enrichRunbookActions(result);
        result.set("commandSafety", commandSafety(result));
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to merge cluster section analysis result", exception);
        }
    }

    /** AnalysisApplicationService의 clusterSectionContext 처리에 필요한 업무 로직을 수행한다. */
    private String clusterSectionContext(Cluster cluster, ClusterAnalysisContext analysisContext, String sectionName) {
        StringBuilder context = new StringBuilder(CLUSTER_SECTION_CONTEXT_CHAR_LIMIT);
        context.append("analysisMode=cluster-sectioned\n")
                .append("section=").append(sectionName).append('\n')
                .append("clusterId=").append(cluster.id()).append('\n')
                .append("clusterName=").append(cluster.name()).append('\n')
                .append("environment=").append(cluster.environment()).append('\n')
                .append("provider=").append(cluster.provider()).append('\n')
                .append("metricsAvailable=false\n");

        List<KubernetesResourceSnapshot.CollectedResource> resources = analysisContext.resources();
        List<KubernetesEventSnapshot.CollectedEvent> events = analysisContext.events();
        long problemResources = resources.stream().filter(this::isProblemCollectedResource).count();
        long warningEvents = events.stream().filter(this::isWarningCollectedEvent).count();
        context.append("diagnosticCounts resources=").append(resources.size())
                .append(" events=").append(events.size())
                .append(" problemResources=").append(problemResources)
                .append(" warningEvents=").append(warningEvents).append('\n');

        if ("cluster-risk-posture".equals(sectionName)) {
            context.append("resourceKindCounts:\n");
            collectedResourceKindCounts(resources).forEach((kind, count) -> context.append("- ")
                    .append(kind).append('=').append(count).append('\n'));
            context.append("namespaceHotspots:\n");
            namespaceHotspots(resources, events).entrySet().stream().limit(12)
                    .forEach(entry -> context.append("- namespace=").append(entry.getKey())
                            .append(' ').append(entry.getValue()).append('\n'));
        }

        context.append("problemResourceSignals:\n");
        resources.stream().filter(this::isProblemCollectedResource)
                .limit("cluster-root-cause".equals(sectionName) ? 18 : 10)
                .forEach(resource -> appendCollectedResource(context, resource));
        context.append("warningEventSignals:\n");
        events.stream().filter(this::isWarningCollectedEvent)
                .sorted(Comparator.comparing(event -> event.count() == null ? 0 : event.count(), Comparator.reverseOrder()))
                .limit("cluster-root-cause".equals(sectionName) ? 14 : 8)
                .forEach(event -> appendCollectedEvent(context, event, 420));

        if ("cluster-runbook-operations".equals(sectionName)) {
            context.append("verificationSeeds:\n")
                    .append("- kubectl get events -A --sort-by=.lastTimestamp\n")
                    .append("- kubectl get pods -A --field-selector=status.phase!=Running\n")
                    .append("- kubectl get deploy,statefulset,daemonset -A\n")
                    .append("- kubectl get pvc,pv -A\n");
        }
        return limitContext(context.toString(), Math.min(maxAnalysisContextChars, CLUSTER_SECTION_CONTEXT_CHAR_LIMIT));
    }

    /** AnalysisApplicationService의 isTimeoutFailure 처리 조건의 충족 여부를 판단한다. */
    private boolean isTimeoutFailure(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("timeout") || normalized.contains("timed out")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /** AnalysisApplicationService의 namespaceTimeoutFallbackJson 처리에 필요한 업무 로직을 수행한다. */
    private String namespaceTimeoutFallbackJson(UUID clusterId, String namespace, String applicationName,
                                                KubernetesNamespaceDiagnostics diagnostics, RuntimeException exception,
                                                long startedNanos, SupportedLocale locale) {
        List<KubernetesNamespaceDiagnostics.DiagnosticResource> problemResources = diagnostics.resources().stream()
                .filter(this::isProblemResource)
                .filter(resource -> applicationName == null || resource.resourceName().contains(applicationName))
                .limit(10)
                .toList();
        List<KubernetesNamespaceDiagnostics.DiagnosticEvent> warningEvents = diagnostics.events().stream()
                .filter(this::isWarningEvent)
                .limit(10)
                .toList();
        List<KubernetesNamespaceDiagnostics.DiagnosticPodLog> highSignalLogs = diagnostics.podLogs().stream()
                .filter(log -> applicationName == null || log.podName().contains(applicationName))
                .filter(this::isHighSignalLog)
                .limit(8)
                .toList();

        int riskScore = Math.min(100, problemResources.size() * 12 + warningEvents.size() * 8 + highSignalLogs.size() * 6);
        String severity = severityFromRiskScore(riskScore);
        NamespaceDiagnosticsResult.RiskForecast forecast = riskForecast(diagnostics);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", "analysis-result.v1");
        root.put("locale", locale.tag());
        root.put("analysisMode", "kubernetes-api-timeout-fallback");
        root.put("summary", "Ollama namespace analysis timed out, so this result was generated from Kubernetes API evidence. "
                + "Problem resources=" + problemResources.size()
                + ", warning events=" + warningEvents.size()
                + ", high-signal logs=" + highSignalLogs.size() + ".");
        root.put("severity", severity);
        root.put("confidence", 0.35);
        root.put("riskScore", riskScore);

        ArrayNode findings = root.putArray("findings");
        problemResources.forEach(resource -> {
            ObjectNode finding = findings.addObject();
            finding.put("title", "Problem resource detected");
            finding.put("resourceKind", resource.resourceType());
            finding.put("resourceName", resource.resourceName());
            finding.put("namespace", namespace);
            finding.putArray("evidence")
                    .add("status=" + valueOrBlank(resource.status()))
                    .add("summary=" + truncate(resource.summaryJson(), 500));
            finding.put("impact", "Needs operator verification because the Kubernetes status is not healthy.");
        });
        warningEvents.forEach(event -> {
            ObjectNode finding = findings.addObject();
            finding.put("title", "Warning event detected: " + valueOrBlank(event.reason()));
            finding.put("resourceKind", valueOrBlank(event.involvedKind()));
            finding.put("resourceName", valueOrBlank(event.involvedName()));
            finding.put("namespace", namespace);
            finding.putArray("evidence")
                    .add("type=" + valueOrBlank(event.type()))
                    .add("count=" + (event.count() == null ? 0 : event.count()))
                    .add("message=" + truncate(event.message(), 500));
            finding.put("impact", "Repeated warning events can indicate rollout, scheduling, storage, or application risk.");
        });

        ArrayNode rootCauses = root.putArray("rootCauses");
        problemResources.stream().findFirst().ifPresent(resource -> rootCauses.addObject()
                .put("cause", resource.resourceType() + "/" + resource.resourceName() + " unhealthy state")
                .putArray("evidence")
                .add("status=" + valueOrBlank(resource.status()))
                .add(truncate(resource.summaryJson(), 300)));
        warningEvents.stream().findFirst().ifPresent(event -> rootCauses.addObject()
                .put("cause", valueOrBlank(event.reason()) + " on " + valueOrBlank(event.involvedKind()) + "/" + valueOrBlank(event.involvedName()))
                .putArray("evidence")
                .add("count=" + (event.count() == null ? 0 : event.count()))
                .add(truncate(event.message(), 300)));
        ArrayNode logAnalysis = root.putArray("logAnalysis");
        highSignalLogs.forEach(log -> {
            ObjectNode item = logAnalysis.addObject();
            item.put("signal", "WARN");
            item.put("severity", "WARN");
            item.put("podName", log.podName());
            item.put("containerName", log.containerName());
            item.put("pattern", firstLogSignal(log.log()));
            item.put("message", truncate(log.log(), 500));
            item.put("interpretation", "High-signal log captured before Ollama timeout. Verify the pod log directly.");
            item.put("analysis", "High-signal log captured before Ollama timeout. Verify the pod log directly.");
        });
        appendLogIntelligenceAnalysis(root, diagnostics);

        root.set("performance", objectMapper.createObjectNode()
                .put("summary", "Metric-based performance analysis is unavailable without Prometheus. Kubernetes status, events, and logs were used.")
                .set("bottlenecks", objectMapper.createArrayNode()));
        ((ObjectNode) root.get("performance")).putArray("improvements")
                .add("Check pending pods, restart loops, probe failures, endpoint readiness, and resource requests/limits.");
        root.set("scaling", objectMapper.createObjectNode()
                .put("summary", "Scaling guidance is limited to Kubernetes API signals because Ollama timed out before deeper analysis.")
                .set("scaleUpCandidates", objectMapper.createArrayNode()));
        ((ObjectNode) root.get("scaling")).putArray("hpaRecommendations");
        ((ObjectNode) root.get("scaling")).putArray("capacityNotes")
                .add("Check HPA, ResourceQuota, LimitRange, pending pods, and FailedScheduling events before scaling.");

        ObjectNode riskForecast = root.putObject("riskForecast");
        riskForecast.put("summary", forecast.summary());
        riskForecast.put("overallRisk", forecast.overallRisk());
        riskForecast.put("riskLevel", forecast.riskLevel());
        riskForecast.put("horizon", forecast.horizon());
        ArrayNode predictions = riskForecast.putArray("predictions");
        forecast.predictions().stream().limit(8).forEach(prediction -> {
            ObjectNode item = predictions.addObject();
            item.put("category", prediction.category());
            item.put("severity", prediction.severity());
            item.put("probability", prediction.probability());
            item.put("horizon", prediction.horizon());
            item.put("resourceKind", valueOrBlank(prediction.resourceKind()));
            item.put("resourceName", valueOrBlank(prediction.resourceName()));
            item.put("signal", valueOrBlank(prediction.signal()));
            item.put("impact", valueOrBlank(prediction.impact()));
            item.put("recommendation", valueOrBlank(prediction.recommendation()));
            item.putArray("evidence").add(valueOrBlank(prediction.evidence()));
            item.put("verificationCommand", valueOrBlank(prediction.verificationCommand()));
        });

        ArrayNode changeTimeline = root.putArray("changeTimeline");
        changeTimeline(diagnostics).stream().limit(8).forEach(item -> {
            ObjectNode node = changeTimeline.addObject();
            node.put("occurredAt", item.occurredAt() == null ? "" : item.occurredAt().toString());
            node.put("severity", valueOrBlank(item.severity()));
            node.put("category", valueOrBlank(item.category()));
            node.put("resourceKind", valueOrBlank(item.resourceKind()));
            node.put("resourceName", valueOrBlank(item.resourceName()));
            node.put("title", valueOrBlank(item.title()));
            node.put("detail", valueOrBlank(item.detail()));
            node.put("suspectedChange", valueOrBlank(item.suspectedChange()));
            node.put("recommendation", valueOrBlank(item.recommendation()));
        });

        ArrayNode runbookActions = root.putArray("runbookActions");
        runbookActions(diagnostics).stream().limit(8).forEach(action ->
                addRunbook(runbookActions, action.priority(), action.title(), action.targetKind(), action.targetName(),
                        action.reason(), action.command(), action.destructive()));
        addRunbook(runbookActions, "P1", "Namespace warning events 확인", "Namespace", namespace,
                "Ollama timeout fallback result needs event-first verification.",
                "kubectl -n " + namespace + " get events --sort-by=.lastTimestamp", false);

        ArrayNode recommendations = root.putArray("recommendations");
        ObjectNode recommendation = recommendations.addObject();
        recommendation.put("priority", "P1");
        recommendation.put("action", "Review Kubernetes API fallback evidence and retry AI analysis after Ollama recovers.");
        recommendation.put("reason", "Kubernetes API collection succeeded, but Ollama did not complete within the configured timeout.");
        recommendation.putArray("commands")
                .add("kubectl -n " + namespace + " get events --sort-by=.lastTimestamp")
                .add("kubectl -n " + namespace + " get pods -o wide");

        ObjectNode operationsGuide = root.putObject("operationsGuide");
        operationsGuide.put("summary", "Use this fallback as triage, then rerun AI analysis when Ollama capacity is stable.");
        operationsGuide.putArray("shortTerm")
                .add("Verify top warning events and unhealthy resources first.")
                .add("Inspect logs for high-signal pods before changing workload settings.");
        operationsGuide.putArray("mediumTerm")
                .add("Increase Ollama timeout, reduce prompt scope, or use a faster model for large namespaces.")
                .add("Keep resource requests, probes, HPA, PDB, quota, and rollout strategy consistent.");
        operationsGuide.putArray("questionsForOperator")
                .add("Which warning event repeated most recently?")
                .add("Did a deployment, config, image, or storage change happen before the first warning?");

        ArrayNode nextActions = root.putArray("nextActions");
        ObjectNode nextAction = nextActions.addObject();
        nextAction.put("priority", "P1");
        nextAction.put("action", "Review fallback evidence and retry namespace AI analysis after Ollama recovers.");
        nextAction.put("ownerHint", "platform");
        nextAction.put("verification", "Backend error was: " + truncate(exception.getMessage(), 300));

        root.putArray("verificationCommands")
                .add("kubectl -n " + namespace + " get events --sort-by=.lastTimestamp")
                .add("kubectl -n " + namespace + " get pods -o wide")
                .add("kubectl -n " + namespace + " get deploy,statefulset,daemonset,svc,ingress,pvc");

        ObjectNode evidence = root.putObject("evidence");
        ArrayNode evidenceResources = evidence.putArray("resources");
        problemResources.forEach(resource -> evidenceResources.add(resource.resourceType() + "/" + resource.resourceName()
                + " status=" + valueOrBlank(resource.status())));
        ArrayNode evidenceEvents = evidence.putArray("events");
        warningEvents.forEach(event -> evidenceEvents.add(valueOrBlank(event.reason()) + " "
                + valueOrBlank(event.involvedKind()) + "/" + valueOrBlank(event.involvedName())
                + " count=" + (event.count() == null ? 0 : event.count())));
        ArrayNode evidenceLogs = evidence.putArray("logs");
        highSignalLogs.forEach(log -> evidenceLogs.add(log.podName() + "/" + log.containerName()
                + " " + truncate(log.log(), 300)));
        ArrayNode fallbackProblemCards = problemCards(diagnostics);
        ArrayNode fallbackIssueGroups = issueGroups(diagnostics);
        root.set("problemCards", fallbackProblemCards);
        root.set("issueGroups", fallbackIssueGroups);
        root.set("logIntelligence", logIntelligence(diagnostics));
        ObjectNode fallbackActionRecommendations = actionRecommendations(diagnostics, fallbackIssueGroups);
        root.set("actionRecommendations", fallbackActionRecommendations);
        appendActionRecommendationNextActions(root, fallbackActionRecommendations);
        root.set("remediationPlan", remediationPlan(diagnostics, fallbackIssueGroups));
        enrichRootCauseEvidence(root, diagnostics);
        root.set("confidenceValidation", confidenceValidation(diagnostics, fallbackProblemCards, fallbackIssueGroups));
        root.set("evidenceLedger", evidenceLedger(diagnostics, fallbackIssueGroups, fallbackProblemCards));
        root.set("eventNoiseReduction", eventNoiseReduction(diagnostics));
        root.set("correlationMap", correlationMap(diagnostics, fallbackIssueGroups));
        root.set("issueGroupDeepDives", issueGroupDeepDives(diagnostics, fallbackIssueGroups));
        root.set("actionWorkflow", actionWorkflow(fallbackIssueGroups));
        root.set("conclusionValidation", conclusionValidation(root, fallbackIssueGroups));
        root.set("reanalysisPlan", reanalysisPlan(diagnostics, fallbackIssueGroups));
        putSingleCallAnalysisDiagnostics(root, "namespace-timeout-fallback", namespace, startedNanos, 0,
                exception.getMessage());
        collectionDiagnosticsWriter.write(root, diagnostics);
        enrichRunbookActions(root);
        root.set("commandSafety", commandSafety(root));
        root.set("analysisQuality", analysisQuality(root, diagnostics, fallbackIssueGroups, fallbackActionRecommendations));

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception jsonException) {
            throw new IllegalStateException("Failed to create namespace fallback analysis result", jsonException);
        }
    }

    /** AnalysisApplicationService의 clusterFallbackJson 처리에 필요한 업무 로직을 수행한다. */
    private String clusterFallbackJson(Cluster cluster, ClusterAnalysisContext analysisContext, RuntimeException exception,
                                       long startedNanos, boolean timedOut) {
        List<KubernetesResourceSnapshot.CollectedResource> problemResources = analysisContext.resources().stream()
                .filter(this::isProblemCollectedResource)
                .limit(10)
                .toList();
        List<KubernetesEventSnapshot.CollectedEvent> warningEvents = analysisContext.events().stream()
                .filter(this::isWarningCollectedEvent)
                .sorted(Comparator.comparing(event -> event.count() == null ? 0 : event.count(), Comparator.reverseOrder()))
                .limit(10)
                .toList();

        int riskScore = Math.min(100, problemResources.size() * 12 + warningEvents.size() * 8);
        String severity = severityFromRiskScore(riskScore);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", "analysis-result.v1");
        root.put("analysisMode", timedOut ? "kubernetes-api-timeout-fallback" : "kubernetes-api-response-fallback");
        root.put("summary", (timedOut
                ? "Ollama cluster analysis timed out."
                : "AI response did not satisfy the analysis schema.")
                + " This result was generated from Kubernetes API evidence. Problem resources="
                + problemResources.size() + ", warning events=" + warningEvents.size() + ".");
        root.put("severity", severity);
        root.put("confidence", 0.35);
        root.put("riskScore", riskScore);

        ArrayNode findings = root.putArray("findings");
        problemResources.forEach(resource -> {
            ObjectNode finding = findings.addObject();
            finding.put("title", "Problem resource detected");
            finding.put("resourceKind", resource.resourceType());
            finding.put("resourceName", resource.resourceName());
            finding.put("namespace", valueOrBlank(resource.namespace()));
            ArrayNode evidence = finding.putArray("evidence");
            evidence.add("status=" + valueOrBlank(resource.status()));
            evidence.add("summary=" + truncate(resource.summaryJson(), 500));
            finding.put("impact", "Needs operator verification because the live Kubernetes status is not healthy.");
        });
        warningEvents.forEach(event -> {
            ObjectNode finding = findings.addObject();
            finding.put("title", "Warning event detected: " + valueOrBlank(event.reason()));
            finding.put("resourceKind", valueOrBlank(event.involvedKind()));
            finding.put("resourceName", valueOrBlank(event.involvedName()));
            finding.put("namespace", valueOrBlank(event.namespace()));
            ArrayNode evidence = finding.putArray("evidence");
            evidence.add("type=" + valueOrBlank(event.type()));
            evidence.add("count=" + (event.count() == null ? 0 : event.count()));
            evidence.add("message=" + truncate(event.message(), 500));
            finding.put("impact", "Repeated warning events can indicate availability, scheduling, storage, or rollout risk.");
        });

        root.putArray("rootCauses");
        root.putArray("logAnalysis");
        root.set("performance", objectMapper.createObjectNode()
                .put("summary", "Metric-based performance analysis is unavailable without Prometheus. Kubernetes status and event signals were used.")
                .set("bottlenecks", objectMapper.createArrayNode()));
        ((ObjectNode) root.get("performance")).putArray("improvements")
                .add("Review resource requests/limits, pending pods, endpoint readiness, and repeated warning events.");
        root.set("scaling", objectMapper.createObjectNode()
                .put("summary", "Scaling guidance is limited to deterministic Kubernetes API signals because the AI response was unavailable.")
                .set("scaleUpCandidates", objectMapper.createArrayNode()));
        ((ObjectNode) root.get("scaling")).putArray("hpaRecommendations");
        ((ObjectNode) root.get("scaling")).putArray("capacityNotes")
                .add("Check pending pods and FailedScheduling events before scaling workloads or nodes.");
        ObjectNode riskForecast = root.putObject("riskForecast");
        riskForecast.put("summary", "Risk forecast generated from current problem resources and warning event frequency.");
        ArrayNode predictions = riskForecast.putArray("predictions");
        warningEvents.stream().limit(5).forEach(event -> {
            ObjectNode prediction = predictions.addObject();
            prediction.put("category", "stability");
            prediction.put("severity", severity);
            prediction.put("probability", Math.min(90, 40 + (event.count() == null ? 0 : event.count()) * 10));
            prediction.put("horizon", "next sync cycle");
            prediction.put("resourceKind", valueOrBlank(event.involvedKind()));
            prediction.put("resourceName", valueOrBlank(event.involvedName()));
            prediction.put("signal", valueOrBlank(event.reason()));
            prediction.put("impact", truncate(event.message(), 300));
            prediction.put("recommendation", "Verify the resource with kubectl describe and inspect recent namespace events.");
            prediction.putArray("evidence").add(truncate(event.message(), 500));
            prediction.put("verificationCommand", "kubectl -n " + valueOrBlank(event.namespace())
                    + " describe " + valueOrBlank(event.involvedKind()).toLowerCase() + " " + valueOrBlank(event.involvedName()));
        });
        root.putArray("changeTimeline");
        ArrayNode runbookActions = root.putArray("runbookActions");
        addRunbook(runbookActions, "P1", "Cluster warning events 확인", "Cluster", cluster.name(),
                "AI fallback result needs event-first verification.",
                "kubectl get events -A --sort-by=.lastTimestamp", false);
        addRunbook(runbookActions, "P1", "Problem pod 확인", "Pod", "all-namespaces",
                "Problem resources or warning events were found in the live inventory.",
                "kubectl get pods -A --field-selector=status.phase!=Running", false);
        ArrayNode recommendations = root.putArray("recommendations");
        ObjectNode recommendation = recommendations.addObject();
        recommendation.put("priority", "P1");
        recommendation.put("action", "Review Kubernetes evidence, then retry AI analysis with a healthy local model runtime.");
        recommendation.put("reason", timedOut
                ? "Kubernetes API collection succeeded, but Ollama did not complete within the configured timeout."
                : "Kubernetes API collection succeeded, but the AI response did not satisfy the required schema.");
        recommendation.putArray("commands")
                .add("kubectl get events -A --sort-by=.lastTimestamp")
                .add("kubectl get pods -A -o wide");
        ObjectNode operationsGuide = root.putObject("operationsGuide");
        operationsGuide.put("summary", "Use this evidence-based fallback for triage, then rerun AI analysis after checking the local model runtime.");
        operationsGuide.putArray("shortTerm")
                .add("Verify top warning events and non-running pods first.")
                .add("If the cluster is large, analyze a high-risk namespace before cluster-wide analysis.");
        operationsGuide.putArray("mediumTerm")
                .add("Increase Ollama timeout or use a faster model for cluster-wide analysis.")
                .add("Keep cluster-wide AI prompts compact and evidence-focused.");
        operationsGuide.putArray("questionsForOperator")
                .add("Which namespace owns the most repeated warning events?")
                .add("Are any non-running pods tied to recent deployment or storage changes?");
        ArrayNode nextActions = root.putArray("nextActions");
        ObjectNode nextAction = nextActions.addObject();
        nextAction.put("priority", "P1");
        nextAction.put("action", "Review fallback evidence and retry cluster AI analysis.");
        nextAction.put("ownerHint", "platform");
        nextAction.put("verification", "Backend error was: " + truncate(exception.getMessage(), 300));
        root.putArray("verificationCommands")
                .add("kubectl get events -A --sort-by=.lastTimestamp")
                .add("kubectl get pods -A --field-selector=status.phase!=Running")
                .add("kubectl get deploy,statefulset,daemonset -A");
        ObjectNode evidence = root.putObject("evidence");
        ArrayNode evidenceResources = evidence.putArray("resources");
        problemResources.forEach(resource -> evidenceResources.add(valueOrBlank(resource.namespace()) + " "
                + resource.resourceType() + "/" + resource.resourceName() + " status=" + valueOrBlank(resource.status())));
        ArrayNode evidenceEvents = evidence.putArray("events");
        warningEvents.forEach(event -> evidenceEvents.add(valueOrBlank(event.namespace()) + " "
                + valueOrBlank(event.reason()) + " " + valueOrBlank(event.involvedKind()) + "/"
                + valueOrBlank(event.involvedName()) + " count=" + (event.count() == null ? 0 : event.count())));
        evidence.putArray("logs");
        putSingleCallAnalysisDiagnostics(root, timedOut ? "cluster-timeout-fallback" : "cluster-response-fallback",
                cluster.name(), startedNanos,
                analysisContext.context().length(), exception.getMessage());
        enrichRunbookActions(root);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception jsonException) {
            throw new IllegalStateException("Failed to create cluster fallback analysis result", jsonException);
        }
    }

    /** AnalysisApplicationService의 severityFromRiskScore 처리에 필요한 업무 로직을 수행한다. */
    private String severityFromRiskScore(int riskScore) {
        // 동일한 riskScore가 화면과 분석 결과에서 서로 다른 등급으로 보이지 않도록 기준을 단일화한다.
        return riskLevel(riskScore);
    }

    /** AnalysisApplicationService의 addRunbook 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addRunbook(ArrayNode runbookActions, String priority, String title, String targetKind,
                            String targetName, String reason, String command, boolean destructive) {
        ObjectNode action = runbookActions.addObject();
        action.put("priority", priority);
        action.put("category", COMMAND_POLICY.runbookCategory(command, destructive));
        action.put("title", title);
        action.put("targetKind", targetKind);
        action.put("targetName", targetName);
        action.put("reason", reason);
        action.put("why", runbookWhy(reason, command));
        action.put("command", command);
        action.put("commandType", COMMAND_POLICY.commandType(command, destructive));
        action.put("destructive", destructive);
    }

    /** AnalysisApplicationService의 enrichRunbookActions 처리에 필요한 업무 로직을 수행한다. */
    private void enrichRunbookActions(ObjectNode root) {
        JsonNode runbookActions = root.get("runbookActions");
        if (runbookActions == null || !runbookActions.isArray()) {
            return;
        }
        runbookActions.forEach(item -> {
            if (!(item instanceof ObjectNode action)) {
                return;
            }
            String command = action.path("command").asText("");
            boolean destructive = action.path("destructive").asBoolean(COMMAND_POLICY.isDestructive(command));
            action.put("destructive", destructive);
            if (!action.hasNonNull("category") || action.path("category").asText().isBlank()) {
                action.put("category", COMMAND_POLICY.runbookCategory(command, destructive));
            }
            if (!action.hasNonNull("commandType") || action.path("commandType").asText().isBlank()) {
                action.put("commandType", COMMAND_POLICY.commandType(command, destructive));
            }
            if (!action.hasNonNull("why") || action.path("why").asText().isBlank()) {
                action.put("why", runbookWhy(action.path("reason").asText(""), command));
            }
        });
    }

    /** AnalysisApplicationService의 runbookWhy 처리의 핵심 작업 흐름을 실행한다. */
    private String runbookWhy(String reason, String command) {
        String base = valueOrBlank(reason);
        if (!base.isBlank()) {
            return base;
        }
        String normalized = valueOrBlank(command).toLowerCase();
        if (normalized.contains("describe")) {
            return "리소스 이벤트, 조건, volume/image/scheduling 상태를 한 번에 확인하기 위해 실행합니다.";
        }
        if (normalized.contains("logs")) {
            return "애플리케이션 로그에서 반복 오류 또는 경고 패턴을 확인하기 위해 실행합니다.";
        }
        if (normalized.contains("get events")) {
            return "최근 Kubernetes 이벤트 순서와 반복 횟수를 확인하기 위해 실행합니다.";
        }
        return "분석 결과의 근거를 운영자가 직접 검증하기 위해 실행합니다.";
    }

    /** AnalysisApplicationService의 prioritizedResources 처리에 필요한 업무 로직을 수행한다. */
    private List<KubernetesNamespaceDiagnostics.DiagnosticResource> prioritizedResources(
            List<KubernetesNamespaceDiagnostics.DiagnosticResource> resources
    ) {
        return resources.stream()
                .sorted(Comparator.comparing(this::isProblemResource).reversed())
                .toList();
    }

    /** AnalysisApplicationService의 prioritizedEvents 처리에 필요한 업무 로직을 수행한다. */
    private List<KubernetesNamespaceDiagnostics.DiagnosticEvent> prioritizedEvents(
            List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events
    ) {
        return events.stream()
                .sorted(Comparator.comparing(this::isWarningEvent).reversed())
                .toList();
    }

    /** AnalysisApplicationService의 prioritizedPodLogs 처리에 필요한 업무 로직을 수행한다. */
    private List<KubernetesNamespaceDiagnostics.DiagnosticPodLog> prioritizedPodLogs(
            List<KubernetesNamespaceDiagnostics.DiagnosticPodLog> podLogs
    ) {
        return podLogs.stream()
                .sorted(Comparator.comparingInt(log -> logInsight(log).priority()))
                .toList();
    }

    /** AnalysisApplicationService의 appendResource 처리에 필요한 업무 로직을 수행한다. */
    private void appendResource(StringBuilder context, KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        context.append("- ")
                .append(resource.resourceType()).append('/')
                .append(resource.resourceName())
                .append(" status=").append(resource.status())
                .append(" summary=").append(resource.summaryJson())
                .append('\n');
    }

    /** AnalysisApplicationService의 appendEvent 처리에 필요한 업무 로직을 수행한다. */
    private void appendEvent(StringBuilder context, KubernetesNamespaceDiagnostics.DiagnosticEvent event, int messageLimit) {
        context.append("- ")
                .append(event.type()).append(' ')
                .append(event.reason()).append(' ')
                .append(event.involvedKind()).append('/')
                .append(event.involvedName())
                .append(" count=").append(event.count())
                .append(" message=").append(truncate(event.message(), messageLimit))
                .append('\n');
    }

    /** AnalysisApplicationService의 appendPodLog 처리에 필요한 업무 로직을 수행한다. */
    private void appendPodLog(StringBuilder context, KubernetesNamespaceDiagnostics.DiagnosticPodLog log, int logLimit) {
        context.append("- Pod/")
                .append(log.podName())
                .append(" container=").append(log.containerName())
                .append(" truncated=").append(log.truncated())
                .append(" log=").append(valueOrBlank(truncate(log.log(), logLimit)).replace('\n', ' '))
                .append('\n');
    }

    /** AnalysisApplicationService의 isHighSignalLog 처리 조건의 충족 여부를 판단한다. */
    private boolean isHighSignalLog(KubernetesNamespaceDiagnostics.DiagnosticPodLog log) {
        return logInsight(log).matched();
    }

    /** AnalysisApplicationService의 collectNamespaceDiagnostics 처리의 핵심 작업 흐름을 실행한다. */
    private KubernetesNamespaceDiagnostics collectNamespaceDiagnostics(UUID clusterId, String namespace) {
        return kubernetesNamespaceDiagnosticsPort.collectNamespaceDiagnostics(connectionCredential(clusterId), namespace);
    }

    /** AnalysisApplicationService의 diagnosticsResult 처리에 필요한 업무 로직을 수행한다. */
    private NamespaceDiagnosticsResult diagnosticsResult(UUID clusterId, String namespace, KubernetesNamespaceDiagnostics diagnostics) {
        return new NamespaceDiagnosticsResult(
                clusterId,
                namespace,
                diagnostics.collectedAt(),
                diagnostics.resources().size(),
                diagnostics.events().size(),
                (int) diagnostics.events().stream().filter(this::isWarningEvent).count(),
                diagnostics.podLogs().size(),
                healthScore(diagnostics),
                riskForecast(diagnostics),
                changeTimeline(diagnostics),
                runbookActions(diagnostics),
                resourceKindCounts(diagnostics.resources()),
                problemResources(diagnostics.resources()),
                warningEvents(diagnostics.events()),
                evidenceSignals(diagnostics),
                diagnostics.podLogs().stream()
                        .map(log -> new NamespaceDiagnosticsResult.PodLogSignal(log.podName(), log.containerName(), log.truncated()))
                        .limit(20)
                        .toList()
        );
    }

    /** AnalysisApplicationService의 resourceKindCounts 처리에 필요한 업무 로직을 수행한다. */
    private List<NamespaceDiagnosticsResult.ResourceKindCount> resourceKindCounts(
            List<KubernetesNamespaceDiagnostics.DiagnosticResource> resources
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        resources.forEach(resource -> counts.merge(resource.resourceType(), 1, Integer::sum));
        List<NamespaceDiagnosticsResult.ResourceKindCount> result = new ArrayList<>();
        counts.forEach((resourceType, count) -> result.add(new NamespaceDiagnosticsResult.ResourceKindCount(resourceType, count)));
        return result;
    }

    /** AnalysisApplicationService의 problemResources 처리에 필요한 업무 로직을 수행한다. */
    private List<NamespaceDiagnosticsResult.ResourceSignal> problemResources(
            List<KubernetesNamespaceDiagnostics.DiagnosticResource> resources
    ) {
        return resources.stream()
                .filter(this::isProblemResource)
                .map(resource -> new NamespaceDiagnosticsResult.ResourceSignal(
                        resource.resourceType(),
                        resource.resourceName(),
                        valueOrBlank(resource.status()),
                        valueOrBlank(resource.summaryJson())
                ))
                .limit(20)
                .toList();
    }

    /** AnalysisApplicationService의 healthScore 처리에 필요한 업무 로직을 수행한다. */
    private NamespaceDiagnosticsResult.HealthScore healthScore(KubernetesNamespaceDiagnostics diagnostics) {
        long problemResources = diagnostics.resources().stream().filter(this::isProblemResource).count();
        long warningEvents = diagnostics.events().stream().filter(this::isWarningEvent).count();
        long highSignalLogs = diagnostics.podLogs().stream().filter(this::isHighSignalLog).count();
        long unreadyPods = diagnostics.resources().stream()
                .filter(resource -> "Pod".equals(resource.resourceType()))
                .filter(this::isProblemResource)
                .count();
        long missingPolicySignals = diagnostics.resources().stream()
                .filter(resource -> "NetworkPolicy".equals(resource.resourceType())
                        || "ResourceQuota".equals(resource.resourceType())
                        || "LimitRange".equals(resource.resourceType()))
                .count();

        int availability = score(100 - (int) unreadyPods * 18 - (int) warningEvents * 3);
        int stability = score(100 - (int) problemResources * 7 - (int) highSignalLogs * 5);
        int performance = score(100 - (int) problemResources * 4 - (int) warningEvents * 2);
        int security = score(70 + (int) Math.min(missingPolicySignals * 5, 25));
        int operability = score(90 - (int) warningEvents * 2 - (diagnostics.podLogs().isEmpty() ? 10 : 0));
        int overall = score((availability + stability + performance + security + operability) / 5);
        return new NamespaceDiagnosticsResult.HealthScore(availability, stability, performance, security, operability, overall);
    }

    /** AnalysisApplicationService의 riskForecast 처리에 필요한 업무 로직을 수행한다. */
    private NamespaceDiagnosticsResult.RiskForecast riskForecast(KubernetesNamespaceDiagnostics diagnostics) {
        List<NamespaceDiagnosticsResult.RiskPrediction> predictions = new ArrayList<>();
        diagnostics.resources().stream()
                .filter(this::isProblemResource)
                .limit(12)
                .forEach(resource -> predictions.add(resourceRiskPrediction(resource)));
        diagnostics.events().stream()
                .filter(this::isWarningEvent)
                .filter(event -> event.count() == null || event.count() >= 2)
                .limit(8)
                .forEach(event -> predictions.add(eventRiskPrediction(event)));
        diagnostics.podLogs().stream()
                .filter(this::isHighSignalLog)
                .limit(6)
                .forEach(log -> predictions.add(logRiskPrediction(log)));

        addGovernanceRiskPredictions(diagnostics, predictions);
        addScalingRiskPredictions(diagnostics, predictions);

        List<NamespaceDiagnosticsResult.RiskPrediction> prioritized = predictions.stream()
                .sorted(Comparator.comparing(NamespaceDiagnosticsResult.RiskPrediction::probability).reversed())
                .limit(15)
                .toList();
        int overallRisk = prioritized.stream()
                .mapToInt(prediction -> severityWeight(prediction.severity()) + prediction.probability() / 4)
                .max()
                .orElse(0);
        overallRisk = score(overallRisk);
        String summary = prioritized.isEmpty()
                ? "현재 Kubernetes API 신호만으로는 가까운 위험 후보가 낮습니다. 단, 메트릭 기반 포화 예측은 Prometheus 연동 전까지 제한됩니다."
                : "Kubernetes API 신호 기준 " + prioritized.size() + "개의 선제 위험 후보가 감지되었습니다.";
        return new NamespaceDiagnosticsResult.RiskForecast(overallRisk, riskLevel(overallRisk), "next 24h", summary, prioritized);
    }

    /** AnalysisApplicationService의 changeTimeline 처리 대상의 상태를 갱신한다. */
    private List<NamespaceDiagnosticsResult.ChangeTimelineItem> changeTimeline(KubernetesNamespaceDiagnostics diagnostics) {
        List<NamespaceDiagnosticsResult.ChangeTimelineItem> items = new ArrayList<>();
        diagnostics.events().stream()
                .filter(event -> event.eventTime() != null)
                .filter(event -> isWarningEvent(event) || isChangeLikeEvent(event))
                .forEach(event -> items.add(new NamespaceDiagnosticsResult.ChangeTimelineItem(
                        event.eventTime(),
                        isWarningEvent(event) ? "WARN" : "INFO",
                        eventCategory(event),
                        valueOrBlank(event.involvedKind()),
                        valueOrBlank(event.involvedName()),
                        valueOrBlank(event.reason()),
                        truncate(event.message(), 260),
                        suspectedChange(event),
                        "같은 시간대의 Pod 상태, ReplicaSet rollout, 로그 신호를 함께 확인하세요."
                )));
        diagnostics.resources().stream()
                .filter(this::isProblemResource)
                .limit(12)
                .forEach(resource -> items.add(new NamespaceDiagnosticsResult.ChangeTimelineItem(
                        diagnostics.collectedAt(),
                        "WARN",
                        "current-state",
                        resource.resourceType(),
                        resource.resourceName(),
                        "Current unhealthy state",
                        "status=" + valueOrBlank(resource.status()),
                        "현재 수집 시점에 비정상 상태가 확인되었습니다.",
                        describeCommand(resource.resourceType(), resource.resourceName())
                )));
        diagnostics.podLogs().stream()
                .filter(this::isHighSignalLog)
                .limit(8)
                .forEach(log -> items.add(new NamespaceDiagnosticsResult.ChangeTimelineItem(
                        diagnostics.collectedAt(),
                        "INFO",
                        "log-signal",
                        "Pod",
                        log.podName(),
                        "High signal log",
                        firstLogSignal(log.log()),
                        "로그상 반복 오류 또는 경고 패턴이 감지되었습니다.",
                        "kubectl logs pod/" + log.podName() + " -n ${namespace} -c " + log.containerName() + " --tail=200"
                )));
        return items.stream()
                .sorted(Comparator.comparing(NamespaceDiagnosticsResult.ChangeTimelineItem::occurredAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(30)
                .toList();
    }

    /** AnalysisApplicationService의 runbookActions 처리의 핵심 작업 흐름을 실행한다. */
    private List<NamespaceDiagnosticsResult.RunbookAction> runbookActions(KubernetesNamespaceDiagnostics diagnostics) {
        List<NamespaceDiagnosticsResult.RunbookAction> actions = new ArrayList<>();
        problemResources(diagnostics.resources()).stream()
                .limit(8)
                .forEach(resource -> actions.add(new NamespaceDiagnosticsResult.RunbookAction(
                        priorityForSeverity(resource.status()),
                        "리소스 상태 상세 확인",
                        resource.resourceType(),
                        resource.resourceName(),
                        "문제 후보 리소스 status=" + resource.status(),
                        describeCommand(resource.resourceType(), resource.resourceName()),
                        false
                )));
        warningEvents(diagnostics.events()).stream()
                .limit(6)
                .forEach(event -> actions.add(new NamespaceDiagnosticsResult.RunbookAction(
                        event.count() != null && event.count() >= 5 ? "P1" : "P2",
                        "관련 이벤트 확인",
                        valueOrBlank(event.involvedKind()),
                        valueOrBlank(event.involvedName()),
                        valueOrBlank(event.reason()) + ": " + valueOrBlank(event.message()),
                        "kubectl get events -n ${namespace} --field-selector involvedObject.name="
                                + valueOrBlank(event.involvedName()) + " --sort-by=.lastTimestamp",
                        false
                )));
        diagnostics.podLogs().stream()
                .filter(this::isHighSignalLog)
                .limit(6)
                .forEach(log -> actions.add(new NamespaceDiagnosticsResult.RunbookAction(
                        "HIGH".equals(logInsight(log).severity()) ? "P1" : "P2",
                        logInsight(log).title(),
                        "Pod",
                        log.podName(),
                        logInsight(log).recommendedNextAction(),
                        logInsight(log).verificationCommand().replace(" -n " + valueOrBlank(log.namespace()), " -n ${namespace}"),
                        false
                )));
        if (actions.isEmpty()) {
            actions.add(new NamespaceDiagnosticsResult.RunbookAction(
                    "P3",
                    "Namespace 상태 기준선 확인",
                    "Namespace",
                    "${namespace}",
                    "즉시 문제 신호가 낮으므로 리소스와 이벤트 기준선을 확인합니다.",
                    "kubectl get all,events -n ${namespace}",
                    false
            ));
        }
        return actions.stream().limit(20).toList();
    }

    /** AnalysisApplicationService의 isChangeLikeEvent 처리 조건의 충족 여부를 판단한다. */
    private boolean isChangeLikeEvent(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String reason = valueOrBlank(event.reason()).toLowerCase();
        return reason.contains("created")
                || reason.contains("started")
                || reason.contains("scheduled")
                || reason.contains("pulled")
                || reason.contains("scaling")
                || reason.contains("successfulcreate")
                || reason.contains("successfuldelete");
    }

    /** AnalysisApplicationService의 eventCategory 처리에 필요한 업무 로직을 수행한다. */
    private String eventCategory(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String reason = valueOrBlank(event.reason()).toLowerCase();
        if (reason.contains("failed") || reason.contains("backoff") || reason.contains("unhealthy")) {
            return "degradation";
        }
        if (reason.contains("scheduled") || reason.contains("pulled") || reason.contains("created") || reason.contains("started")) {
            return "rollout-change";
        }
        return "event";
    }

    /** AnalysisApplicationService의 suspectedChange 처리에 필요한 업무 로직을 수행한다. */
    private String suspectedChange(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String reason = valueOrBlank(event.reason()).toLowerCase();
        if (reason.contains("unhealthy")) {
            return "Probe 설정, 애플리케이션 readiness, 또는 시작 시간 변화 가능성이 있습니다.";
        }
        if (reason.contains("failedscheduling")) {
            return "Node capacity, taint/toleration, affinity, PVC binding 상태 변화 가능성이 있습니다.";
        }
        if (reason.contains("failedmount")) {
            return "ConfigMap/Secret/PVC mount 또는 StorageClass 상태 변화 가능성이 있습니다.";
        }
        if (reason.contains("backoff") || reason.contains("pull")) {
            return "이미지, registry 인증, imagePullPolicy 또는 배포 이미지 변경 가능성이 있습니다.";
        }
        if (reason.contains("created") || reason.contains("started") || reason.contains("scheduled")) {
            return "최근 rollout 또는 Pod 재생성이 발생했습니다.";
        }
        return "이벤트 발생 시점 전후의 리소스 변경 여부 확인이 필요합니다.";
    }

    /** AnalysisApplicationService의 priorityForSeverity 처리에 필요한 업무 로직을 수행한다. */
    private String priorityForSeverity(String status) {
        String value = valueOrBlank(status).toLowerCase();
        if (value.contains("crash") || value.contains("failed") || value.contains("error")) {
            return "P1";
        }
        if (value.contains("pending") || value.contains("unknown")) {
            return "P2";
        }
        return "P3";
    }

    /** AnalysisApplicationService의 severityToPriority 처리에 필요한 업무 로직을 수행한다. */
    private String severityToPriority(String severity) {
        return switch (valueOrBlank(severity).toUpperCase(Locale.ROOT)) {
            case "CRITICAL", "HIGH" -> "P1";
            case "MEDIUM" -> "P2";
            default -> "P3";
        };
    }

    /** AnalysisApplicationService의 resourceRiskPrediction 처리에 필요한 업무 로직을 수행한다. */
    private NamespaceDiagnosticsResult.RiskPrediction resourceRiskPrediction(
            KubernetesNamespaceDiagnostics.DiagnosticResource resource
    ) {
        String status = valueOrBlank(resource.status());
        String lowerStatus = status.toLowerCase();
        String severity = lowerStatus.contains("crash") || lowerStatus.contains("failed") || lowerStatus.contains("error")
                ? "HIGH"
                : "MEDIUM";
        String category = switch (resource.resourceType()) {
            case "Pod", "Deployment", "ReplicaSet", "StatefulSet", "DaemonSet" -> "availability";
            case "PersistentVolumeClaim" -> "storage";
            case "HorizontalPodAutoscaler" -> "scaling";
            case "Endpoint", "Service", "Ingress" -> "traffic";
            default -> "stability";
        };
        return new NamespaceDiagnosticsResult.RiskPrediction(
                category,
                severity,
                "HIGH".equals(severity) ? 82 : 68,
                "next 1-6h",
                resource.resourceType(),
                resource.resourceName(),
                "status=" + status,
                "현재 비정상 상태가 지속되면 요청 실패, 배포 지연, 또는 복구 지연으로 확산될 수 있습니다.",
                "상세 진단에서 이벤트와 로그를 먼저 확인하고, 원인별로 이미지/스케줄링/볼륨/프로브 설정을 검증하세요.",
                truncate(resource.summaryJson(), 260),
                describeCommand(resource.resourceType(), resource.resourceName())
        );
    }

    /** AnalysisApplicationService의 eventRiskPrediction 처리에 필요한 업무 로직을 수행한다. */
    private NamespaceDiagnosticsResult.RiskPrediction eventRiskPrediction(
            KubernetesNamespaceDiagnostics.DiagnosticEvent event
    ) {
        int count = event.count() == null ? 1 : event.count();
        return new NamespaceDiagnosticsResult.RiskPrediction(
                "event-recurrence",
                count >= 5 ? "HIGH" : "MEDIUM",
                Math.min(90, 58 + count * 6),
                "next 1-24h",
                valueOrBlank(event.involvedKind()),
                valueOrBlank(event.involvedName()),
                valueOrBlank(event.reason()) + " repeated " + count + " time(s)",
                "같은 Warning 이벤트가 반복되면 일시적 오류가 운영 장애로 고착될 가능성이 있습니다.",
                "관련 리소스 describe 결과와 최근 이벤트 타임라인을 비교해 반복 주기를 확인하세요.",
                truncate(event.message(), 260),
                describeCommand(valueOrBlank(event.involvedKind()), valueOrBlank(event.involvedName()))
        );
    }

    /** AnalysisApplicationService의 logRiskPrediction 처리에 필요한 업무 로직을 수행한다. */
    private NamespaceDiagnosticsResult.RiskPrediction logRiskPrediction(
            KubernetesNamespaceDiagnostics.DiagnosticPodLog log
    ) {
        LogInsight insight = logInsight(log);
        String signal = insight.matched() ? insight.signal() : firstLogSignal(log.log());
        String severity = insight.matched() ? insight.severity() : "MEDIUM";
        return new NamespaceDiagnosticsResult.RiskPrediction(
                insight.matched() ? insight.category() : "log-pattern",
                severity,
                "HIGH".equals(severity) ? 86 : "MEDIUM".equals(severity) ? 68 : 42,
                "next 1-24h",
                "Pod",
                log.podName(),
                signal,
                insight.matched() ? insight.operatorMeaning() : "애플리케이션 로그 위험 신호가 반복되면 재시작, 성능 저하, 또는 사용자 오류로 이어질 수 있습니다.",
                insight.matched() ? insight.recommendedNextAction() : "선택한 row 수로 로그 범위를 늘려 반복 여부를 확인하고 애플리케이션 설정/의존성 상태를 점검하세요.",
                "container=" + log.containerName(),
                (insight.matched() ? insight.verificationCommand() : "kubectl logs pod/" + log.podName() + " -n " + valueOrBlank(log.namespace()) + " -c " + log.containerName() + " --tail=200")
                        .replace(" -n " + valueOrBlank(log.namespace()), " -n ${namespace}")
        );
    }

    /** AnalysisApplicationService의 addGovernanceRiskPredictions 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addGovernanceRiskPredictions(
            KubernetesNamespaceDiagnostics diagnostics,
            List<NamespaceDiagnosticsResult.RiskPrediction> predictions
    ) {
        boolean hasWorkload = diagnostics.resources().stream().anyMatch(resource ->
                "Deployment".equals(resource.resourceType()) || "StatefulSet".equals(resource.resourceType()) || "Pod".equals(resource.resourceType()));
        if (!hasWorkload) {
            return;
        }
        addMissingKindPrediction(diagnostics, predictions, "ResourceQuota", "capacity-governance",
                "namespace에 ResourceQuota가 없어 리소스 사용량 상한을 Kubernetes API 정책으로 제한하지 못합니다.",
                "namespace별 quota 기준을 정하고 requests/limits와 함께 적용하세요.");
        addMissingKindPrediction(diagnostics, predictions, "LimitRange", "resource-defaults",
                "LimitRange가 없어 기본 requests/limits가 없는 Pod가 생성될 수 있습니다.",
                "기본 request/limit 정책을 정의해 스케줄링 품질과 용량 예측 가능성을 높이세요.");
        addMissingKindPrediction(diagnostics, predictions, "NetworkPolicy", "network-isolation",
                "NetworkPolicy가 없어 namespace 내부/외부 통신 제어가 약할 수 있습니다.",
                "업무 중요도에 맞춰 기본 deny와 필요한 allow 정책을 단계적으로 적용하세요.");
    }

    /** AnalysisApplicationService의 addMissingKindPrediction 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addMissingKindPrediction(
            KubernetesNamespaceDiagnostics diagnostics,
            List<NamespaceDiagnosticsResult.RiskPrediction> predictions,
            String resourceKind,
            String category,
            String impact,
            String recommendation
    ) {
        boolean exists = diagnostics.resources().stream().anyMatch(resource -> resourceKind.equals(resource.resourceType()));
        if (exists) {
            return;
        }
        predictions.add(new NamespaceDiagnosticsResult.RiskPrediction(
                category,
                "LOW",
                42,
                "next 7d",
                resourceKind,
                "-",
                resourceKind + " not found",
                impact,
                recommendation,
                "resource inventory does not include " + resourceKind,
                "kubectl get " + kubectlResourceName(resourceKind) + " -n ${namespace}"
        ));
    }

    /** AnalysisApplicationService의 addScalingRiskPredictions 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addScalingRiskPredictions(
            KubernetesNamespaceDiagnostics diagnostics,
            List<NamespaceDiagnosticsResult.RiskPrediction> predictions
    ) {
        boolean hasDeployment = diagnostics.resources().stream().anyMatch(resource -> "Deployment".equals(resource.resourceType()));
        boolean hasHpa = diagnostics.resources().stream().anyMatch(resource -> "HorizontalPodAutoscaler".equals(resource.resourceType()));
        if (!hasDeployment || hasHpa) {
            return;
        }
        predictions.add(new NamespaceDiagnosticsResult.RiskPrediction(
                "scaling",
                "MEDIUM",
                55,
                "next traffic spike",
                "HorizontalPodAutoscaler",
                "-",
                "Deployment exists but HPA is not configured",
                "트래픽 증가 시 수동 증설 전까지 처리량 부족이나 지연 증가가 발생할 수 있습니다.",
                "핵심 Deployment부터 resource requests와 HPA target을 정의하세요. 실제 CPU/메모리 기준은 Prometheus 또는 metrics-server 연동 후 보정해야 합니다.",
                "resource inventory includes Deployment but no HorizontalPodAutoscaler",
                "kubectl get hpa -n ${namespace}"
        ));
    }

    /** AnalysisApplicationService의 severityWeight 처리에 필요한 업무 로직을 수행한다. */
    private int severityWeight(String severity) {
        return switch (valueOrBlank(severity).toUpperCase()) {
            case "CRITICAL" -> 80;
            case "HIGH" -> 65;
            case "MEDIUM" -> 45;
            case "LOW" -> 25;
            default -> 10;
        };
    }

    /** AnalysisApplicationService의 riskLevel 처리에 필요한 업무 로직을 수행한다. */
    private String riskLevel(int riskScore) {
        if (riskScore >= 85) {
            return "CRITICAL";
        }
        if (riskScore >= 70) {
            return "HIGH";
        }
        if (riskScore >= 45) {
            return "MEDIUM";
        }
        if (riskScore >= 20) {
            return "LOW";
        }
        return "INFO";
    }

    /** AnalysisApplicationService의 describeCommand 처리에 필요한 업무 로직을 수행한다. */
    private String describeCommand(String resourceKind, String resourceName) {
        if (valueOrBlank(resourceKind).isBlank() || valueOrBlank(resourceName).isBlank() || "-".equals(resourceName)) {
            return "kubectl get events -n ${namespace} --sort-by=.lastTimestamp";
        }
        return "kubectl describe " + kubectlResourceName(resourceKind) + "/" + resourceName + " -n ${namespace}";
    }

    /** AnalysisApplicationService의 kubectlResourceName 처리에 필요한 업무 로직을 수행한다. */
    private String kubectlResourceName(String resourceKind) {
        return switch (valueOrBlank(resourceKind)) {
            case "Pod" -> "pod";
            case "Deployment" -> "deployment";
            case "ReplicaSet" -> "replicaset";
            case "StatefulSet" -> "statefulset";
            case "DaemonSet" -> "daemonset";
            case "Service" -> "service";
            case "Endpoint" -> "endpoints";
            case "Ingress" -> "ingress";
            case "PersistentVolumeClaim" -> "pvc";
            case "HorizontalPodAutoscaler" -> "hpa";
            case "ResourceQuota" -> "resourcequota";
            case "LimitRange" -> "limitrange";
            case "NetworkPolicy" -> "networkpolicy";
            default -> valueOrBlank(resourceKind).toLowerCase();
        };
    }

    /** AnalysisApplicationService의 score 처리에 필요한 업무 로직을 수행한다. */
    private int score(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /** AnalysisApplicationService의 evidenceSignals 처리에 필요한 업무 로직을 수행한다. */
    private List<NamespaceDiagnosticsResult.EvidenceSignal> evidenceSignals(KubernetesNamespaceDiagnostics diagnostics) {
        List<NamespaceDiagnosticsResult.EvidenceSignal> signals = new ArrayList<>();
        problemResources(diagnostics.resources()).forEach(resource -> signals.add(new NamespaceDiagnosticsResult.EvidenceSignal(
                "WARN",
                resource.resourceType() + "/" + resource.resourceName(),
                "status=" + resource.status()
        )));
        warningEvents(diagnostics.events()).forEach(event -> signals.add(new NamespaceDiagnosticsResult.EvidenceSignal(
                "WARN",
                valueOrBlank(event.involvedKind()) + "/" + valueOrBlank(event.involvedName()),
                valueOrBlank(event.reason()) + ": " + valueOrBlank(truncate(event.message(), 180))
        )));
        diagnostics.podLogs().stream()
                .filter(this::isHighSignalLog)
                .limit(10)
                .forEach(log -> signals.add(new NamespaceDiagnosticsResult.EvidenceSignal(
                        "INFO",
                        "Pod/" + log.podName() + " container=" + log.containerName(),
                        firstLogSignal(log.log())
                )));
        return signals.stream().limit(30).toList();
    }

    /** AnalysisApplicationService의 firstLogSignal 처리에 필요한 업무 로직을 수행한다. */
    private String firstLogSignal(String log) {
        return valueOrBlank(log).lines()
                .map(String::trim)
                .filter(line -> {
                    String lower = line.toLowerCase();
                    return lower.contains("error")
                            || lower.contains("warn")
                            || lower.contains("exception")
                            || lower.contains("failed")
                            || lower.contains("timeout")
                            || lower.contains("address already in use")
                            || lower.contains("bind()")
                            || lower.contains("cannot bind")
                            || lower.contains("listen tcp")
                            || lower.contains("failed to listen")
                            || lower.contains("port is already allocated")
                            || lower.contains("permission denied")
                            || lower.contains("eaddrinuse")
                            || lower.contains("development server");
                })
                .sorted(Comparator.comparingInt(this::logSignalPriority))
                .findFirst()
                .map(line -> truncate(line, 220))
                .orElse(truncate(valueOrBlank(log).replace('\n', ' '), 220));
    }

    /** AnalysisApplicationService의 logSignalPriority 처리에 필요한 업무 로직을 수행한다. */
    private int logSignalPriority(String line) {
        String lower = valueOrBlank(line).toLowerCase(Locale.ROOT);
        if (lower.contains("emerg") || lower.contains("fatal") || lower.contains("panic")) {
            return 0;
        }
        if (lower.contains("bind()") || lower.contains("cannot bind") || lower.contains("permission denied")
                || lower.contains("address already in use") || lower.contains("eaddrinuse")) {
            return 1;
        }
        if (lower.contains("failed") || lower.contains("error") || lower.contains("exception")) {
            return 2;
        }
        if (lower.contains("timeout") || lower.contains("warn")) {
            return 3;
        }
        return 4;
    }

    /** AnalysisApplicationService의 warningEvents 처리에 필요한 업무 로직을 수행한다. */
    private List<NamespaceDiagnosticsResult.EventSignal> warningEvents(
            List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events
    ) {
        return events.stream()
                .filter(this::isWarningEvent)
                .map(event -> new NamespaceDiagnosticsResult.EventSignal(
                        event.type(),
                        event.reason(),
                        event.involvedKind(),
                        event.involvedName(),
                        truncate(event.message(), 300),
                        event.count(),
                        event.eventTime()
                ))
                .limit(20)
                .toList();
    }

    /** AnalysisApplicationService의 isWarningEvent 처리 조건의 충족 여부를 판단한다. */
    private boolean isWarningEvent(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        return "Warning".equalsIgnoreCase(event.type());
    }

    /** AnalysisApplicationService의 isProblemResource 처리 조건의 충족 여부를 판단한다. */
    private boolean isProblemResource(KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        String status = valueOrBlank(resource.status()).toLowerCase();
        if (status.isBlank()) {
            return false;
        }
        return status.contains("pending")
                || status.contains("failed")
                || status.contains("error")
                || status.contains("crash")
                || status.contains("terminating")
                || status.contains("unknown")
                || status.matches("\\d+/\\d+") && !status.startsWith(status.substring(status.indexOf('/') + 1) + "/");
    }

    /** AnalysisApplicationService의 problemTitle 처리에 필요한 업무 로직을 수행한다. */
    private String problemTitle(KubernetesNamespaceDiagnostics.DiagnosticResource resource,
                                List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events) {
        return events.stream()
                .findFirst()
                .map(event -> valueOrBlank(event.reason()) + " on " + resource.resourceType() + "/" + resource.resourceName())
                .orElse(resource.resourceType() + "/" + resource.resourceName() + " 비정상 상태");
    }

    /** AnalysisApplicationService의 problemSeverity 처리에 필요한 업무 로직을 수행한다. */
    private String problemSeverity(KubernetesNamespaceDiagnostics.DiagnosticResource resource,
                                   List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events) {
        String status = valueOrBlank(resource.status()).toLowerCase();
        boolean repeatedCriticalEvent = events.stream().anyMatch(event -> event.count() != null && event.count() >= 5);
        if (status.contains("crash") || status.contains("failed") || status.contains("error") || repeatedCriticalEvent) {
            return "HIGH";
        }
        if (status.contains("pending") || status.contains("unknown") || !events.isEmpty()) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /** AnalysisApplicationService의 rootCauseSummary 처리에 필요한 업무 로직을 수행한다. */
    private String rootCauseSummary(KubernetesNamespaceDiagnostics.DiagnosticResource resource,
                                    List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events) {
        return events.stream()
                .findFirst()
                .map(event -> valueOrBlank(event.reason()) + ": " + truncate(event.message(), 220))
                .orElse("Kubernetes status=" + valueOrBlank(resource.status()) + " 기준으로 비정상 상태가 감지되었습니다.");
    }

    /** AnalysisApplicationService의 recommendedNextAction 처리에 필요한 업무 로직을 수행한다. */
    private String recommendedNextAction(KubernetesNamespaceDiagnostics.DiagnosticResource resource,
                                         List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events) {
        return events.stream()
                .findFirst()
                .map(this::performanceEventRecommendation)
                .orElse(performanceRecommendation(resource));
    }

    /** AnalysisApplicationService의 eventMatchesResource 처리에 필요한 업무 로직을 수행한다. */
    private boolean eventMatchesResource(KubernetesNamespaceDiagnostics.DiagnosticEvent event, String resourceKind, String resourceName) {
        return valueOrBlank(event.involvedName()).equals(resourceName)
                || valueOrBlank(event.involvedKind()).equals(resourceKind) && valueOrBlank(event.involvedName()).equals(resourceName);
    }

    /** AnalysisApplicationService의 isClearlyFixableEvent 처리 조건의 충족 여부를 판단한다. */
    private boolean isClearlyFixableEvent(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String reason = valueOrBlank(event.reason()).toLowerCase();
        String message = valueOrBlank(event.message()).toLowerCase();
        return reason.contains("failedmount")
                && (message.contains("configmap") || message.contains("secret") || message.contains("persistentvolumeclaim")
                || message.contains("pvc") || message.contains("not found"));
    }

    /** AnalysisApplicationService의 requiresVerificationEvent 처리 입력과 현재 상태의 유효성을 검증한다. */
    private boolean requiresVerificationEvent(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String reason = valueOrBlank(event.reason()).toLowerCase();
        return reason.contains("failedscheduling")
                || reason.contains("failedmount")
                || reason.contains("unhealthy")
                || reason.contains("backoff")
                || reason.contains("pull")
                || reason.contains("failed");
    }

    /** AnalysisApplicationService의 fixReadinessForEvent 처리에 필요한 업무 로직을 수행한다. */
    private String fixReadinessForEvent(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        if (isClearlyFixableEvent(event)) {
            return "READY_TO_FIX";
        }
        if (requiresVerificationEvent(event)) {
            return "NEEDS_VERIFICATION";
        }
        return "OBSERVE";
    }

    /** AnalysisApplicationService의 fixReadinessReasonForEvent 처리에 필요한 업무 로직을 수행한다. */
    private String fixReadinessReasonForEvent(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        return switch (fixReadinessForEvent(event)) {
            case "READY_TO_FIX" -> "이벤트 메시지에 구체적인 누락/불일치 대상이 포함되어 있습니다.";
            case "NEEDS_VERIFICATION" -> "관련 리소스 describe, event, log를 통해 원인을 좁혀야 합니다.";
            default -> "반복 여부를 관찰하면서 추가 evidence를 확보하는 단계입니다.";
        };
    }

    /** AnalysisApplicationService의 addTrace 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addTrace(ArrayNode trace, String type, String source, String message) {
        ObjectNode item = trace.addObject();
        item.put("type", valueOrBlank(type));
        item.put("source", valueOrBlank(source));
        item.put("message", valueOrBlank(message));
    }

    /** AnalysisApplicationService의 addCommand 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addCommand(ArrayNode commands, String label, String command, String why) {
        ObjectNode item = commands.addObject();
        item.put("label", valueOrBlank(label));
        item.put("command", valueOrBlank(command));
        item.put("why", valueOrBlank(why));
    }

    /** AnalysisApplicationService의 addReference 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addReference(ArrayNode references, String kind, String name, String reason) {
        boolean exists = false;
        for (JsonNode reference : references) {
            if (valueOrBlank(reference.path("kind").asText()).equals(kind)
                    && valueOrBlank(reference.path("name").asText()).equals(name)) {
                exists = true;
                break;
            }
        }
        if (exists || valueOrBlank(name).isBlank()) {
            return;
        }
        ObjectNode item = references.addObject();
        item.put("kind", valueOrBlank(kind));
        item.put("name", valueOrBlank(name));
        item.put("reason", valueOrBlank(reason));
    }

    /** AnalysisApplicationService의 addReferencesFromEventMessage 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addReferencesFromEventMessage(ArrayNode references, KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String message = valueOrBlank(event.message());
        addReferenceFromRegex(references, message, "ConfigMap", "configmap \"?([A-Za-z0-9_.-]+)\"?", valueOrBlank(event.reason()));
        addReferenceFromRegex(references, message, "Secret", "secret \"?([A-Za-z0-9_.-]+)\"?", valueOrBlank(event.reason()));
        addReferenceFromRegex(references, message, "PersistentVolumeClaim", "(?:persistentvolumeclaim|pvc) \"?([A-Za-z0-9_.-]+)\"?", valueOrBlank(event.reason()));
    }

    /** AnalysisApplicationService의 addReferenceFromRegex 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addReferenceFromRegex(ArrayNode references, String message, String kind, String regex, String reason) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(valueOrBlank(message));
        if (matcher.find()) {
            addReference(references, kind, matcher.group(1), reason);
        }
    }

    /** AnalysisApplicationService의 safeReadTree 처리에 필요한 업무 로직을 수행한다. */
    private JsonNode safeReadTree(String json) {
        if (valueOrBlank(json).isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    /** AnalysisApplicationService의 findResourceForAnalysisNode 처리 결과를 조회해 반환한다. */
    private KubernetesNamespaceDiagnostics.DiagnosticResource findResourceForAnalysisNode(
            ObjectNode node,
            KubernetesNamespaceDiagnostics diagnostics
    ) {
        String resourceKind = valueOrBlank(node.path("resourceKind").asText());
        String resourceName = valueOrBlank(node.path("resourceName").asText());
        if (!resourceKind.isBlank() && !resourceName.isBlank()) {
            return diagnostics.resources().stream()
                    .filter(resource -> resource.resourceType().equals(resourceKind) && resource.resourceName().equals(resourceName))
                    .findFirst()
                    .orElse(null);
        }
        String text = valueOrBlank(node.path("cause").asText()) + " " + valueOrBlank(node.path("title").asText());
        for (KubernetesNamespaceDiagnostics.DiagnosticResource resource : diagnostics.resources()) {
            if (text.contains(resource.resourceType() + "/" + resource.resourceName())
                    || text.contains(resource.resourceName())) {
                return resource;
            }
        }
        return null;
    }

    /** AnalysisApplicationService의 hasResourceKind 처리 조건의 충족 여부를 판단한다. */
    private boolean hasResourceKind(KubernetesNamespaceDiagnostics diagnostics, String resourceKind) {
        return diagnostics.resources().stream().anyMatch(resource -> resourceKind.equals(resource.resourceType()));
    }

    /** AnalysisApplicationService의 summaryContains 처리에 필요한 업무 로직을 수행한다. */
    private boolean summaryContains(KubernetesNamespaceDiagnostics.DiagnosticResource resource, String token) {
        return valueOrBlank(resource.summaryJson()).contains(token);
    }

    /** AnalysisApplicationService의 isPerformanceRelevantResource 처리 조건의 충족 여부를 판단한다. */
    private boolean isPerformanceRelevantResource(KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        String type = valueOrBlank(resource.resourceType());
        String status = valueOrBlank(resource.status()).toLowerCase();
        return switch (type) {
            case "Pod", "Deployment", "ReplicaSet", "StatefulSet", "DaemonSet", "Endpoint", "PersistentVolumeClaim",
                    "HorizontalPodAutoscaler" -> true;
            default -> status.contains("pending")
                    || status.contains("failed")
                    || status.contains("error")
                    || status.contains("unknown");
        };
    }

    /** AnalysisApplicationService의 isPerformanceRelevantEvent 처리 조건의 충족 여부를 판단한다. */
    private boolean isPerformanceRelevantEvent(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String reason = valueOrBlank(event.reason()).toLowerCase();
        return reason.contains("failedscheduling")
                || reason.contains("failedmount")
                || reason.contains("unhealthy")
                || reason.contains("backoff")
                || reason.contains("failed")
                || reason.contains("notready")
                || reason.contains("oom");
    }

    /** AnalysisApplicationService의 addPerformanceBottleneck 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addPerformanceBottleneck(ArrayNode bottlenecks, KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        ObjectNode item = bottlenecks.addObject();
        item.put("resourceKind", resource.resourceType());
        item.put("resourceName", resource.resourceName());
        item.put("signal", valueOrBlank(resource.status()));
        item.put("recommendation", performanceRecommendation(resource));
        item.putArray("evidence").add(truncate(resource.summaryJson(), 300));
    }

    /** AnalysisApplicationService의 addPerformanceEventBottleneck 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void addPerformanceEventBottleneck(ArrayNode bottlenecks, KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        ObjectNode item = bottlenecks.addObject();
        item.put("resourceKind", valueOrBlank(event.involvedKind()));
        item.put("resourceName", valueOrBlank(event.involvedName()));
        item.put("signal", valueOrBlank(event.reason()) + " count=" + (event.count() == null ? 0 : event.count()));
        item.put("recommendation", performanceEventRecommendation(event));
        item.putArray("evidence").add(truncate(event.message(), 300));
    }

    /** AnalysisApplicationService의 performanceRecommendation 처리에 필요한 업무 로직을 수행한다. */
    private String performanceRecommendation(KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        String type = valueOrBlank(resource.resourceType());
        String status = valueOrBlank(resource.status()).toLowerCase();
        if ("Pod".equals(type) && status.contains("pending")) {
            return "Pod 이벤트에서 FailedScheduling/FailedMount 여부를 확인하고 node capacity, PVC, ConfigMap/Secret, image pull 상태를 순서대로 검증하세요.";
        }
        if ("Endpoint".equals(type)) {
            return "Service selector와 Pod readiness를 확인해 트래픽이 실제 ready endpoint로 전달되는지 검증하세요.";
        }
        if ("PersistentVolumeClaim".equals(type)) {
            return "PVC phase, StorageClass, PV binding 상태를 확인하세요.";
        }
        if ("HorizontalPodAutoscaler".equals(type)) {
            return "HPA condition과 metrics API 수집 상태를 확인하세요.";
        }
        if (type.contains("Deployment") || type.contains("StatefulSet") || type.contains("ReplicaSet") || type.contains("DaemonSet")) {
            return "desired/ready replica 차이, rollout status, Pod 이벤트를 확인하세요.";
        }
        return "관련 리소스 describe 결과와 이벤트를 확인해 성능 저하로 이어질 상태 신호인지 검증하세요.";
    }

    /** AnalysisApplicationService의 performanceEventRecommendation 처리에 필요한 업무 로직을 수행한다. */
    private String performanceEventRecommendation(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String reason = valueOrBlank(event.reason()).toLowerCase();
        if (reason.contains("failedscheduling")) {
            return "node allocatable, taint/toleration, affinity, requests/limits, quota 상태를 확인하세요.";
        }
        if (reason.contains("failedmount")) {
            return "ConfigMap/Secret/PVC 이름과 volumeMount 설정, StorageClass/PV binding 상태를 확인하세요.";
        }
        if (reason.contains("unhealthy")) {
            return "readiness/liveness probe path, timeout, initialDelay, 애플리케이션 응답 시간을 확인하세요.";
        }
        if (reason.contains("backoff")) {
            return "컨테이너 로그와 종료 코드를 확인하고 재시작 원인을 먼저 제거하세요.";
        }
        return "반복 이벤트의 involved resource를 describe하고 같은 시점의 Pod 로그를 확인하세요.";
    }

    /** AnalysisApplicationService의 collectedResourceKindCounts 처리의 핵심 작업 흐름을 실행한다. */
    private Map<String, Integer> collectedResourceKindCounts(List<KubernetesResourceSnapshot.CollectedResource> resources) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        resources.forEach(resource -> counts.merge(resource.resourceType(), 1, Integer::sum));
        return counts;
    }

    /** AnalysisApplicationService의 namespaceHotspots 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, String> namespaceHotspots(
            List<KubernetesResourceSnapshot.CollectedResource> resources,
            List<KubernetesEventSnapshot.CollectedEvent> events
    ) {
        Map<String, NamespaceHotspot> hotspots = new LinkedHashMap<>();
        resources.forEach(resource -> {
            String namespace = valueOrCluster(resource.namespace());
            NamespaceHotspot hotspot = hotspots.computeIfAbsent(namespace, ignored -> new NamespaceHotspot());
            hotspot.resources++;
            if (isProblemCollectedResource(resource)) {
                hotspot.problemResources++;
            }
        });
        events.forEach(event -> {
            String namespace = valueOrCluster(event.namespace());
            NamespaceHotspot hotspot = hotspots.computeIfAbsent(namespace, ignored -> new NamespaceHotspot());
            hotspot.events++;
            if (isWarningCollectedEvent(event)) {
                hotspot.warningEvents++;
            }
        });
        return hotspots.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, NamespaceHotspot> entry) -> entry.getValue().score()).reversed())
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue().summary()),
                        LinkedHashMap::putAll);
    }

    /** AnalysisApplicationService의 isWarningCollectedEvent 처리 조건의 충족 여부를 판단한다. */
    private boolean isWarningCollectedEvent(KubernetesEventSnapshot.CollectedEvent event) {
        return "Warning".equalsIgnoreCase(event.type());
    }

    /** AnalysisApplicationService의 isProblemCollectedResource 처리 조건의 충족 여부를 판단한다. */
    private boolean isProblemCollectedResource(KubernetesResourceSnapshot.CollectedResource resource) {
        String status = valueOrBlank(resource.status()).toLowerCase();
        if (status.isBlank()) {
            return false;
        }
        return status.contains("pending")
                || status.contains("failed")
                || status.contains("error")
                || status.contains("crash")
                || status.contains("terminating")
                || status.contains("unknown")
                || status.matches("\\d+/\\d+") && !status.startsWith(status.substring(status.indexOf('/') + 1) + "/");
    }

    /** AnalysisApplicationService의 appendCollectedResource 처리에 필요한 업무 로직을 수행한다. */
    private void appendCollectedResource(StringBuilder context, KubernetesResourceSnapshot.CollectedResource resource) {
        context.append("- namespace=").append(valueOrCluster(resource.namespace()))
                .append(' ').append(resource.resourceType()).append('/')
                .append(resource.resourceName())
                .append(" status=").append(valueOrBlank(resource.status()))
                .append(" summary=").append(valueOrBlank(resource.summaryJson()))
                .append('\n');
    }

    /** AnalysisApplicationService의 appendCollectedEvent 처리에 필요한 업무 로직을 수행한다. */
    private void appendCollectedEvent(StringBuilder context, KubernetesEventSnapshot.CollectedEvent event, int messageLimit) {
        context.append("- namespace=").append(valueOrCluster(event.namespace()))
                .append(' ').append(valueOrBlank(event.type()))
                .append(' ').append(valueOrBlank(event.reason()))
                .append(' ').append(valueOrBlank(event.involvedKind()))
                .append('/').append(valueOrBlank(event.involvedName()))
                .append(" count=").append(event.count())
                .append(" message=").append(truncate(event.message(), messageLimit))
                .append('\n');
    }

    /** AnalysisApplicationService의 valueOrCluster 처리에 필요한 업무 로직을 수행한다. */
    private String valueOrCluster(String namespace) {
        return valueOrBlank(namespace).isBlank() ? "_cluster" : namespace;
    }

    /** AnalysisApplicationService의 normalizeResourceType 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizeResourceType(String resourceType) {
        String value = valueOrBlank(resourceType).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "", "all" -> "";
            case "pod", "pods", "po" -> "Pod";
            case "service", "services", "svc" -> "Service";
            case "configmap", "configmaps", "cm" -> "ConfigMap";
            case "secret", "secrets" -> "Secret";
            case "persistentvolumeclaim", "persistentvolumeclaims", "pvc" -> "PersistentVolumeClaim";
            case "deployment", "deployments", "deploy" -> "Deployment";
            case "replicaset", "replicasets", "rs" -> "ReplicaSet";
            case "statefulset", "statefulsets", "sts" -> "StatefulSet";
            case "daemonset", "daemonsets", "ds" -> "DaemonSet";
            case "job", "jobs" -> "Job";
            case "cronjob", "cronjobs" -> "CronJob";
            case "ingress", "ingresses", "ing" -> "Ingress";
            case "horizontalpodautoscaler", "horizontalpodautoscalers", "hpa" -> "HorizontalPodAutoscaler";
            case "event", "events", "ev" -> "Event";
            default -> resourceType;
        };
    }

    /** AnalysisApplicationService의 normalizeWorkflowStatus 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizeWorkflowStatus(String status) {
        String value = valueOrBlank(status).trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "OPEN", "INVESTIGATING", "ACTION_PENDING", "FIXED", "ACCEPTED" -> value;
            default -> "OPEN";
        };
    }

    /** AnalysisApplicationService의 requireText 처리 입력과 현재 상태의 유효성을 검증한다. */
    private String requireText(String value, String name) {
        String normalized = valueOrBlank(value).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    /** AnalysisApplicationService의 defaultNamespace 처리에 필요한 업무 로직을 수행한다. */
    private String defaultNamespace(String namespace) {
        String value = valueOrBlank(namespace).trim();
        return value.isBlank() ? "default" : value;
    }

    /** AnalysisApplicationService의 requireCluster 처리 입력과 현재 상태의 유효성을 검증한다. */
    private Cluster requireCluster(UUID clusterId) {
        return clusterRepositoryPort.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
    }

    private record IssueReference(String kind, String name, String reason) {
    }

    private record LogInsight(boolean matched, String category, String severity, int priority, String title,
                              String namespace, String podName, String containerName, String signal,
                              String operatorMeaning, String beginnerExplanation, String recommendedNextAction,
                              String verificationCommand, boolean previousLog, List<String> matchedPatterns) {

        /** LogInsight의 none 처리에 필요한 업무 로직을 수행한다. */
        static LogInsight none(KubernetesNamespaceDiagnostics.DiagnosticPodLog log) {
            return new LogInsight(false, "none", "INFO", 1000, "", valueOrBlankStatic(log.namespace()),
                    valueOrBlankStatic(log.podName()), valueOrBlankStatic(log.containerName()), "",
                    "", "", "", "", false, List.of());
        }

        /** LogInsight의 valueOrBlankStatic 처리에 필요한 업무 로직을 수행한다. */
        private static String valueOrBlankStatic(String value) {
            return value == null ? "" : value;
        }
    }

    private final class IssueGroupAccumulator {
        private final String key;
        private String category = "resource-health";
        private String title = "Issue group";
        private String severity = "MEDIUM";
        private String fixReadiness = "NEEDS_VERIFICATION";
        private String rootCause = "";
        private String recommendedNextAction = "";
        private String resourceKind = "";
        private String resourceName = "";
        private String namespace = "";
        private int eventOccurrences;
        private int logSignals;
        private final List<String> affectedResources = new ArrayList<>();
        private final List<String> evidence = new ArrayList<>();
        private final List<IssueReference> relatedReferences = new ArrayList<>();

        /** IssueGroupAccumulator 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
        private IssueGroupAccumulator(String key) {
            this.key = key;
        }

        /** IssueGroupAccumulator의 matches 처리 조건의 충족 여부를 판단한다. */
        private boolean matches(String kind, String name) {
            String targetKind = valueOrBlank(kind);
            String targetName = valueOrBlank(name);
            if (targetName.isBlank()) {
                return false;
            }
            if (targetKind.equals(resourceKind) && targetName.equals(resourceName)) {
                return true;
            }
            return affectedResources.stream().anyMatch(value -> value.equals(targetKind + "/" + targetName));
        }

        /** IssueGroupAccumulator의 addEvent 처리에 필요한 데이터를 생성하거나 저장한다. */
        private void addEvent(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
            int count = event.count() == null ? 1 : Math.max(1, event.count());
            eventOccurrences += count;
            if (namespace.isBlank()) {
                namespace = valueOrBlank(event.namespace());
            }
            if (resourceKind.isBlank()) {
                resourceKind = valueOrBlank(event.involvedKind());
            }
            if (resourceName.isBlank()) {
                resourceName = valueOrBlank(event.involvedName());
            }
            addAffectedResource(event.involvedKind(), event.involvedName());
            addEvidence(valueOrBlank(event.reason()) + " count=" + count + " "
                    + valueOrBlank(event.involvedKind()) + "/" + valueOrBlank(event.involvedName())
                    + " " + truncate(event.message(), 180));
            addReferenceFromMessage(event.message(), valueOrBlank(event.reason()));
        }

        /** IssueGroupAccumulator의 addResource 처리에 필요한 데이터를 생성하거나 저장한다. */
        private void addResource(KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
            if (namespace.isBlank()) {
                namespace = valueOrBlank(resource.namespace());
            }
            if (resourceKind.isBlank()) {
                resourceKind = valueOrBlank(resource.resourceType());
            }
            if (resourceName.isBlank()) {
                resourceName = valueOrBlank(resource.resourceName());
            }
            addAffectedResource(resource.resourceType(), resource.resourceName());
            addEvidence(valueOrBlank(resource.resourceType()) + "/" + valueOrBlank(resource.resourceName())
                    + " status=" + valueOrBlank(resource.status()) + " "
                    + truncate(resource.summaryJson(), 180));
        }

        /** IssueGroupAccumulator의 addLog 처리에 필요한 데이터를 생성하거나 저장한다. */
        private void addLog(KubernetesNamespaceDiagnostics.DiagnosticPodLog log) {
            LogInsight insight = logInsight(log);
            logSignals++;
            if (namespace.isBlank()) {
                namespace = valueOrBlank(log.namespace());
            }
            if (resourceKind.isBlank()) {
                resourceKind = "Pod";
            }
            if (resourceName.isBlank()) {
                resourceName = valueOrBlank(log.podName());
            }
            addAffectedResource("Pod", log.podName());
            if (insight.matched()) {
                addEvidence("Pod/" + valueOrBlank(log.podName()) + " " + insight.category()
                        + " severity=" + insight.severity() + " log=" + truncate(insight.signal(), 180));
            } else {
                addEvidence("Pod/" + valueOrBlank(log.podName()) + " log=" + truncate(firstLogSignal(log.log()), 180));
            }
        }

        /** IssueGroupAccumulator의 addAffectedResource 처리에 필요한 데이터를 생성하거나 저장한다. */
        private void addAffectedResource(String kind, String name) {
            String resource = valueOrBlank(kind) + "/" + valueOrBlank(name);
            if (!"/".equals(resource) && affectedResources.stream().noneMatch(resource::equals)) {
                affectedResources.add(resource);
            }
        }

        /** IssueGroupAccumulator의 addEvidence 처리에 필요한 데이터를 생성하거나 저장한다. */
        private void addEvidence(String value) {
            String normalized = truncate(value, 260);
            if (!normalized.isBlank() && evidence.stream().noneMatch(normalized::equals)) {
                evidence.add(normalized);
            }
        }

        /** IssueGroupAccumulator의 addReferenceFromMessage 처리에 필요한 데이터를 생성하거나 저장한다. */
        private void addReferenceFromMessage(String message, String reason) {
            String configMap = extractReferenceName(message, "configmap \"?([A-Za-z0-9_.-]+)\"?");
            if (!configMap.isBlank()) {
                addReference("ConfigMap", configMap, reason);
            }
            String secret = extractReferenceName(message, "secret \"?([A-Za-z0-9_.-]+)\"?");
            if (!secret.isBlank()) {
                addReference("Secret", secret, reason);
            }
            String pvc = extractReferenceName(message, "(?:persistentvolumeclaim|pvc) \"?([A-Za-z0-9_.-]+)\"?");
            if (!pvc.isBlank()) {
                addReference("PersistentVolumeClaim", pvc, reason);
            }
        }

        /** IssueGroupAccumulator의 addReference 처리에 필요한 데이터를 생성하거나 저장한다. */
        private void addReference(String kind, String name, String reason) {
            String normalizedKind = valueOrBlank(kind);
            String normalizedName = valueOrBlank(name);
            if (normalizedKind.isBlank() || normalizedName.isBlank()) {
                return;
            }
            boolean exists = relatedReferences.stream()
                    .anyMatch(reference -> reference.kind().equals(normalizedKind) && reference.name().equals(normalizedName));
            if (!exists) {
                relatedReferences.add(new IssueReference(normalizedKind, normalizedName, valueOrBlank(reason)));
            }
        }

        /** IssueGroupAccumulator의 score 처리에 필요한 업무 로직을 수행한다. */
        private int score() {
            int severityScore = switch (valueOrBlank(severity).toUpperCase()) {
                case "CRITICAL" -> 400;
                case "HIGH" -> 300;
                case "MEDIUM" -> 200;
                case "LOW" -> 100;
                default -> 50;
            };
            int readinessScore = "READY_TO_FIX".equals(valueOrBlank(fixReadiness).toUpperCase()) ? 40 : 0;
            return severityScore
                    + readinessScore
                    + Math.min(eventOccurrences, 120)
                    + affectedResources.size() * 8
                    + logSignals * 6
                    + relatedReferences.size() * 12;
        }
    }

    private static final class NamespaceHotspot {
        private int resources;
        private int problemResources;
        private int events;
        private int warningEvents;

        /** NamespaceHotspot의 score 처리에 필요한 업무 로직을 수행한다. */
        private int score() {
            return problemResources * 10 + warningEvents * 6 + events;
        }

        /** NamespaceHotspot의 summary 처리에 필요한 업무 로직을 수행한다. */
        private String summary() {
            return "resources=" + resources
                    + " problemResources=" + problemResources
                    + " events=" + events
                    + " warningEvents=" + warningEvents;
        }
    }

    private static final class EventNoiseAccumulator {
        private final String reason;
        private final String targetKind;
        private final String targetName;
        private final String message;
        private int eventCount;
        private int occurrenceCount;

        /** EventNoiseAccumulator 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
        private EventNoiseAccumulator(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
            this.reason = event.reason() == null ? "" : event.reason();
            this.targetKind = event.involvedKind() == null ? "" : event.involvedKind();
            this.targetName = event.involvedName() == null ? "" : event.involvedName();
            this.message = event.message() == null ? "" : event.message();
        }

        /** EventNoiseAccumulator의 add 처리에 필요한 데이터를 생성하거나 저장한다. */
        private void add(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
            eventCount++;
            occurrenceCount += event.count() == null ? 1 : Math.max(1, event.count());
        }

        /** EventNoiseAccumulator의 occurrenceCount 처리에 필요한 업무 로직을 수행한다. */
        private int occurrenceCount() {
            return occurrenceCount;
        }

        /** EventNoiseAccumulator의 priority 처리에 필요한 업무 로직을 수행한다. */
        private String priority() {
            String value = (reason + " " + message).toLowerCase();
            if (occurrenceCount >= 10 || value.contains("failedmount") || value.contains("failedscheduling")
                    || value.contains("backoff") || value.contains("unhealthy")) {
                return "HIGH";
            }
            if (occurrenceCount >= 3 || eventCount >= 2) {
                return "MEDIUM";
            }
            return "LOW";
        }

        /** EventNoiseAccumulator의 priorityWeight 처리에 필요한 업무 로직을 수행한다. */
        private int priorityWeight() {
            return switch (priority()) {
                case "HIGH" -> 300;
                case "MEDIUM" -> 200;
                default -> 100;
            } + occurrenceCount;
        }
    }

    /** AnalysisApplicationService의 audit 처리에 필요한 업무 로직을 수행한다. */
    private void audit(String action, UUID analysisId, String actor, String requestId) {
        auditLogRepositoryPort.save(AuditLog.create(action, "ANALYSIS", analysisId.toString(), actor, requestId));
    }

    /** AnalysisApplicationService의 mergeCommandExecutionIntoAnalysis 처리에 필요한 업무 로직을 수행한다. */
    private void mergeCommandExecutionIntoAnalysis(AnalysisSession analysis, AnalysisCommandExecution execution,
                                                   AnalysisCommandParser.ParsedCommand parsed) {
        try {
            commandEvidenceMerger.merge(analysis.resultJson(), execution, parsed).ifPresent(resultJson ->
                analysisSessionRepositoryPort.save(analysis.withResult(
                        resultJson,
                        analysis.resultSummary(),
                        analysis.schemaVersion()
                )));
        } catch (RuntimeException ignored) {
            // Command execution must not fail just because analysis enrichment persistence is unavailable.
        }
    }

    /** AnalysisApplicationService의 arrayField 처리에 필요한 업무 로직을 수행한다. */
    private ArrayNode arrayField(ObjectNode parent, String fieldName) {
        JsonNode existing = parent.get(fieldName);
        if (existing instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode created = objectMapper.createArrayNode();
        parent.set(fieldName, created);
        return created;
    }

    /** AnalysisApplicationService의 persistCommandExecution 처리에 필요한 데이터를 생성하거나 저장한다. */
    private AnalysisCommandExecution persistCommandExecution(AnalysisCommandExecution execution) {
        try {
            return analysisCommandExecutionRepositoryPort.save(execution);
        } catch (RuntimeException exception) {
            return execution;
        }
    }

    /** AnalysisApplicationService의 auditCommand 처리에 필요한 업무 로직을 수행한다. */
    private void auditCommand(String action, UUID analysisId, String actor, String requestId) {
        try {
            audit(action, analysisId, actor, requestId);
        } catch (RuntimeException ignored) {
            // Command execution must still return its result even if audit persistence is temporarily unavailable.
        }
    }

    /** AnalysisApplicationService의 connectionCredential 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesConnectionCredential connectionCredential(UUID clusterId) {
        EncryptedClusterCredential credential = clusterCredentialRepositoryPort.findByClusterId(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found: " + clusterId));
        String plaintextPayload = secretCryptoPort.decrypt(new EncryptedSecret(
                credential.encryptedPayload(),
                credential.keyId(),
                credential.algorithm(),
                credential.nonce()
        ));
        return new KubernetesConnectionCredential(credential.credentialType(), plaintextPayload);
    }

    /** AnalysisApplicationService의 truncate 처리에 필요한 업무 로직을 수행한다. */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /** AnalysisApplicationService의 valueOrBlank 처리에 필요한 업무 로직을 수행한다. */
    private String valueOrBlank(String value) {
        return value == null ? "" : value;
    }

    /** AnalysisApplicationService의 withAnalysisComparison 처리에 필요한 업무 로직을 수행한다. */
    private String withAnalysisComparison(String resultJson, UUID clusterId, UUID applicationId, String namespace) {
        return analysisComparisonService.withComparison(resultJson,
                analysisSessionRepositoryPort.findLatestSucceededByScope(clusterId, applicationId, namespace));
    }

    /** AnalysisApplicationService의 resultSummary 처리에 필요한 업무 로직을 수행한다. */
    private String resultSummary(String resultJson) {
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            String severity = root.path("severity").asText("");
            String summary = root.path("summary").asText("AI analysis completed");
            if (!severity.isBlank()) {
                return severity + " - " + summary;
            }
            return summary;
        } catch (Exception exception) {
            return "AI analysis completed";
        }
    }

    /** AnalysisApplicationService의 schemaVersion 처리에 필요한 업무 로직을 수행한다. */
    private String schemaVersion(String resultJson) {
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            String schemaVersion = root.path("schemaVersion").asText("");
            return schemaVersion.isBlank() ? "analysis-result.v1" : schemaVersion;
        } catch (Exception exception) {
            return "analysis-result.v1";
        }
    }

    /** AnalysisApplicationService의 failedAnalysisJson 처리에 필요한 업무 로직을 수행한다. */
    private String failedAnalysisJson(RuntimeException exception) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", "analysis-result.v1");
        root.put("summary", "AI analysis failed before a result could be produced.");
        root.put("severity", "HIGH");
        root.put("confidence", 0.0);
        root.put("riskScore", 0);
        root.putArray("findings");
        root.putArray("rootCauses");
        root.putArray("logAnalysis");
        root.set("performance", objectMapper.createObjectNode()
                .put("summary", "Performance analysis was not completed.")
                .set("bottlenecks", objectMapper.createArrayNode()));
        ((ObjectNode) root.get("performance")).putArray("improvements");
        root.set("scaling", objectMapper.createObjectNode()
                .put("summary", "Scaling analysis was not completed.")
                .set("scaleUpCandidates", objectMapper.createArrayNode()));
        ((ObjectNode) root.get("scaling")).putArray("hpaRecommendations");
        ((ObjectNode) root.get("scaling")).putArray("capacityNotes");
        ObjectNode riskForecast = root.putObject("riskForecast");
        riskForecast.put("summary", "Risk forecast was not completed.");
        riskForecast.putArray("predictions");
        root.putArray("changeTimeline");
        root.putArray("runbookActions");
        ObjectNode recommendation = root.putArray("recommendations").addObject();
        recommendation.put("priority", "P1");
        recommendation.put("action", "Review the async job error and retry analysis after the dependency is healthy.");
        recommendation.put("reason", truncate(exception.getMessage(), 500));
        recommendation.putArray("commands");
        ObjectNode operationsGuide = root.putObject("operationsGuide");
        operationsGuide.put("summary", "The analysis job failed. Check backend logs and dependency health.");
        operationsGuide.putArray("shortTerm").add("Check Ollama and Kubernetes API connectivity.");
        operationsGuide.putArray("mediumTerm").add("Retry with a narrower namespace scope if cluster-level analysis repeatedly fails.");
        operationsGuide.putArray("questionsForOperator").add("Did the failure happen during Kubernetes collection or AI response generation?");
        ObjectNode nextAction = root.putArray("nextActions").addObject();
        nextAction.put("priority", "P1");
        nextAction.put("action", "Open the job error detail and retry analysis.");
        nextAction.put("ownerHint", "platform");
        nextAction.put("verification", truncate(exception.getMessage(), 500));
        root.putArray("verificationCommands")
                .add("kubectl get ns")
                .add("kubectl get events -A --sort-by=.lastTimestamp");
        ObjectNode evidence = root.putObject("evidence");
        evidence.putArray("resources");
        evidence.putArray("events");
        evidence.putArray("logs").add(truncate(exception.getMessage(), 1000));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception jsonException) {
            return "{\"schemaVersion\":\"analysis-result.v1\",\"summary\":\"AI analysis failed\",\"severity\":\"HIGH\",\"findings\":[],\"rootCauses\":[],\"logAnalysis\":[],\"recommendations\":[],\"changeTimeline\":[],\"runbookActions\":[],\"nextActions\":[],\"verificationCommands\":[],\"evidence\":{\"resources\":[],\"events\":[],\"logs\":[]}}";
        }
    }

    /** AnalysisApplicationService의 errorCode 처리에 필요한 업무 로직을 수행한다. */
    private String errorCode(RuntimeException exception) {
        String name = exception.getClass().getSimpleName();
        if (name == null || name.isBlank()) {
            return "AI_ANALYSIS_FAILED";
        }
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    /** AnalysisApplicationService의 normalizedNamespace 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizedNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return null;
        }
        return namespace.trim();
    }

    private record ClusterAnalysisContext(
            String context,
            List<KubernetesResourceSnapshot.CollectedResource> resources,
            List<KubernetesEventSnapshot.CollectedEvent> events
    ) {
    }
}
