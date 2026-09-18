package io.strato.aiops.application.service;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.*;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Pattern;

/** 기본 Values의 주석·설명을 유지하면서 민감값 위치만 가리는 참조 전용 처리이다. */
final class ValuesReferenceText {
    private static final Pattern SENSITIVE = Pattern.compile("(?i).*(password|passwd|secret|token|api.?key|access.?key|private.?key|certificate|credential).*");

    private ValuesReferenceText() { }

    /** YAML 노드의 원문 위치를 사용하여 다중 행 및 환경변수 credential도 제거한다. */
    static String mask(String source) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(1024 * 1024);
        options.setNestingDepthLimit(64);
        options.setMaxAliasesForCollections(20);
        List<int[]> ranges = new ArrayList<>();
        Node node = new Yaml(options).compose(new StringReader(source));
        collect(node, ranges, Collections.newSetFromMap(new IdentityHashMap<>()));
        StringBuilder result = new StringBuilder(source);
        ranges.sort(Comparator.comparingInt((int[] span) -> span[0]).reversed());
        int previous = source.length() + 1;
        for (int[] range : ranges) {
            int start = source.offsetByCodePoints(0, range[0]), end = source.offsetByCodePoints(0, range[1]);
            if (end > previous) continue;
            // block mapping은 다음 key의 들여쓰기까지 span에 포함하므로 끝 공백·개행을 모두 보존한다.
            var trailing = Pattern.compile("[\\t \\r\\n]*$").matcher(source.substring(start, end));
            String suffix = trailing.find() ? trailing.group() : "";
            result.replace(start, end, "\"***REDACTED***\"" + suffix);
            previous = start;
        }
        // 주석의 credential 예시도 모델 입력에 남기지 않는다.
        return result.toString().replaceAll("(?im)(#.*?(?:password|passwd|token|api[_-]?key)\\s*[:=]\\s*)[^\\r\\n]+", "$1[protected]");
    }

    /** 민감 노드는 자식 순회 대신 전체 값을 보호하고 참조명과 boolean 설정은 유지한다. */
    private static void collect(Node node, List<int[]> ranges, Set<Node> visited) {
        if (node == null || !visited.add(node)) return;
        if (node instanceof MappingNode map) {
            boolean secretEnv = map.getValue().stream().anyMatch(tuple -> scalar(tuple.getKeyNode()).equals("name")
                    && SENSITIVE.matcher(scalar(tuple.getValueNode())).matches());
            for (NodeTuple tuple : map.getValue()) {
                String key = scalar(tuple.getKeyNode());
                Node value = tuple.getValueNode();
                String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
                boolean reference = normalized.contains("existingsecret") || normalized.contains("secretref")
                        || normalized.contains("secretname");
                boolean sensitive = !reference && SENSITIVE.matcher(key).matches();
                if ((sensitive || secretEnv && key.equals("value")) && !Tag.BOOL.equals(value.getTag())
                        && !Tag.NULL.equals(value.getTag()) && !scalar(value).isEmpty()) {
                    ranges.add(new int[]{value.getStartMark().getIndex(), value.getEndMark().getIndex()});
                } else if (sensitive && (value instanceof MappingNode || value instanceof SequenceNode)) {
                    ranges.add(new int[]{value.getStartMark().getIndex(), value.getEndMark().getIndex()});
                } else collect(value, ranges, visited);
            }
        } else if (node instanceof SequenceNode list) {
            list.getValue().forEach(item -> collect(item, ranges, visited));
        }
    }

    /** YAML scalar에서 키나 환경변수 이름을 읽는다. */
    private static String scalar(Node node) { return node instanceof ScalarNode scalar ? scalar.getValue() : ""; }
}
