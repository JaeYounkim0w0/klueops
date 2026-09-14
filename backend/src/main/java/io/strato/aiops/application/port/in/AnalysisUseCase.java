package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.AnalysisSession;

import java.util.List;
import java.util.UUID;

public interface AnalysisUseCase {

    AnalysisSession analyzeApplication(AnalyzeApplicationCommand command, String actor, String requestId);

    AnalysisSession analyzeNamespace(AnalyzeNamespaceCommand command, String actor, String requestId);

    AnalysisSession analyzeCluster(AnalyzeClusterCommand command, String actor, String requestId);

    StartAnalysisJobResult startApplicationAnalysis(AnalyzeApplicationCommand command, String actor, String requestId);

    StartAnalysisJobResult startNamespaceAnalysis(AnalyzeNamespaceCommand command, String actor, String requestId);

    StartAnalysisJobResult startClusterAnalysis(AnalyzeClusterCommand command, String actor, String requestId);

    StartAnalysisJobResult retryAnalysis(UUID analysisId, String actor, String requestId);

    AnalysisSession getAnalysisByJobId(UUID jobId);

    AnalysisSession getAnalysis(UUID analysisId);

    List<AnalysisSession> listHistory(UUID clusterId, UUID applicationId, String namespace);

    void deleteAnalysis(UUID analysisId, String actor, String requestId);
}
