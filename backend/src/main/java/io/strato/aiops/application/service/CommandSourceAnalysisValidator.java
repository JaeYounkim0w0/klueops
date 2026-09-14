package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AnalysisSessionRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class CommandSourceAnalysisValidator {
    private final AnalysisSessionRepositoryPort analyses;

    public CommandSourceAnalysisValidator(AnalysisSessionRepositoryPort analyses) {
        this.analyses = analyses;
    }

    public void validate(UUID clusterId, UUID analysisId) {
        if (analysisId == null) return;
        var analysis = analyses.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Source analysis not found: " + analysisId));
        if (!analysis.clusterId().equals(clusterId)) {
            throw new NoSuchElementException("Source analysis not found: " + analysisId);
        }
    }
}
