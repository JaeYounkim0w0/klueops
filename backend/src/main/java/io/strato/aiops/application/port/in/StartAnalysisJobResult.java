package io.strato.aiops.application.port.in;

import java.util.UUID;

public record StartAnalysisJobResult(
        UUID jobId,
        UUID analysisId
) {
}
