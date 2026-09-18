package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.*;
import java.util.regex.Pattern;

/** AI에 전송할 민감값 보호와 생성 결과의 기존 Secret 보존만 담당한다. */
final class ValuesSecretProtection {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(password|passwd|secret|token|api[-_]?key|access[-_]?key|private[-_]?key|certificate|credential).*");
    private static final String PROTECTED_INSTRUCTION_VALUE = "__KLUEOPS_PROTECTED_INPUT__";
    private static final Pattern ENV_SECRET_INSTRUCTION = Pattern.compile(
            "(\\b[A-Z][A-Z0-9_.-]*(?:PASSWORD|PASSWD|SECRET|TOKEN|API[_-]?KEY|ACCESS[_-]?KEY|PRIVATE[_-]?KEY|CREDENTIAL)[A-Z0-9_.-]*\\b\\s*(?:[:=]\\s*|\\s+))([^\\s,;]+)");
    private static final Pattern NAMED_SECRET_INSTRUCTION = Pattern.compile(
            "(?i)((?:password|passwd|token|api[-_ ]?key|access[-_ ]?key|private[-_ ]?key|credential|비밀번호|토큰)\\s*(?:[:=]|은|는)\\s*)([^\\s,;]+)");

    /** 사용자 요청문에 포함된 credential 본문을 LLM 전송 전에 비가역 보호 표식으로 치환한다. */
    String maskInstructionSecrets(String instruction) {
        String masked = ENV_SECRET_INSTRUCTION.matcher(instruction)
                .replaceAll("$1" + PROTECTED_INSTRUCTION_VALUE);
        return NAMED_SECRET_INSTRUCTION.matcher(masked)
                .replaceAll("$1" + PROTECTED_INSTRUCTION_VALUE);
    }

    /** 변경 계획에 새 credential이나 근거 없는 Secret 참조가 있으면 자동 삭제하지 않고 보충 입력을 요구한다. */
    String validatePlannedCandidate(String candidate, String instruction, String current) {
        SanitizedCandidate sanitized = sanitizeGeneratedCredentials(candidate, instruction, current);
        if (sanitized.removedPlaintextCount() > 0 || sanitized.removedReferenceCount() > 0)
            throw new IllegalArgumentException("기존 Secret 이름과 key를 명시해 주세요. 새 credential이나 임의 Secret 참조는 적용하지 않습니다.");
        return restoreSensitiveValues(current, candidate);
    }

    /** AI가 생성한 평문 credential만 제거해 안전한 나머지 제안을 계속 검증할 수 있게 한다. */
    private SanitizedCandidate sanitizeGeneratedCredentials(String candidateYaml, String instruction,
                                                             String currentValuesYaml) {
        requireYamlMapping(candidateYaml);
        try {
            Object candidate = yamlMapper.readValue(candidateYaml, Object.class);
            int removedPlaintext = removeGeneratedPlaintextCredentials(candidate, 0);
            Set<String> allowedReferences = new LinkedHashSet<>();
            Object current = yamlMapper.readValue(currentValuesYaml, Object.class);
            collectSecretReferences(current, allowedReferences, 0);
            int removedReferences = removeUngroundedSecretReferences(candidate, instruction, allowedReferences, 0);
            return new SanitizedCandidate(yamlMapper.writeValueAsString(candidate).trim(), removedPlaintext,
                    removedReferences);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("AI Values YAML is invalid", exception);
        }
    }

    /** 기존 Values에서 사용자가 이미 승인한 Secret resource 이름만 수집한다. */
    private void collectSecretReferences(Object value, Set<String> references, int depth) {
        if (depth > 12) return;
        if (value instanceof Map<?, ?> map) {
            Object secretKeyRef = map.get("secretKeyRef");
            if (secretKeyRef instanceof Map<?, ?> reference && reference.get("name") != null) {
                references.add(String.valueOf(reference.get("name")));
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object child = entry.getValue();
                if (isSecretReferenceKey(key) && !(child instanceof Map<?, ?>) && !(child instanceof List<?>)
                        && child != null && !String.valueOf(child).isBlank()) {
                    references.add(String.valueOf(child));
                }
                collectSecretReferences(child, references, depth + 1);
            }
        } else if (value instanceof List<?> list) {
            for (Object child : list) collectSecretReferences(child, references, depth + 1);
        }
    }

