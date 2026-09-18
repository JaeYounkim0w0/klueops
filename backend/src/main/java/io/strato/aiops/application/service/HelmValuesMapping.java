package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 실제 Chart의 경로 근거와 모델의 변경 계획을 비교하고 기존 Values에 합성한다. */
public final class HelmValuesMapping {
    private final Set<String> supported = new LinkedHashSet<>();
    private final Set<String> openObjects = new LinkedHashSet<>();
    private final ObjectMapper mapper;

    /** 기본 Values, Schema 및 정적 템플릿 경로를 동일 JSON Pointer 집합으로 구성한다. */
    HelmValuesMapping(ObjectMapper mapper, JsonNode defaults, JsonNode schema, Set<String> templatePaths) {
        this.mapper = mapper;
        collect(defaults, "", false);
        collect(schema, "", true);
        supported.addAll(templatePaths);
    }

    /** 수동 Values에도 동일한 경로 검증을 적용하며 object 안의 미지원 키를 빠뜨리지 않는다. */
    void requireSupported(JsonNode values) { requireSupported(values, ""); }

    /** 기본값이 아닌 Schema·템플릿 전용 경로도 허용하되 부모 object만으로 자식 키를 승인하지 않는다. */
    private void requireSupported(JsonNode node, String path) {
        if (!path.isEmpty() && !supported.contains(path)
                && supported.stream().noneMatch(known -> known.startsWith(path + "/"))
                && openObjects.stream().noneMatch(open -> path.startsWith(open + "/")))
            throw new HelmValuesValidationFailure("Unsupported Values path", "Unknown Chart path: " + path + ". Read the relevant reference and use its exact nesting.");
        if (node.isObject()) node.fields().forEachRemaining(entry -> requireSupported(entry.getValue(),
                path + "/" + entry.getKey().replace("~", "~0").replace("/", "~1")));
    }

    /** 기본값과 Schema properties를 순회하고 배열은 통째로 변경하는 항목으로 취급한다. */
    private void collect(JsonNode node, String path, boolean schema) {
        if (node == null || path.length() > 1024) return;
        JsonNode fields = schema ? node.path("properties") : node;
        if (!fields.isObject()) return;
        fields.fields().forEachRemaining(entry -> {
            String child = path + "/" + entry.getKey().replace("~", "~0").replace("/", "~1");
            supported.add(child);
            // 비어 있는 Values mapping과 명시적 additionalProperties는 사용자 정의 하위 키를 받는다.
            if ((!schema && entry.getValue().isObject() && entry.getValue().isEmpty())
                    || (schema && entry.getValue().path("additionalProperties").asBoolean(false)))
                openObjects.add(child);
            collect(entry.getValue(), child, schema);
        });
    }

    /** 경로 중복·상하위 충돌·미확인 경로를 거부하고 현재 설정을 복사한 뒤 변경만 적용한다. */
    ObjectNode apply(JsonNode current, List<Change> changes) {
        if (!current.isObject()) throw new IllegalArgumentException("현재 Values는 YAML object여야 합니다.");
        requireKnownChanges(changes);
        ObjectNode result = current.deepCopy();
        List<String> applied = new ArrayList<>();
        for (Change change : changes) {
            String path = change.path();
            if (!path.startsWith("/") || change.value() == null)
                throw new IllegalArgumentException("Values 변경 경로와 값이 필요합니다.");
            if (applied.stream().anyMatch(previous -> path.equals(previous) || path.startsWith(previous + "/")
                    || previous.startsWith(path + "/")))
                throw new IllegalArgumentException("서로 중복되거나 충돌하는 설정 경로입니다: " + path);
            applied.add(path);
            String[] segments = path.substring(1).split("/", -1);
            ObjectNode cursor = result;
            for (int i = 0; i < segments.length - 1; i++) {
                String key = decode(segments[i]);
                JsonNode child = cursor.get(key);
                if (child != null && !child.isObject())
                    throw new IllegalArgumentException("기존 설정의 타입과 충돌합니다: " + path);
                if (child == null) cursor.set(key, mapper.createObjectNode());
                cursor = (ObjectNode) cursor.get(key);
            }
            String key = decode(segments[segments.length - 1]);
            cursor.set(key, merge(cursor.get(key), change.value()));
        }
        requireSupported(result);
        return result;
    }

    /** 복합 요청의 경로 오류를 한 번에 교정하도록 실제 Chart의 동일 필드 후보만 제시한다. */
    private void requireKnownChanges(List<Change> changes) {
        List<String> unknown = changes.stream().map(Change::path)
                .filter(path -> !supported.contains(path) && openObjects.stream().noneMatch(prefix -> path.startsWith(prefix + "/")))
                .distinct().toList();
        if (unknown.isEmpty()) return;
        StringBuilder feedback = new StringBuilder("Unknown Chart paths. Rebuild all changes using exact root-relative paths:\n");
        for (String path : unknown) {
            String leaf = path.substring(path.lastIndexOf('/') + 1);
            String candidates = supported.stream().filter(known -> known.endsWith("/" + leaf))
                    .sorted(java.util.Comparator.comparingLong((String known) -> known.chars().filter(ch -> ch == '/').count())
                            .thenComparing(java.util.Comparator.naturalOrder()))
                    .limit(20).collect(java.util.stream.Collectors.joining(", "));
            String line = path + " -> existing fields with the same name: " + candidates + "\n";
            if (feedback.length() + line.length() > 6000) break;
            feedback.append(line);
        }
        feedback.append("Candidates are evidence, not automatic replacements. Read references if the intended component is unclear.");
        // 사용자가 어떤 요청 항목을 다시 확인해야 하는지 알 수 있도록 경로만 오류에 포함한다.
        String paths = unknown.stream().limit(8).collect(java.util.stream.Collectors.joining(", "));
        throw new HelmValuesValidationFailure("Chart에서 확인되지 않은 설정 경로가 있습니다: " + paths, feedback.toString());
    }

    /** 모델이 object 단위로 제안해도 명시하지 않은 기존 하위 설정은 보존한다. */
    private JsonNode merge(JsonNode current, JsonNode patch) {
        if (current == null || !current.isObject() || !patch.isObject()) return patch.deepCopy();
        ObjectNode result = current.deepCopy();
        patch.fields().forEachRemaining(entry -> result.set(entry.getKey(), merge(result.get(entry.getKey()), entry.getValue())));
        return result;
    }

    /** JSON Pointer escaping을 필드 이름으로 복원한다. */
    private String decode(String value) { return value.replace("~1", "/").replace("~0", "~"); }

    public record Change(String requirementId, String path, JsonNode value, String explanation) { }
}
