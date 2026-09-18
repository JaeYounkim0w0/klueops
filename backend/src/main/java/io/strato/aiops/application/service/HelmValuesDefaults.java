package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.out.ChartArchiveInspectionPort.InspectedArchive;

/** 하위 Chart의 설정 경로를 상위 Chart의 override와 함께 확인한다. */
final class HelmValuesDefaults {
    private HelmValuesDefaults() { }

    /** 부모 override가 우선하도록 dependency alias 경로 아래에 기본값을 보완한다. */
    static JsonNode combined(InspectedArchive archive) {
        ObjectNode root = (ObjectNode) ChartValuesEligibility.requireUsable(archive.defaultValuesYaml());
        archive.referenceFiles().entrySet().stream().filter(entry -> entry.getKey().matches("(?:charts/[^/]+/)+values\\.yaml"))
                .sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
                    String[] parts = entry.getKey().split("/");
                    ObjectNode cursor = root;
                    for (int i = 1; i < parts.length - 1; i += 2) {
                        if (!cursor.has(parts[i])) cursor.putObject(parts[i]);
                        if (!(cursor.get(parts[i]) instanceof ObjectNode child)) return;
                        cursor = child;
                    }
                    fill(cursor, ChartValuesEligibility.parseDependency(entry.getValue()));
                });
        return root;
    }

    /** 이미 존재하는 부모 설정은 변경하지 않는다. */
    private static void fill(ObjectNode target, JsonNode defaults) {
        defaults.fields().forEachRemaining(entry -> {
            if (!target.has(entry.getKey())) target.set(entry.getKey(), entry.getValue());
            else if (target.get(entry.getKey()) instanceof ObjectNode object && entry.getValue().isObject()) fill(object, entry.getValue());
        });
    }
}