    /** 요청문이나 기존 Values에 근거가 없는 AI 생성 Secret 이름을 제거한다. */
    @SuppressWarnings("unchecked")
    private int removeUngroundedSecretReferences(Object value, String instruction, Set<String> allowed, int depth) {
        if (depth > 12) return 0;
        int removed = 0;
        if (value instanceof Map<?, ?> rawMap) {
            Map<Object, Object> map = (Map<Object, Object>) rawMap;
            Object secretKeyRef = map.get("secretKeyRef");
            if (secretKeyRef instanceof Map<?, ?> reference && reference.get("name") != null) {
                String name = String.valueOf(reference.get("name"));
                if (!isGroundedSecretReference(name, instruction, allowed)) {
                    map.clear();
                    return 1;
                }
            }
            var iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Object, Object> entry = iterator.next();
                String key = String.valueOf(entry.getKey());
                Object child = entry.getValue();
                if (isSecretReferenceKey(key) && !(child instanceof Map<?, ?>) && !(child instanceof List<?>)
                        && child != null && !String.valueOf(child).isBlank()
                        && !isGroundedSecretReference(String.valueOf(child), instruction, allowed)) {
                    iterator.remove();
                    removed++;
                    continue;
                }
                int childRemoved = removeUngroundedSecretReferences(child, instruction, allowed, depth + 1);
                removed += childRemoved;
                if (childRemoved > 0 && ((child instanceof Map<?, ?> childMap && childMap.isEmpty())
                        || (child instanceof List<?> childList && childList.isEmpty()))) iterator.remove();
            }
            Object envName = map.get("name");
            if (removed > 0 && envName != null && SENSITIVE_KEY.matcher(String.valueOf(envName)).matches()
                    && !map.containsKey("value") && !map.containsKey("valueFrom")) map.clear();
        } else if (value instanceof List<?> rawList) {
            List<Object> list = (List<Object>) rawList;
            for (int index = list.size() - 1; index >= 0; index--) {
                Object child = list.get(index);
                int childRemoved = removeUngroundedSecretReferences(child, instruction, allowed, depth + 1);
                removed += childRemoved;
                if (childRemoved > 0 && child instanceof Map<?, ?> childMap && childMap.isEmpty()) list.remove(index);
            }
        }
        return removed;
    }

    /** Secret 이름이 사용자 입력 또는 기존 설정에 명시되어 있는지 확인한다. */
    private boolean isGroundedSecretReference(String name, String instruction, Set<String> allowed) {
        return allowed.contains(name) || instruction.contains(name);
    }

    /** Values 트리를 순회하며 Secret 참조가 아닌 민감 scalar와 민감 환경 변수 항목을 제거한다. */
    @SuppressWarnings("unchecked")
    private int removeGeneratedPlaintextCredentials(Object value, int depth) {
        if (depth > 12) return 0;
        int removed = 0;
        if (value instanceof Map<?, ?> rawMap) {
            Map<Object, Object> map = (Map<Object, Object>) rawMap;
            Object envName = map.get("name");
            Object envValue = map.get("value");
            if (envName != null && envValue != null && SENSITIVE_KEY.matcher(String.valueOf(envName)).matches()
                    && !String.valueOf(envValue).isBlank()
                    && !"***REDACTED***".equals(String.valueOf(envValue))) {
                map.remove("value");
                map.remove("name");
                return 1;
            }
            var iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Object, Object> entry = iterator.next();
                String key = String.valueOf(entry.getKey());
                Object child = entry.getValue();
                if (SENSITIVE_KEY.matcher(key).matches() && !isSecretReferenceKey(key)
                        && child instanceof String
                        && child != null && !String.valueOf(child).isBlank()
                        && !"***REDACTED***".equals(String.valueOf(child))) {
                    iterator.remove();
                    removed++;
                } else {
                    int childRemoved = removeGeneratedPlaintextCredentials(child, depth + 1);
                    removed += childRemoved;
                    if (childRemoved > 0 && ((child instanceof Map<?, ?> childMap && childMap.isEmpty())
                            || (child instanceof List<?> childList && childList.isEmpty()))) {
                        iterator.remove();
                    }
                }
            }
        } else if (value instanceof List<?> rawList) {
            List<Object> list = (List<Object>) rawList;
            for (int index = list.size() - 1; index >= 0; index--) {
                Object child = list.get(index);
                int childRemoved = removeGeneratedPlaintextCredentials(child, depth + 1);
                removed += childRemoved;
                if (childRemoved > 0 && child instanceof Map<?, ?> childMap && childMap.isEmpty()) {
                    list.remove(index);
                }
            }
        }
        return removed;
    }

    /** Secret 자체 값이 아닌 기존 Secret 리소스 이름 참조 key인지 구분한다. */
    private boolean isSecretReferenceKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return normalized.contains("existingsecret") || normalized.contains("secretname")
                || normalized.contains("secretref");
    }

    /** HelmValuesSuggestionService의 compactAndMaskYaml 처리에 필요한 업무 로직을 수행한다. */
    String compactAndMaskYaml(String valuesYaml) {
        if (valuesYaml == null || valuesYaml.isBlank()) return "{}";
        try {
            Object parsed = yamlMapper.readValue(valuesYaml, Object.class);
            if (!(parsed instanceof Map<?, ?>)) throw new IllegalArgumentException("Values root must be an object");
            maskSensitiveNode(parsed);
            return yamlMapper.writeValueAsString(parsed).trim();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Values YAML is invalid", exception);
        }
    }

    /** HelmValuesSuggestionService의 restoreSensitiveValues 처리에 필요한 업무 로직을 수행한다. */
    String restoreSensitiveValues(String originalYaml, String candidateYaml) {
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
            Object envName = map.get("name");
            if (envName != null && map.containsKey("value")
                    && SENSITIVE_KEY.matcher(String.valueOf(envName)).matches()) {
                ((Map<Object, Object>) map).put("value", "***REDACTED***");
            }
            ((Map<Object, Object>) map).replaceAll((key, child) -> {
                if (isSensitiveValueKey(key) && !(child instanceof Boolean) && !(child instanceof Number) && child != null) return "***REDACTED***";
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
            if (originalMap.get("name") != null && originalMap.get("name").equals(target.get("name"))
                    && SENSITIVE_KEY.matcher(String.valueOf(originalMap.get("name"))).matches()
                    && "***REDACTED***".equals(target.get("value"))) target.put("value", originalMap.get("value"));
            originalMap.forEach((key, value) -> {
                if (isSensitiveValueKey(key)) target.put(key, value);
                else restoreSensitiveNode(value, target.get(key));
            });
        } else if (original instanceof List<?> originalList && candidate instanceof List<?> candidateList) {
            for (int index = 0; index < candidateList.size(); index++) {
                Object child = candidateList.get(index);
                Object source = index < originalList.size() ? originalList.get(index) : null;
                if (child instanceof Map<?, ?> named && named.get("name") != null)
                    source = originalList.stream().filter(item -> item instanceof Map<?, ?> map
                            && named.get("name").equals(map.get("name"))).findFirst().orElse(null);
                restoreSensitiveNode(source, child);
            }
        }
    }

    /** HelmValuesSuggestionService의 isSensitiveValueKey 처리 조건의 충족 여부를 판단한다. */
    private boolean isSensitiveValueKey(Object key) {
        String value = String.valueOf(key);
        // 기존 Secret의 이름·내부 key 참조는 credential 본문이 아니므로 정확한 Values 작성을 위해 유지한다.
        if (isSecretReferenceKey(value)) return false;
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

    private record SanitizedCandidate(String valuesYaml, int removedPlaintextCount, int removedReferenceCount) { }
}
