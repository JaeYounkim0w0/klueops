package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.strato.aiops.application.port.out.ApplicationDeliveryRepositoryPort;
import io.strato.aiops.application.port.out.ChartArchiveInspectionPort;
import io.strato.aiops.application.port.out.HelmValuesSuggestionPort;
import io.strato.aiops.domain.applicationdelivery.ChartVersion;
import io.strato.aiops.domain.applicationdelivery.TenantChart;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class HelmValuesSuggestionService {
    static final String PROMPT_VERSION = "helm-values.v2";
    private static final int MAXIMUM_CUSTOM_VALUES_CONTEXT = 12_000;
    private static final int MAXIMUM_DEFAULT_VALUES_CONTEXT = 28_000;
    private static final int MAXIMUM_SCHEMA_CONTEXT = 18_000;
    private static final int MAXIMUM_VALIDATION_FEEDBACK = 2_000;
    private static final int MAXIMUM_ATTEMPTS = 2;
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(password|passwd|secret|token|api[-_]?key|access[-_]?key|private[-_]?key|certificate|credential).*");

    private final ApplicationDeliveryRepositoryPort repository;
    private final ChartArchiveInspectionPort archiveInspector;
    private final HelmValuesSuggestionPort suggestionPort;
    private final HelmReleaseCommandRunner helmRunner;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper;

    public HelmValuesSuggestionService(ApplicationDeliveryRepositoryPort repository,
                                       ChartArchiveInspectionPort archiveInspector,
                                       HelmValuesSuggestionPort suggestionPort,
                                       HelmReleaseCommandRunner helmRunner,
                                       ObjectMapper jsonMapper) {
        this.repository = repository;
        this.archiveInspector = archiveInspector;
        this.suggestionPort = suggestionPort;
        this.helmRunner = helmRunner;
        this.jsonMapper = jsonMapper;
    }

    public SuggestionResult suggest(UUID tenantId, UUID chartVersionId, String currentValues, String instruction) {
        requireYamlMapping(currentValues);
        if (instruction == null || instruction.isBlank() || instruction.length() > 2000) {
            throw new IllegalArgumentException("A Values instruction of up to 2000 characters is required");
        }
        ChartContract contract = loadContract(tenantId, chartVersionId);
        String maskedCurrent = bounded(compactAndMaskYaml(currentValues), MAXIMUM_CUSTOM_VALUES_CONTEXT,
                "Current custom Values is too large for a safe AI suggestion");
        String defaultSkeleton = boundedReference(compactAndMaskYaml(contract.inspected().defaultValuesYaml()),
                MAXIMUM_DEFAULT_VALUES_CONTEXT);
        String schema = boundedReference(compactJson(contract.inspected().valuesSchemaJson()), MAXIMUM_SCHEMA_CONTEXT);
        String validationFeedback = null;

        for (int attempt = 1; attempt <= MAXIMUM_ATTEMPTS; attempt++) {
            var request = new HelmValuesSuggestionPort.SuggestionRequest(PROMPT_VERSION,
                    contract.chart().name(), contract.chart().packageName(), contract.chart().sourceName(),
                    contract.chart().sourceType().name(), contract.version().chartVersion(),
                    contract.version().appVersion(), maskedCurrent, defaultSkeleton, schema,
                    instruction.trim(), validationFeedback);
            String candidate = stripMarkdown(suggestionPort.suggest(tenantId, request));
            try {
                String restored = restoreSensitiveValues(currentValues, candidate);
                requireValidForChart(contract, restored);
                return new SuggestionResult(restored, PROMPT_VERSION, "HELM_TEMPLATE_VALIDATED", attempt,
                        contract.chart().name(), contract.chart().sourceName(), contract.version().chartVersion(),
                        contract.version().appVersion(), contract.inspected().valuesSchemaJson() != null);
            } catch (IllegalArgumentException exception) {
                validationFeedback = boundedFeedback(exception.getMessage());
            }
        }
        throw new IllegalArgumentException("AI suggestion did not satisfy " + contract.chart().name() + " "
                + contract.version().chartVersion() + " after " + MAXIMUM_ATTEMPTS
                + " attempts: " + validationFeedback);
    }

    public void requireValid(UUID tenantId, UUID chartVersionId, String valuesYaml) {
        requireValidForChart(loadContract(tenantId, chartVersionId), valuesYaml);
    }

    private ChartContract loadContract(UUID tenantId, UUID chartVersionId) {
        ChartVersion version = repository.findVersion(tenantId, chartVersionId).orElseThrow();
        TenantChart chart = repository.findChart(tenantId, version.tenantChartId()).orElseThrow();
        byte[] archive = repository.loadArtifact(tenantId, chartVersionId);
        var inspected = archiveInspector.inspect(archive);
        if (!version.chartVersion().equals(inspected.version()) || !chart.name().equals(inspected.name())) {
            throw new IllegalStateException("Stored Chart metadata does not match the immutable Chart artifact");
        }
        return new ChartContract(chart, version, archive, inspected);
    }

    private void requireValidForChart(ChartContract contract, String valuesYaml) {
        requireYamlMapping(valuesYaml);
        // AI와 수동 편집 모두 저장 전에 같은 immutable Chart로 Helm 렌더링해 타입/템플릿 계약을 검증한다.
        helmRunner.render("klueops-values-check", "default", contract.archive(), valuesYaml);
    }

    private String compactAndMaskYaml(String valuesYaml) {
        if (valuesYaml == null || valuesYaml.isBlank()) return "{}";
        try {
            Object parsed = yamlMapper.readValue(valuesYaml, Object.class);
            if (!(parsed instanceof Map<?, ?>)) return "{}";
            maskSensitiveNode(parsed);
            return yamlMapper.writeValueAsString(parsed).trim();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Values YAML is invalid", exception);
        }
    }

    private String compactJson(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) return null;
        try {
            return jsonMapper.writeValueAsString(jsonMapper.readTree(schemaJson));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Chart values.schema.json is invalid", exception);
        }
    }

    private String restoreSensitiveValues(String originalYaml, String candidateYaml) {
        requireYamlMapping(candidateYaml);
        try {
            Object original = yamlMapper.readValue(originalYaml, Object.class);
            Object candidate = yamlMapper.readValue(candidateYaml, Object.class);
            restoreSensitiveNode(original, candidate);
            return yamlMapper.writeValueAsString(candidate).trim();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("AI Values YAML is invalid", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private void maskSensitiveNode(Object value) {
        if (value instanceof Map<?, ?> map) {
            ((Map<Object, Object>) map).replaceAll((key, child) -> {
                if (SENSITIVE_KEY.matcher(String.valueOf(key)).matches()) return "***REDACTED***";
                maskSensitiveNode(child);
                return child;
            });
        } else if (value instanceof List<?> list) {
            list.forEach(this::maskSensitiveNode);
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreSensitiveNode(Object original, Object candidate) {
        if (original instanceof Map<?, ?> originalMap && candidate instanceof Map<?, ?> candidateMap) {
            Map<Object, Object> target = (Map<Object, Object>) candidateMap;
            originalMap.forEach((key, value) -> {
                if (SENSITIVE_KEY.matcher(String.valueOf(key)).matches()) target.put(key, value);
                else restoreSensitiveNode(value, target.get(key));
            });
        } else if (original instanceof List<?> originalList && candidate instanceof List<?> candidateList) {
            int size = Math.min(originalList.size(), candidateList.size());
            for (int index = 0; index < size; index++) {
                restoreSensitiveNode(originalList.get(index), candidateList.get(index));
            }
        }
    }

    private void requireYamlMapping(String valuesYaml) {
        if (valuesYaml == null || valuesYaml.isBlank()) throw new IllegalArgumentException("Values YAML is required");
        if (valuesYaml.length() > 1024 * 1024) throw new IllegalArgumentException("Values YAML exceeds 1 MiB");
        try {
            Object parsed = yamlMapper.readValue(valuesYaml, Object.class);
            if (!(parsed instanceof Map<?, ?>)) throw new IllegalArgumentException("Values YAML root must be an object");
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Values YAML is invalid", exception);
        }
    }

    private String stripMarkdown(String value) {
        String result = value == null ? "" : value.trim();
        if (result.startsWith("```")) {
            int firstBreak = result.indexOf('\n');
            int end = result.lastIndexOf("```");
            if (firstBreak >= 0 && end > firstBreak) result = result.substring(firstBreak + 1, end).trim();
        }
        return result;
    }

    private String bounded(String value, int maximum, String message) {
        if (value != null && value.length() > maximum) throw new IllegalArgumentException(message);
        return value == null ? "{}" : value;
    }

    private String boundedReference(String value, int maximum) {
        if (value == null || value.isBlank()) return "(not provided by this Chart)";
        if (value.length() <= maximum) return value;
        return value.substring(0, maximum) + "\n# [KlueOps reference truncated at safe context limit]";
    }

    private String boundedFeedback(String message) {
        String value = message == null ? "Unknown Helm validation failure" : message;
        return value.substring(0, Math.min(value.length(), MAXIMUM_VALIDATION_FEEDBACK));
    }

    private record ChartContract(TenantChart chart, ChartVersion version, byte[] archive,
                                 ChartArchiveInspectionPort.InspectedArchive inspected) { }

    public record SuggestionResult(String valuesYaml, String promptVersion, String validationStatus, int attempts,
                                   String chartName, String providerName, String chartVersion,
                                   String applicationVersion, boolean schemaIncluded) { }
}
