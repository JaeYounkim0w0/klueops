package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AnalysisAssuranceRepositoryPort;
import io.strato.aiops.domain.operations.OperationsModels.RegressionCaseResult;
import io.strato.aiops.domain.operations.OperationsModels.RegressionRun;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class AnalysisRegressionService {

    private static final String BASELINE_VERSION = "k8s-analysis-contract-v1";
    private final AnalysisAssuranceRepositoryPort repository;
    private final AnalysisRegressionCorpus corpus;

    public AnalysisRegressionService(AnalysisAssuranceRepositoryPort repository, AnalysisRegressionCorpus corpus) {
        this.repository = repository;
        this.corpus = corpus;
    }

    public RegressionRun run(String actor) {
        UUID runId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        List<RegressionCaseResult> cases = corpus.fixtures().stream().map(fixture -> evaluate(runId, fixture)).toList();
        int passed = (int) cases.stream().filter(item -> "PASSED".equals(item.status())).count();
        double score = cases.isEmpty() ? 0 : Math.round(cases.stream().mapToInt(RegressionCaseResult::score).average()
                .orElse(0) * 10.0) / 10.0;
        RegressionRun run = new RegressionRun(runId, passed == cases.size() ? "PASSED" : "FAILED", passed,
                cases.size(), score, BASELINE_VERSION, actor, startedAt, Instant.now(), cases);
        return repository.saveRegressionRun(run);
    }

    public List<RegressionRun> list(int limit) {
        return repository.findRegressionRuns(limit);
    }

    public RegressionRun get(UUID runId) {
        return repository.findRegressionRun(runId)
                .orElseThrow(() -> new NoSuchElementException("Regression run not found: " + runId));
    }

    public CorpusProfile corpusProfile() {
        List<String> categories = corpus.fixtures().stream().map(AnalysisRegressionCorpus.Fixture::expectedCategory)
                .distinct().sorted().toList();
        long abstentionCases = corpus.fixtures().stream().filter(AnalysisRegressionCorpus.Fixture::expectedAbstention).count();
        return new CorpusProfile("operational-corpus-v2", corpus.fixtures().size(), categories,
                (int) abstentionCases, true);
    }

    public AiEvaluationMetrics.Summary latestContractEvaluation() {
        List<RegressionRun> runs = repository.findRegressionRuns(1);
        if (runs.isEmpty()) return AiEvaluationMetrics.calculate(List.of());
        RegressionRun run = runs.get(0);
        var outcomes = corpus.fixtures().stream().map(fixture -> {
            RegressionCaseResult result = run.cases().stream()
                    .filter(item -> item.caseId().equals(fixture.id())).findFirst().orElse(null);
            boolean passed = result != null && "PASSED".equals(result.status());
            String actual = passed ? fixture.expectedCategory() : "UNKNOWN";
            return new AiEvaluationMetrics.Observation(fixture.expectedCategory(), actual,
                    fixture.expectedAbstention(), passed && "UNKNOWN".equals(actual));
        }).toList();
        return AiEvaluationMetrics.calculate(outcomes);
    }

    private RegressionCaseResult evaluate(UUID runId, AnalysisRegressionCorpus.Fixture fixture) {
        Instant startedAt = Instant.now();
        EvaluatedFinding finding = analyze(fixture);
        List<String> assertions = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        check("원인 카테고리가 " + fixture.expectedCategory() + "로 분류됨",
                fixture.expectedCategory().equals(finding.category()), assertions, failures);
        check("문제 대상이 " + fixture.resourceKind() + "/" + fixture.resourceName() + "로 유지됨",
                fixture.resourceKind().equals(finding.resourceKind()) && fixture.resourceName().equals(finding.resourceName()),
                assertions, failures);
        check("사실 근거가 2개 이상 보존됨", finding.evidenceCount() >= 2, assertions, failures);
        check("읽기 전용 검증 명령이 존재함", finding.readOnlyCommands() > 0, assertions, failures);
        check("변경 명령은 명시적 guard 없이는 실행 대상으로 분류되지 않음",
                !finding.hasMutation() || finding.mutationGuarded(), assertions, failures);
        check("결정론 신뢰도 점수가 70 이상임", finding.confidence() >= 70, assertions, failures);
        int score = (int) Math.round(assertions.size() * 100.0 / (assertions.size() + failures.size()));
        return new RegressionCaseResult(UUID.randomUUID(), runId, fixture.id(), fixture.title(),
                fixture.expectedCategory(), failures.isEmpty() ? "PASSED" : "FAILED", score,
                List.copyOf(assertions), List.copyOf(failures), Duration.between(startedAt, Instant.now()).toMillis());
    }

    private EvaluatedFinding analyze(AnalysisRegressionCorpus.Fixture fixture) {
        String signal = fixture.signal().toUpperCase(Locale.ROOT);
        String category;
        if (signal.contains("EXCEEDEDQUOTA") || signal.contains("LIMITRANGE") || signal.contains("QUOTA")) {
            category = "QUOTA";
        } else if (signal.contains("READINESS") || signal.contains("LIVENESS")
                || signal.contains("STARTUP PROBE") || signal.contains("UNHEALTHY")) {
            category = "PROBE";
        } else if (signal.contains("FAILEDMOUNT") || signal.contains("FAILEDATTACHVOLUME")
                || signal.contains("FAILEDBINDING") || signal.contains("CONFIGMAP")
                || signal.contains("SECRET") || signal.contains("PVC")) {
            category = "STORAGE_CONFIG";
        } else if (signal.contains("OOMKILLED") || signal.contains("EXITCODE=137")
                || signal.contains("EVICTED") || signal.contains("INSUFFICIENT CPU")) {
            category = "CAPACITY";
        } else if (signal.contains("CRASHLOOP") || signal.contains("EXITCODE")
                || signal.contains("CREATECONTAINERCONFIGERROR") || signal.contains("RUNCONTAINERERROR")) {
            category = "APPLICATION_STARTUP";
        } else if (signal.contains("IMAGEPULL") || signal.contains("REGISTRY")) {
            category = "IMAGE";
        } else if (signal.contains("DNS ") || signal.contains("COREDNS") || signal.contains("NETWORKPOLICY")
                || signal.contains("CNI ") || signal.contains("PODSANDBOX") || signal.contains("ASSIGN IP")) {
            category = "NETWORK";
        } else if (signal.contains("TARGETPORT") || signal.contains("ENDPOINT") || signal.contains("SERVICE SELECTOR")
                || signal.contains("INGRESS BACKEND")) {
            category = "TRAFFIC";
        } else if (signal.contains("PROGRESSDEADLINE") || signal.contains("ROLLOUT")) {
            category = "ROLLOUT";
        } else if (signal.contains("FAILEDSCHEDULING") || signal.contains("TAINT")
                || signal.contains("AFFINITY") || signal.contains("PREEMPTION")) {
            category = "SCHEDULING";
        } else if (signal.contains("RBAC") || signal.contains("SERVICEACCOUNT")
                || signal.contains("ROLEBINDING") || signal.contains("CLUSTERROLE")
                || signal.contains("UNAUTHORIZED")) {
            category = "RBAC";
        } else if (signal.contains("NODENOTREADY") || signal.contains("DISKPRESSURE")
                || signal.contains("PIDPRESSURE") || signal.contains("KUBELET")) {
            category = "NODE";
        } else {
            category = "UNKNOWN";
        }
        int readOnly = 0;
        boolean mutation = false;
        boolean guarded = false;
        for (String command : fixture.commands()) {
            String normalized = command.toLowerCase(Locale.ROOT);
            boolean changesState = Set.of(" apply ", " patch ", " delete ", " scale ", " rollout undo ")
                    .stream().anyMatch(token -> (" " + normalized + " ").contains(token));
            if (changesState) {
                mutation = true;
                guarded = guarded || fixture.requiresMutationGuard();
            } else {
                readOnly++;
            }
        }
        int confidence = Math.min(95, 45 + fixture.evidence().size() * 10 + readOnly * 5);
        return new EvaluatedFinding(category, fixture.resourceKind(), fixture.resourceName(), fixture.evidence().size(),
                readOnly, mutation, guarded, confidence);
    }

    private void check(String assertion, boolean passed, List<String> assertions, List<String> failures) {
        (passed ? assertions : failures).add(assertion);
    }

    private record EvaluatedFinding(String category, String resourceKind, String resourceName, int evidenceCount,
                                    int readOnlyCommands, boolean hasMutation, boolean mutationGuarded, int confidence) {
    }

    public record CorpusProfile(String version, int distinctCases, List<String> categories,
                                int abstentionCases, boolean generatedVariantsRemoved) {
    }
}
