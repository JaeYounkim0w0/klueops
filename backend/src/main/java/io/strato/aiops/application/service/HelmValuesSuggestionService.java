package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** 수동·AI Values가 동일한 Chart 검증을 사용하도록 조정하며 구형 API를 호환한다. */
@Service
public class HelmValuesSuggestionService {
    private final ApplicationDeliveryRepositoryPort repository;
    private final ChartArchiveInspectionPort inspector;
    private final HelmValuesSuggestionPort ai;
    private final HelmReleaseCommandRunner helm;
    private final ObjectMapper json;
    private final ValuesSecretProtection secrets = new ValuesSecretProtection();

    public HelmValuesSuggestionService(ApplicationDeliveryRepositoryPort repository, ChartArchiveInspectionPort inspector,
                                      HelmValuesSuggestionPort ai, HelmReleaseCommandRunner helm, ObjectMapper json) {
        this.repository = repository; this.inspector = inspector; this.ai = ai; this.helm = helm; this.json = json;
    }

    /** 구형 동기 API도 단일 생성 엔진으로 위임해 예전 휴리스틱 생성 경로를 실행하지 않는다. */
    public SuggestionResult suggest(UUID tenant, UUID version, String current, String instruction) {
        var result = new HelmValuesAssistanceService(repository, inspector, ai, this, json).assist(tenant, version, current, instruction);
        if (!result.validationStatus().equals("HELM_TEMPLATE_VALIDATED"))
            throw new IllegalArgumentException(String.join(" ", result.questions()));
        return new SuggestionResult(result.valuesYaml(), result.promptVersion(), result.validationStatus(), result.attempts(),
                result.chartName(), result.providerName(), result.chartVersion(), result.applicationVersion(), result.schemaIncluded(), result.warnings());
    }

    /** 저장된 정확한 Chart와 동일한 검증을 수행하며 값이나 오류 원문을 로그에 기록하지 않는다. */
    public void requireValid(UUID tenant, UUID versionId, String values) {
        var version = repository.findVersion(tenant, versionId).orElseThrow();
        var chart = repository.findChart(tenant, version.tenantChartId()).orElseThrow();
        byte[] artifact = repository.loadArtifact(tenant, versionId);
        var archive = inspector.inspect(artifact);
        if (!chart.name().equals(archive.name()) || !version.chartVersion().equals(archive.version()))
            throw new IllegalStateException("Stored Chart metadata does not match its artifact");
        validateAndRender(archive, artifact, values);
    }

    /** 기본 Values·Schema 경로를 확인하고 Helm의 실제 Schema/렌더 검증을 실행한다. */
    String validateAndRender(ChartArchiveInspectionPort.InspectedArchive archive, byte[] artifact, String values) {
        var candidate = ChartValuesEligibility.parse(values);
        var defaults = HelmValuesDefaults.combined(archive);
        if (candidate.has("apiVersion") && candidate.has("kind") && candidate.has("metadata"))
            throw new IllegalArgumentException("Custom Values must not be a Kubernetes manifest");
        if (values.contains("***REDACTED***") || values.contains("__KLUEOPS_PROTECTED_INPUT__"))
            throw new IllegalArgumentException("Custom Values contains a protected placeholder");
        try {
            var schema = archive.valuesSchemaJson() == null ? json.createObjectNode() : json.readTree(archive.valuesSchemaJson());
            var contract = new HelmValuesMapping(json, defaults, schema, archive.templateValuePaths());
            contract.requireSupported(candidate);
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("Chart values.schema.json is invalid");
        }
        String manifest = helm.render("klueops-values-check", "default", artifact, values);
        RenderedExposureInspector.requireValidServices(manifest, "default");
        return manifest;
    }

    /** 기존 민감값을 마스킹한 상태로만 모델에 전달한다. */
    String compactAndMaskYaml(String values) { return secrets.compactAndMaskYaml(values); }

    /** 자연어에 포함된 credential을 모델 전송 전에 보호한다. */
    String maskInstructionSecrets(String instruction) { return secrets.maskInstructionSecrets(instruction); }

    /** 새 credential과 근거 없는 Secret 참조를 거부하고 기존 민감값을 복원한다. */
    String validatePlannedCandidate(String candidate, String instruction, String current) {
        return secrets.validatePlannedCandidate(candidate, instruction, current);
    }

    public record SuggestionResult(String valuesYaml, String promptVersion, String validationStatus, int attempts,
                                   String chartName, String providerName, String chartVersion,
                                   String applicationVersion, boolean schemaIncluded, List<String> warnings) { }
}
