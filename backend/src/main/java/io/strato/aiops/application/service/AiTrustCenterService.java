package io.strato.aiops.application.service;

import io.strato.aiops.domain.operations.OperationsEvolutionModels.AiReleaseGate;
import io.strato.aiops.domain.operations.OperationsModels.AiCalibrationSummary;
import io.strato.aiops.domain.operations.OperationsModels.AiQualitySummary;
import io.strato.aiops.domain.operations.OperationsModels.RegressionRun;
import io.strato.aiops.domain.operations.OperationsReadinessModels.AnalysisBenchmark;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AiTrustCenterService {

    private final OperationsControlPlaneService operationsService;
    private final OperationsReadinessService readinessService;
    private final AnalysisRegressionService regressionService;
    private final OperationsEvolutionService evolutionService;

    public AiTrustCenterService(OperationsControlPlaneService operationsService,
                                OperationsReadinessService readinessService,
                                AnalysisRegressionService regressionService,
                                OperationsEvolutionService evolutionService) {
        this.operationsService = operationsService;
        this.readinessService = readinessService;
        this.regressionService = regressionService;
        this.evolutionService = evolutionService;
    }

    public Snapshot snapshot() {
        AiQualitySummary quality = operationsService.getAiQuality();
        AiCalibrationSummary calibration = operationsService.getAiCalibration();
        AnalysisBenchmark benchmark = readinessService.latestBenchmark();
        List<RegressionRun> regressions = regressionService.list(5);
        List<AiReleaseGate> gates = evolutionService.releaseGates(5);
        String state = state(quality, calibration, benchmark, regressions, gates);
        return new Snapshot("ai-trust.v1", Instant.now(), state, quality, calibration, benchmark,
                regressionService.corpusProfile(), regressionService.latestContractEvaluation(),
                List.copyOf(regressions), List.copyOf(gates));
    }

    private String state(AiQualitySummary quality, AiCalibrationSummary calibration, AnalysisBenchmark benchmark,
                         List<RegressionRun> regressions, List<AiReleaseGate> gates) {
        if (quality.dangerousSuggestionCount() > 0
                || (!gates.isEmpty() && "BLOCKED".equals(gates.get(0).state()))
                || (!regressions.isEmpty() && "FAILED".equals(regressions.get(0).status()))) {
            return "BLOCKED";
        }
        if (benchmark == null || calibration.groundTruthCount() < 3) return "NEEDS_EVIDENCE";
        return "TRUSTED";
    }

    public record Snapshot(
            String schemaVersion,
            Instant generatedAt,
            String state,
            AiQualitySummary quality,
            AiCalibrationSummary calibration,
            AnalysisBenchmark latestBenchmark,
            AnalysisRegressionService.CorpusProfile corpus,
            AiEvaluationMetrics.Summary contractEvaluation,
            List<RegressionRun> recentRegressions,
            List<AiReleaseGate> recentReleaseGates
    ) {
    }
}
