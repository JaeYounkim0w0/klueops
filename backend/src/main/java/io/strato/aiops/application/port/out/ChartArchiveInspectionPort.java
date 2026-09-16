package io.strato.aiops.application.port.out;

public interface ChartArchiveInspectionPort {
    /** ChartArchiveInspectionPort의 inspect 처리 계약을 정의한다. */
    InspectedArchive inspect(byte[] payload);

    record InspectedArchive(String name, String version, String appVersion, String description,
                            String chartYaml, String defaultValuesYaml, String valuesSchemaJson,
                            int fileCount, long expandedBytes) { }
}
