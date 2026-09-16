package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface AnalysisExecutorPort {

    /** AnalysisExecutorPort의 submitAnalysis 처리 계약을 정의한다. */
    void submitAnalysis(UUID asyncJobId);
}
