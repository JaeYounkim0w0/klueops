package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.strato.aiops.application.port.out.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** 실제 Chart 근거로 생성하고 필요할 때만 자료 조회와 검증 교정을 이어간다. */
@Service
public class HelmValuesAssistanceService {
    private final ApplicationDeliveryRepositoryPort repository;
    private final ChartArchiveInspectionPort inspector;
    private final HelmValuesSuggestionPort ai;
    private final HelmValuesSuggestionService validator;
    private final ObjectMapper json;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HelmValuesAssistanceService.class);
    private static final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(2);

    public HelmValuesAssistanceService(ApplicationDeliveryRepositoryPort repository, ChartArchiveInspectionPort inspector,
                                      HelmValuesSuggestionPort ai, HelmValuesSuggestionService validator, ObjectMapper json) {
        this.repository = repository; this.inspector = inspector; this.ai = ai; this.validator = validator; this.json = json;
    }

    /** 동기 호환 API도 같은 Chart 기반 생성 흐름으로 처리한다. */
    public Result assist(UUID tenant, UUID version, String current, String instruction) {
        return assist(tenant, version, current, instruction, () -> true);
    }

    /** 취소와 호출 수를 제한하며 외부 AI 대기 동안 DB transaction을 유지하지 않는다. */
    public Result assist(UUID tenant, UUID versionId, String currentYaml, String instruction,
                         java.util.function.BooleanSupplier active) {
        if (!slots.tryAcquire()) throw new IllegalStateException("Values 도우미가 다른 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요.");
        try { return execute(tenant, versionId, currentYaml, instruction, active); }
        finally { slots.release(); }
    }

    /** 작은 Chart는 한 번에 생성하고 대형 자료는 주소로 추가 조회한 후 검증한다. */
    private Result execute(UUID tenant, UUID versionId, String currentYaml, String instruction,
                           java.util.function.BooleanSupplier active) {
        if (instruction == null || instruction.isBlank() || instruction.length() > 2000)
            throw new IllegalArgumentException("요청 내용은 1~2000자로 입력해 주세요.");
        var version = repository.findVersion(tenant, versionId).orElseThrow();
        var chart = repository.findChart(tenant, version.tenantChartId()).orElseThrow();
        byte[] artifact = repository.loadArtifact(tenant, versionId);
        var archive = inspector.inspect(artifact);
        if (!chart.name().equals(archive.name()) || !version.chartVersion().equals(archive.version()))
            throw new IllegalStateException("Stored Chart identity does not match its artifact");
        String current = currentYaml == null || currentYaml.isBlank() ? "{}" : currentYaml;
        ChartValuesEligibility.parse(current);
        String masked = validator.compactAndMaskYaml(current);
        String protectedInstruction = validator.maskInstructionSecrets(instruction.trim());
        List<Requirement> requirements = List.of();
        int calls = 0, corrections = 0;
        String stage = "REFERENCES";
        long started = System.nanoTime();
        try {
            JsonNode defaults = HelmValuesDefaults.combined(archive);
            if (!protectedInstruction.equals(instruction.trim()))
                return result("NEEDS_INPUT", "", chart, version, archive, 0, requirements, List.of(), List.of(),
                        List.of("비밀번호 대신 Chart가 지원하는 인증 방식과 기존 Secret 이름/key를 지정해 주세요."));
            var references = new HelmValuesReferences(archive);
            JsonNode schema = archive.valuesSchemaJson() == null ? json.createObjectNode() : json.readTree(archive.valuesSchemaJson());
            var contract = new HelmValuesMapping(json, defaults, schema, archive.templateValuePaths());
            String index = references.index();
            String evidence = references.initialEvidence(protectedInstruction);
            String feedback = null;
            Set<String> reads = new HashSet<>();
            // 추가 조회와 교정을 합해 최대 5회이며 동일 페이지 반복 요청은 종료한다.
            while (calls < 5) {
                if (!active.getAsBoolean()) throw new IllegalStateException("Job no longer active");
                stage = "GENERATE";
                var request = new HelmValuesSuggestionPort.SuggestionRequest("helm-values.grounded.v2", chart.name(),
                        chart.packageName(), chart.providerName(), chart.sourceType().name(), version.chartVersion(),
                        version.appVersion(), masked, index, evidence, "", List.of(), protectedInstruction, feedback);
                if (masked.length() + index.length() + evidence.length() + (feedback == null ? 0 : feedback.length()) > 42000)
                    throw new IllegalArgumentException("Chart 참조 색인이 처리 가능한 범위를 초과했습니다.");
                calls++;
                JsonNode response = HelmValuesPlan.decode(json, ai.suggest(tenant, request));
                if (!active.getAsBoolean()) throw new IllegalStateException("Job no longer active");
                if (response.has("referenceIds")) {
                    JsonNode ids = response.get("referenceIds");
                    if (!ids.isArray()) throw new IllegalArgumentException("Invalid reference request");
                    List<String> selected = new ArrayList<>();
                    ids.forEach(id -> selected.add(id.asText()));
                    if (!reads.add(selected.toString())) throw new IllegalArgumentException("Repeated reference request");
                    evidence = references.read(selected);
                    continue;
                }
                try {
                    HelmValuesPlan plan = HelmValuesPlan.parse(response);
                    requirements = plan.requirements();
                    if (!plan.questions().isEmpty())
                        return result("NEEDS_INPUT", "", chart, version, archive, calls, requirements,
                                List.of(), List.of(), plan.questions());
                    String candidate = yaml.writeValueAsString(contract.apply(ChartValuesEligibility.parse(masked), plan.changes()));
                    stage = "VALIDATE";
                    String restored = validator.validatePlannedCandidate(candidate, protectedInstruction, current);
                    String manifest = validator.validateAndRender(archive, artifact, restored);
                    // Custom Values 단계는 Values 계약과 Helm lint/template만 검증하고 배포 상태는 Job에서 확인한다.
                    List<String> warnings = List.of("검증 범위: Values 경로·Schema·Helm 렌더링. 실제 Cluster 배포 상태는 배포 Job에서 확인합니다.");
                    return result("HELM_TEMPLATE_VALIDATED", restored, chart, version, archive, calls, requirements,
                            plan.changes(), warnings, List.of());
                } catch (IllegalArgumentException failure) {
                    if (++corrections > 2) throw failure;
                    feedback = HelmValuesValidationFailure.correctionFor(failure);
                    log.info("Values assistance correction requested stage={} calls={} origin={}", stage, calls, failure.getStackTrace()[0]);
                }
            }
            throw new IllegalArgumentException("Reference/repair call budget exhausted");
        } catch (Exception failure) {
            log.warn("Values assistance failed stage={} calls={} errorType={} origin={} latencyMs={}",
                    stage, calls, failure.getClass().getSimpleName(), failure.getStackTrace()[0], (System.nanoTime() - started) / 1_000_000);
            String message = failure instanceof HelmValuesValidationFailure validationFailure
                    ? "Chart Values 매핑 실패: " + validationFailure.getMessage()
                    : stage.equals("REFERENCES")
                    ? "Chart의 기본 Values 또는 참조 자료를 사용할 수 없습니다. 파일과 지원 범위를 확인해 주세요."
                    : "AI 생성 또는 검증을 완료하지 못했습니다. 현재 Values는 유지됩니다. 요청 조건과 Chart 자료를 확인해 주세요.";
            return result("GENERATION_FAILED", "", chart, version, archive, calls, requirements, List.of(), List.of(), List.of(message));
        }
    }

    /** 모든 결과에 동일한 immutable Chart 좌표와 실제 호출 횟수를 제공한다. */
    private Result result(String status, String values, io.strato.aiops.domain.applicationdelivery.TenantChart chart,
                          io.strato.aiops.domain.applicationdelivery.ChartVersion version,
                          ChartArchiveInspectionPort.InspectedArchive archive, int calls, List<Requirement> requirements,
                          List<HelmValuesMapping.Change> changes, List<String> warnings, List<String> questions) {
        return new Result(values, "helm-values.grounded.v2", status, calls, chart.name(), chart.providerName(),
                version.chartVersion(), version.appVersion(), archive.valuesSchemaJson() != null,
                List.copyOf(warnings), List.copyOf(requirements), List.copyOf(changes), List.copyOf(questions));
    }

    public record Requirement(String id, String request, String kind) { }
    public record Result(String valuesYaml, String promptVersion, String validationStatus, int attempts,
                         String chartName, String providerName, String chartVersion, String applicationVersion,
                         boolean schemaIncluded, List<String> warnings, List<Requirement> requirements,
                         List<HelmValuesMapping.Change> changes, List<String> questions) { }
}
