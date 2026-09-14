package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface AnalysisExecutorPort {

    void submitAnalysis(UUID asyncJobId);
}
