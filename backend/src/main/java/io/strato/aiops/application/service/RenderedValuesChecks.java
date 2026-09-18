package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.*;

/** 제공사별 Values 키 대신 렌더된 Kubernetes 리소스에서 요청 결과를 확인한다. */
final class RenderedValuesChecks {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private RenderedValuesChecks() { }

    /** 조건은 서버에서 비교하며 검사하지 않은 요구사항은 경고로 남긴다. */
    static List<String> verify(String manifest, JsonNode checks, List<HelmValuesAssistanceService.Requirement> requirements) {
        List<JsonNode> resources = new ArrayList<>();
        try (com.fasterxml.jackson.databind.MappingIterator<JsonNode> iterator = YAML.readerFor(JsonNode.class).readValues(manifest)) {
            while (iterator.hasNext()) resources.add(iterator.next());
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("렌더된 Manifest를 읽을 수 없습니다.");
        }
        if (!checks.isArray() || checks.size() > 80) throw new IllegalArgumentException("Invalid checks array");
        Set<String> covered = new HashSet<>();
        for (JsonNode check : checks) {
            String id = check.path("requirementId").asText();
            if (requirements.stream().noneMatch(item -> item.id().equals(id))) throw new IllegalArgumentException("Unknown requirement check");
            String kind = check.path("kind").asText(), name = check.path("name").asText();
            String path = check.path("path").asText();
            if (kind.isBlank() || name.isBlank() || !path.startsWith("/spec/") || path.length() > 512 || !check.has("value"))
                throw new HelmValuesValidationFailure("Invalid rendered resource check",
                        "Every checks entry must have requirementId, Kubernetes kind, name (exact or *), path starting /spec/, and value. "
                        + "Checks target Kubernetes manifests, NOT Helm Values paths. Arrays use /*/ or /0/."
                        + inventory(resources, kind));
            List<JsonNode> matched = resources.stream().filter(resource -> kind.equals(resource.path("kind").asText())
                    && (name.equals("*") || name.equals(resource.path("metadata").path("name").asText()))).toList();
            // wildcard 조건은 같은 kind의 모든 조건이 동시에 성립하는 단일 리소스에만 연결한다.
            if (name.equals("*")) {
                matched = matched.stream().filter(resource -> {
                    for (JsonNode related : checks) {
                        if (kind.equals(related.path("kind").asText()) && "*".equals(related.path("name").asText())) {
                            String relatedPath = related.path("path").asText();
                            if (!relatedPath.startsWith("/spec/") || !related.has("value")
                                    || !matches(resource, relatedPath.substring(1).split("/"), 0, related.get("value"))) return false;
                        }
                    }
                    return true;
                }).toList();
                if (matched.size() > 1)
                    throw new HelmValuesValidationFailure("Ambiguous rendered check", "Select an exact rendered resource name for " + id + inventory(resources, kind));
            }
            if (matched.stream().noneMatch(resource -> matches(resource, path.substring(1).split("/"), 0, check.get("value"))))
                throw new HelmValuesValidationFailure("Rendered requirement not satisfied", "Rendered check failed for " + id
                        + ": kind=" + kind + ", path=" + path + ". Fix Values and prerequisites using Chart evidence." + inventory(resources, kind));
            covered.add(id);
        }
        List<String> warnings = new ArrayList<>();
        for (var requirement : requirements) {
            if (!"PRESERVE".equals(requirement.kind()) && !covered.contains(requirement.id()))
                warnings.add("요청 결과의 자동 확인이 필요합니다: " + requirement.request());
        }
        warnings.add("검증 범위: Helm 렌더와 제시된 리소스 조건. 대상 Cluster의 배포·실제 접속은 별도 확인이 필요합니다.");
        return warnings;
    }

    /** 배열의 포트·컨테이너 항목을 포함한 JSON Pointer 값을 정확한 타입으로 비교한다. */
    private static boolean matches(JsonNode node, String[] path, int index, JsonNode expected) {
        if (index == path.length) return node.equals(expected);
        String key = path[index].replace("~1", "/").replace("~0", "~");
        if (key.equals("*") && node.isArray()) {
            for (JsonNode child : node) if (matches(child, path, index + 1, expected)) return true;
            return false;
        }
        if (node.isArray() && key.matches("[0-9]{1,6}"))
            return matches(node.path(Integer.parseInt(key)), path, index + 1, expected);
        return matches(node.path(key), path, index + 1, expected);
    }

    /** 리소스 본문이나 Secret 값 없이 정확한 렌더 대상 식별자만 교정 근거로 제공한다. */
    private static String inventory(List<JsonNode> resources, String kind) {
        return " Rendered identities and available spec paths (bounded; no field values): " + resources.stream()
                .filter(resource -> kind.equals(resource.path("kind").asText()))
                .filter(resource -> resource.path("metadata").path("name").asText().matches("[a-zA-Z0-9_.-]{1,253}"))
                .limit(8).map(resource -> {
                    List<String> paths = new ArrayList<>();
                    collectPaths(resource.path("spec"), "/spec", paths);
                    return resource.path("metadata").path("name").asText() + " " + paths;
                }).collect(java.util.stream.Collectors.joining("; "));
    }

    /** 검증 실패를 보정할 때 실제 생성된 필드 경로만 제한적으로 안내한다. */
    private static void collectPaths(JsonNode node, String path, List<String> paths) {
        if (paths.size() >= 40 || path.length() > 180) return;
        if (node.isObject()) node.fields().forEachRemaining(entry -> {
            if (!entry.getKey().matches("(?i).*(secret|env|annotation|data|token|password|certificate).*"))
                collectPaths(entry.getValue(), path + "/" + entry.getKey().replace("~", "~0").replace("/", "~1"), paths);
        });
        else if (node.isArray()) { if (!node.isEmpty()) collectPaths(node.get(0), path + "/*", paths); }
        else paths.add(path);
    }
}
