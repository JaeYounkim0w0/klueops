package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.AnalysisSession;

import java.util.List;
import java.util.UUID;

public interface AnalysisUseCase {

    /** AnalysisUseCase의 analyzeApplication 처리의 핵심 작업 흐름을 실행한다. */
    AnalysisSession analyzeApplication(AnalyzeApplicationCommand command, String actor, String requestId);

    /** AnalysisUseCase의 analyzeNamespace 처리의 핵심 작업 흐름을 실행한다. */
    AnalysisSession analyzeNamespace(AnalyzeNamespaceCommand command, String actor, String requestId);

    /** AnalysisUseCase의 analyzeCluster 처리의 핵심 작업 흐름을 실행한다. */
    AnalysisSession analyzeCluster(AnalyzeClusterCommand command, String actor, String requestId);

    /** AnalysisUseCase의 startApplicationAnalysis 처리 계약을 정의한다. */
    StartAnalysisJobResult startApplicationAnalysis(AnalyzeApplicationCommand command, String actor, String requestId);

    /** AnalysisUseCase의 startNamespaceAnalysis 처리 계약을 정의한다. */
    StartAnalysisJobResult startNamespaceAnalysis(AnalyzeNamespaceCommand command, String actor, String requestId);

    /** AnalysisUseCase의 startClusterAnalysis 처리 계약을 정의한다. */
    StartAnalysisJobResult startClusterAnalysis(AnalyzeClusterCommand command, String actor, String requestId);

    /** AnalysisUseCase의 retryAnalysis 처리 계약을 정의한다. */
    StartAnalysisJobResult retryAnalysis(UUID analysisId, String actor, String requestId);

    /** AnalysisUseCase의 getAnalysisByJobId 처리 결과를 조회해 반환한다. */
    AnalysisSession getAnalysisByJobId(UUID jobId);

    /** AnalysisUseCase의 getAnalysis 처리 결과를 조회해 반환한다. */
    AnalysisSession getAnalysis(UUID analysisId);

    /** AnalysisUseCase의 listHistory 처리 결과를 조회해 반환한다. */
    List<AnalysisSession> listHistory(UUID clusterId, UUID applicationId, String namespace);

    /** AnalysisUseCase의 deleteAnalysis 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteAnalysis(UUID analysisId, String actor, String requestId);
}
