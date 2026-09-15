package io.strato.aiops.application.port.out;

public interface ChartArchiveInspectionPort {
    InspectedArchive inspect(byte[] payload);

    record InspectedArchive(String name, String version, String appVersion, String description,
                            String chartYaml, int fileCount, long expandedBytes) { }
}
