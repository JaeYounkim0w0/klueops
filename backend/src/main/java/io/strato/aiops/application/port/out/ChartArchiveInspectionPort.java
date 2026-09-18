package io.strato.aiops.application.port.out;

public interface ChartArchiveInspectionPort {
    /** ChartArchiveInspectionPort의 inspect 처리 계약을 정의한다. */
    InspectedArchive inspect(byte[] payload);

    record InspectedArchive(String name, String version, String appVersion, String description,
                            String chartYaml, String defaultValuesYaml, String valuesSchemaJson,
                            int fileCount, long expandedBytes, java.util.Set<String> templateValuePaths,
                            java.util.Map<String, String> referenceFiles) {
        public InspectedArchive(String name, String version, String appVersion, String description,
                                String chartYaml, String defaultValuesYaml, String valuesSchemaJson,
                                int fileCount, long expandedBytes, java.util.Set<String> templateValuePaths) {
            this(name, version, appVersion, description, chartYaml, defaultValuesYaml, valuesSchemaJson,
                    fileCount, expandedBytes, templateValuePaths, java.util.Map.of());
        }
        public InspectedArchive(String name, String version, String appVersion, String description,
                                String chartYaml, String defaultValuesYaml, String valuesSchemaJson,
                                int fileCount, long expandedBytes) {
            this(name, version, appVersion, description, chartYaml, defaultValuesYaml, valuesSchemaJson,
                    fileCount, expandedBytes, java.util.Set.of(), java.util.Map.of());
        }
    }
}
