package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.analysis.AnalysisSession;
import io.strato.aiops.domain.analysis.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "AI analysis session")
public record AnalysisResponse(
        UUID id,
        UUID asyncJobId,
        UUID clusterId,
        UUID applicationId,
        String namespace,
        AnalysisStatus status,
        String aiProvider,
        String aiModel,
        String promptVersion,
        String schemaVersion,
        String locale,
        String resultSummary,
        String resultJson,
        String createdBy,
        Instant createdAt
) {
    public static AnalysisResponse from(AnalysisSession analysisSession) {
        return new AnalysisResponse(analysisSession.id(), analysisSession.asyncJobId(), analysisSession.clusterId(),
                analysisSession.applicationId(), analysisSession.namespace(), analysisSession.status(),
                analysisSession.aiProvider(), analysisSession.aiModel(), analysisSession.promptVersion(),
                analysisSession.schemaVersion(), analysisSession.locale(), analysisSession.resultSummary(), analysisSession.resultJson(),
                analysisSession.createdBy(), analysisSession.createdAt());
    }
}
