package io.strato.aiops.application.service;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.*;
import java.io.StringReader;
import java.util.*;

/** 원문을 분할해도 각 설정의 실제 상위 경로를 잃지 않도록 YAML 구조를 색인한다. */
final class ValuesReferencePaths {
    private ValuesReferencePaths() { }

    /** dependency 파일은 alias를 포함한 루트 Values 경로로 변환한다. */
    static Map<Integer, List<String>> index(String source, String text) {
        if (!source.endsWith("values.yaml")) return Map.of();
        String prefix = "";
        if (source.startsWith("charts/")) {
            String[] parts = source.split("/");
            for (int i = 0; i + 1 < parts.length; i++)
                if (parts[i].equals("charts")) prefix += "/" + escape(parts[++i]);
        }
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(1024 * 1024);
        options.setNestingDepthLimit(64);
        options.setMaxAliasesForCollections(20);
        Map<Integer, List<String>> result = new TreeMap<>();
        collect(new Yaml(options).compose(new StringReader(text)), prefix, result,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        return result;
    }

    /** 배열은 교체 단위로, mapping은 정확한 leaf 경로로 기록한다. */
    private static void collect(Node node, String path, Map<Integer, List<String>> result, Set<Node> active) {
        if (node == null || !active.add(node)) return;
        if (node instanceof MappingNode map && !map.getValue().isEmpty()) {
            for (NodeTuple tuple : map.getValue()) {
                if (tuple.getKeyNode() instanceof ScalarNode key)
                    collect(tuple.getValueNode(), path + "/" + escape(key.getValue()), result, active);
            }
        } else result.computeIfAbsent(node.getStartMark().getLine() + 1, ignored -> new ArrayList<>()).add(path);
        active.remove(node);
    }

    /** JSON Pointer 예약 문자를 이스케이프한다. */
    private static String escape(String key) { return key.replace("~", "~0").replace("/", "~1"); }
}
