package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AnalysisSessionRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class CommandSourceAnalysisValidator {
    private final AnalysisSessionRepositoryPort analyses;

    /** CommandSourceAnalysisValidator 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public CommandSourceAnalysisValidator(AnalysisSessionRepositoryPort analyses) {
        this.analyses = analyses;
    }

    /** CommandSourceAnalysisValidator의 validate 처리 입력과 현재 상태의 유효성을 검증한다. */
    public void validate(UUID clusterId, UUID analysisId) {
        if (analysisId == null) return;
        var analysis = analyses.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Source analysis not found: " + analysisId));
        if (!analysis.clusterId().equals(clusterId)) {
            throw new NoSuchElementException("Source analysis not found: " + analysisId);
        }
    }
}
