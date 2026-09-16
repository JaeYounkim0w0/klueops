package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AnalysisSessionRepositoryPort;
import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisSession;
import io.strato.aiops.domain.analysis.AnalysisStatus;
import io.strato.aiops.domain.operations.OperationsModels.AiCalibrationSummary;
import io.strato.aiops.domain.operations.OperationsModels.AiQualitySummary;
import io.strato.aiops.domain.operations.OperationsModels.AnalysisFeedback;
import io.strato.aiops.domain.operations.OperationsModels.GroundTruthSample;
import io.strato.aiops.domain.operations.OperationsModels.ModelQualityProfile;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class AiQualityQueryService {

    private static final int SAMPLE_LIMIT = 500;
    private static final int GROUND_TRUTH_LIMIT = 20;

    private final OperationsRepositoryPort operationsRepository;
    private final AnalysisSessionRepositoryPort analysisRepository;

    /** AiQualityQueryService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AiQualityQueryService(OperationsRepositoryPort operationsRepository,
                                 AnalysisSessionRepositoryPort analysisRepository) {
        this.operationsRepository = operationsRepository;
        this.analysisRepository = analysisRepository;
    }

    /** AiQualityQueryService의 getQuality 처리 결과를 조회해 반환한다. */
    public AiQualitySummary getQuality() {
        List<AnalysisFeedback> feedback = operationsRepository.findAnalysisFeedback(SAMPLE_LIMIT);
        List<AnalysisSession> analyses = analysisRepository.findRecent(null, null, null, SAMPLE_LIMIT);
        int correct = count(feedback, item -> "CORRECT".equals(item.accuracy()));
        int partial = count(feedback, item -> "PARTIAL".equals(item.accuracy()));
        int incorrect = count(feedback, item -> "INCORRECT".equals(item.accuracy()));
        int improved = count(feedback, item -> Set.of("RESOLVED", "IMPROVED").contains(item.outcome()));
        int dangerous = count(feedback, AnalysisFeedback::dangerousSuggestion);
        int succeeded = count(analyses, item -> item.status() == AnalysisStatus.SUCCEEDED);
        int failed = count(analyses, item -> item.status() == AnalysisStatus.FAILED);
        return new AiQualitySummary(feedback.size(), correct, partial, incorrect, improved, dangerous,
                succeeded, failed, percentage(succeeded, succeeded + failed),
                percentage(correct, correct + partial + incorrect));
    }

    /** AiQualityQueryService의 getCalibration 처리 결과를 조회해 반환한다. */
    public AiCalibrationSummary getCalibration() {
        List<AnalysisFeedback> feedback = operationsRepository.findAnalysisFeedback(SAMPLE_LIMIT);
        Set<UUID> analysisIds = feedback.stream().map(AnalysisFeedback::analysisId).collect(Collectors.toSet());
        Map<UUID, AnalysisSession> sessions = analysisRepository.findByIds(analysisIds).stream()
                .collect(Collectors.toMap(AnalysisSession::id, session -> session));
        List<AnalysisFeedback> groundTruth = feedback.stream()
                .filter(item -> hasText(item.actualRootCause()) || hasText(item.actualResolution()))
                .toList();
        Map<String, List<AnalysisFeedback>> grouped = feedback.stream().collect(Collectors.groupingBy(item -> {
            AnalysisSession session = sessions.get(item.analysisId());
            return session == null ? "unknown|unknown" : session.aiModel() + "|" + session.promptVersion();
        }));
        List<ModelQualityProfile> profiles = grouped.entrySet().stream().map(entry -> {
            String[] key = entry.getKey().split("\\|", 2);
            List<AnalysisFeedback> values = entry.getValue();
            int accurate = count(values, item -> Set.of("CORRECT", "PARTIAL").contains(item.accuracy()));
            int resolved = count(values, item -> Set.of("RESOLVED", "IMPROVED").contains(item.outcome()));
            return new ModelQualityProfile(key[0], key[1], values.size(), percentage(accurate, values.size()),
                    percentage(resolved, values.size()), count(values, AnalysisFeedback::dangerousSuggestion));
        }).sorted(Comparator.comparingInt(ModelQualityProfile::feedbackCount).reversed()).toList();
        List<GroundTruthSample> samples = groundTruth.stream().limit(GROUND_TRUTH_LIMIT).map(item -> {
            AnalysisSession session = sessions.get(item.analysisId());
            return new GroundTruthSample(item.analysisId(), session == null ? "unknown" : session.aiModel(),
                    session == null ? "unknown" : session.promptVersion(), item.accuracy(), item.actualRootCause(),
                    item.actualResolution(), item.validatedResourceKind(), item.validatedResourceName(), item.updatedAt());
        }).toList();
        int accurate = count(feedback, item -> Set.of("CORRECT", "PARTIAL").contains(item.accuracy()));
        int resolved = count(feedback, item -> Set.of("RESOLVED", "IMPROVED").contains(item.outcome()));
        return new AiCalibrationSummary(feedback.size(), groundTruth.size(),
                percentage(groundTruth.size(), feedback.size()), percentage(accurate, feedback.size()),
                percentage(resolved, feedback.size()), count(feedback, AnalysisFeedback::dangerousSuggestion),
                profiles, samples);
    }

    /** AiQualityQueryService의 count 처리에 필요한 업무 로직을 수행한다. */
    private static <T> int count(List<T> values, Predicate<T> predicate) {
        return (int) values.stream().filter(predicate).count();
    }

    /** AiQualityQueryService의 percentage 처리에 필요한 업무 로직을 수행한다. */
    private static double percentage(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : Math.round((numerator * 1000.0) / denominator) / 10.0;
    }

    /** AiQualityQueryService의 hasText 처리 조건의 충족 여부를 판단한다. */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
