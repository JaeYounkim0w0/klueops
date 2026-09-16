package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.strato.aiops.application.port.out.ApplicationDeliveryRepositoryPort;
import io.strato.aiops.application.port.out.ChartArchiveInspectionPort;
import io.strato.aiops.application.port.out.HelmValuesSuggestionPort;
import io.strato.aiops.domain.applicationdelivery.ChartVersion;
import io.strato.aiops.domain.applicationdelivery.TenantChart;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class HelmValuesSuggestionService {
    static final String PROMPT_VERSION = "helm-values.v10";
    private static final int MAXIMUM_CUSTOM_VALUES_CONTEXT = 12_000;
    private static final int MAXIMUM_DEFAULT_VALUES_CONTEXT = 28_000;
    private static final int MAXIMUM_SCHEMA_CONTEXT = 18_000;
    private static final int MAXIMUM_VALIDATION_FEEDBACK = 2_000;
    private static final int MAXIMUM_ATTEMPTS = 3;
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(password|passwd|secret|token|api[-_]?key|access[-_]?key|private[-_]?key|certificate|credential).*");

    private final ApplicationDeliveryRepositoryPort repository;
    private final ChartArchiveInspectionPort archiveInspector;
    private final HelmValuesSuggestionPort suggestionPort;
    private final HelmReleaseCommandRunner helmRunner;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper;

    /** HelmValuesSuggestionService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** HelmValuesSuggestionService의 suggest 처리에 필요한 업무 로직을 수행한다. */
    public SuggestionResult suggest(UUID tenantId, UUID chartVersionId, String currentValues, String instruction) {
        // Helm의 빈 override는 유효하므로 AI 요청 안에서는 빈 입력을 명시적인 빈 mapping으로 정규화한다.
        String normalizedCurrentValues = normalizeCurrentValues(currentValues);
        requireYamlMapping(normalizedCurrentValues);
        if (instruction == null || instruction.isBlank() || instruction.length() > 2000) {
            throw new IllegalArgumentException("A Values instruction of up to 2000 characters is required");
        }
        ChartContract contract = loadContract(tenantId, chartVersionId);
        String maskedCurrent = bounded(compactAndMaskYaml(normalizedCurrentValues), MAXIMUM_CUSTOM_VALUES_CONTEXT,
                "Current custom Values is too large for a safe AI suggestion");
        ValuesReferenceContext references = focusedReferences(contract, normalizedCurrentValues, instruction);
        Set<String> requiredRootKeys = requestedRootKeys(contract, instruction);
        String defaultSkeleton = boundedReference(references.defaultValues(), MAXIMUM_DEFAULT_VALUES_CONTEXT);
        String schema = boundedReference(references.schemaJson(), MAXIMUM_SCHEMA_CONTEXT);
        String validationFeedback = null;

        for (int attempt = 1; attempt <= MAXIMUM_ATTEMPTS; attempt++) {
            var request = new HelmValuesSuggestionPort.SuggestionRequest(PROMPT_VERSION,
                    contract.chart().name(), contract.chart().packageName(), contract.chart().sourceName(),
                    contract.chart().sourceType().name(), contract.version().chartVersion(),
                    contract.version().appVersion(), maskedCurrent, defaultSkeleton, schema,
                    List.copyOf(requiredRootKeys), instruction.trim(), validationFeedback);
            String candidate = stripMarkdown(suggestionPort.suggest(tenantId, request));
            try {
                String restored = restoreSensitiveValues(normalizedCurrentValues, candidate);
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

    /** HelmValuesSuggestionService의 requireValid 처리 입력과 현재 상태의 유효성을 검증한다. */
    public void requireValid(UUID tenantId, UUID chartVersionId, String valuesYaml) {
        requireValidForChart(loadContract(tenantId, chartVersionId), valuesYaml);
    }

    /** HelmValuesSuggestionService의 loadContract 처리 결과를 조회해 반환한다. */
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

    /** HelmValuesSuggestionService의 requireValidForChart 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireValidForChart(ChartContract contract, String valuesYaml) {
        requireYamlMapping(valuesYaml);
        requireValuesContract(contract, valuesYaml);
        // AI와 수동 편집 모두 저장 전에 같은 immutable Chart로 Helm 렌더링해 타입/템플릿 계약을 검증한다.
        String manifest = helmRunner.render("klueops-values-check", "default", contract.archive(), valuesYaml);
        // Helm template이 성공해도 Kubernetes API가 거부할 Service 포트 계약은 Revision 저장 전에 차단한다.
        RenderedExposureInspector.requireValidServices(manifest, "default");
    }

    /** HelmValuesSuggestionService의 requireValuesContract 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireValuesContract(ChartContract contract, String valuesYaml) {
        try {
            Object parsed = yamlMapper.readValue(valuesYaml, Object.class);
            Map<?, ?> candidate = (Map<?, ?>) parsed;
            Set<String> candidateKeys = candidate.keySet().stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (candidateKeys.contains("apiVersion") || candidateKeys.contains("kind")
                    || candidateKeys.containsAll(Set.of("metadata", "spec"))) {
                throw new IllegalArgumentException("Custom Values must be an override mapping, not a Kubernetes manifest. "
                        + "Do not output apiVersion, kind, metadata, or spec resource fields");
            }
            if (valuesYaml.contains("***REDACTED***")) {
                throw new IllegalArgumentException("Custom Values must not introduce a redacted placeholder or a new secret value");
            }
            Object defaults = yamlMapper.readValue(contract.inspected().defaultValuesYaml(), Object.class);
            JsonNode schema = contract.inspected().valuesSchemaJson() == null
                    ? null : jsonMapper.readTree(contract.inspected().valuesSchemaJson());
            validateSupportedShape(candidate, defaults, schema, "$", new LinkedHashSet<>());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Values YAML is invalid", exception);
        }
    }

    /** HelmValuesSuggestionService의 requestedRootKeys 처리에 필요한 업무 로직을 수행한다. */
    private Set<String> requestedRootKeys(ChartContract contract, String instruction) {
        try {
            Object parsedDefaults = yamlMapper.readValue(contract.inspected().defaultValuesYaml(), Object.class);
            if (!(parsedDefaults instanceof Map<?, ?> defaults)) return Set.of();
            String normalized = instruction.toLowerCase(Locale.ROOT);
            Set<String> required = new LinkedHashSet<>();
            requireWhen(defaults, required, normalized, List.of("replica", "복제", "개로", "개로 설정"), "replicaCount");
            requireWhen(defaults, required, normalized, List.of("service", "port", "clusterip", "nodeport", "loadbalancer", "서비스", "포트"), "service");
            requireWhen(defaults, required, normalized, List.of("persist", "storage", "volume", "pvc", "영속", "저장", "용량"), "persistence");
            requireWhen(defaults, required, normalized, List.of("resource", "cpu", "memory", "메모리", "리소스"), "resources");
            requireWhen(defaults, required, normalized, List.of("security", "runas", "seccomp", "privilege", "보안"), "containerSecurityContext");
            requireWhen(defaults, required, normalized, List.of("liveness"), "livenessProbe");
            requireWhen(defaults, required, normalized, List.of("readiness"), "readinessProbe");
            requireWhen(defaults, required, normalized, List.of("password", "secret", "auth", "credential", "비밀번호", "인증"), "auth");
            return required;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Chart default Values is invalid", exception);
        }
    }

    /** HelmValuesSuggestionService의 requireWhen 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireWhen(Map<?, ?> defaults, Set<String> required, String instruction, List<String> signals,
                             String key) {
        if (defaults.containsKey(key) && signals.stream().anyMatch(instruction::contains)) required.add(key);
    }

    /** HelmValuesSuggestionService의 validateSupportedShape 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void validateSupportedShape(Object candidate, Object defaults, JsonNode schema, String path,
                                        Set<String> visitedPaths) {
        if (!visitedPaths.add(path) || !(candidate instanceof Map<?, ?> candidateMap)) return;
        Map<?, ?> defaultMap = defaults instanceof Map<?, ?> values ? values : Map.of();
        JsonNode properties = schema == null ? null : schema.path("properties");
        boolean hasSchemaProperties = properties != null && properties.isObject() && !properties.isEmpty();
        boolean arbitraryMap = defaultMap.isEmpty() && !hasSchemaProperties;
        for (Map.Entry<?, ?> entry : candidateMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            boolean inDefaults = defaultMap.containsKey(key);
            JsonNode childSchema = hasSchemaProperties ? properties.get(key) : null;
            if (!inDefaults && childSchema == null && !arbitraryMap) {
                throw new IllegalArgumentException("Unsupported Values path for this exact Chart: " + path + "." + key
                        + ". Use only nested keys present in the Chart default Values or JSON Schema");
            }
            Object childDefaults = inDefaults ? defaultMap.get(key) : null;
            if (entry.getValue() instanceof Map<?, ?>) {
                validateSupportedShape(entry.getValue(), childDefaults, childSchema, path + "." + key, visitedPaths);
            } else if (entry.getValue() instanceof List<?> candidateList && !candidateList.isEmpty()) {
                Object defaultItem = childDefaults instanceof List<?> defaultList && !defaultList.isEmpty()
                        ? defaultList.get(0) : null;
                JsonNode itemSchema = childSchema == null ? null : childSchema.path("items");
                for (int index = 0; index < candidateList.size(); index++) {
                    validateSupportedShape(candidateList.get(index), defaultItem, itemSchema,
                            path + "." + key + "[" + index + "]", visitedPaths);
                }
            }
        }
    }

    /** HelmValuesSuggestionService의 compactAndMaskYaml 처리에 필요한 업무 로직을 수행한다. */
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

    /** HelmValuesSuggestionService의 compactJson 처리에 필요한 업무 로직을 수행한다. */
    private String compactJson(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) return null;
        try {
            return jsonMapper.writeValueAsString(jsonMapper.readTree(schemaJson));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Chart values.schema.json is invalid", exception);
        }
    }

    /** HelmValuesSuggestionService의 focusedReferences 처리에 필요한 업무 로직을 수행한다. */
    @SuppressWarnings("unchecked")
    private ValuesReferenceContext focusedReferences(ChartContract contract, String currentValues, String instruction) {
        try {
            Object parsed = yamlMapper.readValue(contract.inspected().defaultValuesYaml(), Object.class);
            if (!(parsed instanceof Map<?, ?> defaultMap)) {
                return new ValuesReferenceContext("(not provided by this Chart)",
                        compactJson(contract.inspected().valuesSchemaJson()));
            }
            Set<String> selected = relevantRootKeys(defaultMap, currentValues, instruction);
            Map<String, Object> focusedDefaults = new LinkedHashMap<>();
            selected.forEach(key -> {
                if (defaultMap.containsKey(key)) focusedDefaults.put(key, defaultMap.get(key));
            });
            maskSensitiveNode(focusedDefaults);
            String availableKeys = defaultMap.keySet().stream().map(String::valueOf).sorted().toList().toString();
            String defaults = "Available top-level keys (reference only): " + availableKeys
                    + "\nRelevant exact default Values:\n" + yamlMapper.writeValueAsString(focusedDefaults).trim();
            return new ValuesReferenceContext(defaults,
                    focusedSchema(contract.inspected().valuesSchemaJson(), selected));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Chart default Values is invalid", exception);
        }
    }

    /** HelmValuesSuggestionService의 relevantRootKeys 처리에 필요한 업무 로직을 수행한다. */
    private Set<String> relevantRootKeys(Map<?, ?> defaults, String currentValues, String instruction)
            throws JsonProcessingException {
        Set<String> selected = new LinkedHashSet<>();
        String normalized = instruction.toLowerCase(Locale.ROOT);
        defaults.keySet().stream().map(String::valueOf)
                .filter(key -> normalized.contains(key.toLowerCase(Locale.ROOT)))
                .forEach(selected::add);
        Object current = yamlMapper.readValue(currentValues, Object.class);
        if (current instanceof Map<?, ?> currentMap) currentMap.keySet().forEach(key -> selected.add(String.valueOf(key)));
        selectWhen(defaults, selected, normalized, List.of("replica", "instance", "standalone", "단일", "복제"),
                "architecture", "replicaCount", "clusterReplicaCount");
        selectWhen(defaults, selected, normalized, List.of("service", "port", "clusterip", "nodeport", "loadbalancer", "서비스", "포트"),
                "service");
        selectWhen(defaults, selected, normalized, List.of("persist", "storage", "volume", "pvc", "영속", "저장", "용량"),
                "persistence", "persistentVolumeClaimRetentionPolicy", "volumePermissions");
        selectWhen(defaults, selected, normalized, List.of("resource", "cpu", "memory", "메모리", "리소스"),
                "resources");
        selectWhen(defaults, selected, normalized, List.of("security", "runas", "seccomp", "privilege", "보안"),
                "containerSecurityContext", "podSecurityContext");
        selectWhen(defaults, selected, normalized, List.of("probe", "liveness", "readiness", "startup", "상태 점검"),
                "livenessProbe", "readinessProbe", "startupProbe");
        selectWhen(defaults, selected, normalized, List.of("password", "secret", "auth", "credential", "비밀번호", "인증"),
                "auth");
        // 의미를 분류하지 못한 요청도 모델이 임의 구조를 만들지 않도록 작은 기본 참조를 제공한다.
        if (selected.isEmpty()) defaults.keySet().stream().map(String::valueOf).limit(8).forEach(selected::add);
        return selected;
    }

    /** HelmValuesSuggestionService의 selectWhen 처리에 필요한 업무 로직을 수행한다. */
    private void selectWhen(Map<?, ?> defaults, Set<String> selected, String instruction, List<String> signals,
                            String... keys) {
        if (signals.stream().noneMatch(instruction::contains)) return;
        for (String key : keys) if (defaults.containsKey(key)) selected.add(key);
    }

    /** HelmValuesSuggestionService의 focusedSchema 처리에 필요한 업무 로직을 수행한다. */
    private String focusedSchema(String schemaJson, Set<String> selected) throws JsonProcessingException {
        if (schemaJson == null || schemaJson.isBlank()) return null;
        JsonNode root = jsonMapper.readTree(schemaJson);
        JsonNode properties = root.path("properties");
        if (!properties.isObject()) return compactJson(schemaJson);
        ObjectNode focused = jsonMapper.createObjectNode();
        focused.put("type", "object");
        ObjectNode focusedProperties = focused.putObject("properties");
        selected.forEach(key -> {
            JsonNode property = properties.get(key);
            if (property != null) focusedProperties.set(key, property);
        });
        return jsonMapper.writeValueAsString(focused);
    }

    /** HelmValuesSuggestionService의 restoreSensitiveValues 처리에 필요한 업무 로직을 수행한다. */
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

    /** HelmValuesSuggestionService의 maskSensitiveNode 처리에 필요한 업무 로직을 수행한다. */
    @SuppressWarnings("unchecked")
    private void maskSensitiveNode(Object value) {
        if (value instanceof Map<?, ?> map) {
            ((Map<Object, Object>) map).replaceAll((key, child) -> {
                if (isSensitiveValueKey(key)) return "***REDACTED***";
                maskSensitiveNode(child);
                return child;
            });
        } else if (value instanceof List<?> list) {
            list.forEach(this::maskSensitiveNode);
        }
    }

    /** HelmValuesSuggestionService의 restoreSensitiveNode 처리에 필요한 업무 로직을 수행한다. */
    @SuppressWarnings("unchecked")
    private void restoreSensitiveNode(Object original, Object candidate) {
        if (original instanceof Map<?, ?> originalMap && candidate instanceof Map<?, ?> candidateMap) {
            Map<Object, Object> target = (Map<Object, Object>) candidateMap;
            originalMap.forEach((key, value) -> {
                if (isSensitiveValueKey(key)) target.put(key, value);
                else restoreSensitiveNode(value, target.get(key));
            });
        } else if (original instanceof List<?> originalList && candidate instanceof List<?> candidateList) {
            int size = Math.min(originalList.size(), candidateList.size());
            for (int index = 0; index < size; index++) {
                restoreSensitiveNode(originalList.get(index), candidateList.get(index));
            }
        }
    }

    /** HelmValuesSuggestionService의 isSensitiveValueKey 처리 조건의 충족 여부를 판단한다. */
    private boolean isSensitiveValueKey(Object key) {
        String value = String.valueOf(key);
        String normalized = value.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
        // 기존 Secret의 이름·내부 key 참조는 credential 본문이 아니므로 정확한 Values 작성을 위해 유지한다.
        if (normalized.contains("existingsecret")) return false;
        return SENSITIVE_KEY.matcher(value).matches();
    }

    /** HelmValuesSuggestionService의 requireYamlMapping 처리 입력과 현재 상태의 유효성을 검증한다. */
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

    /** 비어 있는 Custom Values를 Helm이 해석 가능한 빈 YAML mapping으로 정규화한다. */
    private String normalizeCurrentValues(String valuesYaml) {
        return valuesYaml == null || valuesYaml.isBlank() ? "{}\n" : valuesYaml;
    }

    /** HelmValuesSuggestionService의 stripMarkdown 처리에 필요한 업무 로직을 수행한다. */
    private String stripMarkdown(String value) {
        String result = value == null ? "" : value.trim();
        if (result.startsWith("```")) {
            int firstBreak = result.indexOf('\n');
            int end = result.lastIndexOf("```");
            if (firstBreak >= 0 && end > firstBreak) result = result.substring(firstBreak + 1, end).trim();
        }
        return result;
    }

    /** HelmValuesSuggestionService의 bounded 처리에 필요한 업무 로직을 수행한다. */
    private String bounded(String value, int maximum, String message) {
        if (value != null && value.length() > maximum) throw new IllegalArgumentException(message);
        return value == null ? "{}" : value;
    }

    /** HelmValuesSuggestionService의 boundedReference 처리에 필요한 업무 로직을 수행한다. */
    private String boundedReference(String value, int maximum) {
        if (value == null || value.isBlank()) return "(not provided by this Chart)";
        if (value.length() <= maximum) return value;
        return value.substring(0, maximum) + "\n# [KlueOps reference truncated at safe context limit]";
    }

    /** HelmValuesSuggestionService의 boundedFeedback 처리에 필요한 업무 로직을 수행한다. */
    private String boundedFeedback(String message) {
        String value = message == null ? "Unknown Helm validation failure" : message;
        return value.substring(0, Math.min(value.length(), MAXIMUM_VALIDATION_FEEDBACK));
    }

    private record ChartContract(TenantChart chart, ChartVersion version, byte[] archive,
                                 ChartArchiveInspectionPort.InspectedArchive inspected) { }

    private record ValuesReferenceContext(String defaultValues, String schemaJson) { }

    public record SuggestionResult(String valuesYaml, String promptVersion, String validationStatus, int attempts,
                                   String chartName, String providerName, String chartVersion,
                                   String applicationVersion, boolean schemaIncluded) { }
}
