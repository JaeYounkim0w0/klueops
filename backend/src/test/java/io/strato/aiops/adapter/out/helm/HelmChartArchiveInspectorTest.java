package io.strato.aiops.adapter.out.helm;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelmChartArchiveInspectorTest {
    private final HelmChartArchiveInspector inspector = new HelmChartArchiveInspector(1024 * 1024, 1024 * 1024, 20);

    /** HelmChartArchiveInspectorTest의 readsRootChartMetadataFromSafeArchive 처리 결과를 조회해 반환한다. */
    @Test
    void readsRootChartMetadataFromSafeArchive() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("sample/Chart.yaml", "name: sample\nversion: 1.2.3\nappVersion: 4.5.6\n");
        files.put("sample/values.yaml", "service:\n  type: ClusterIP\n");
        files.put("sample/values.schema.json", "{\"type\":\"object\"}");
        files.put("sample/templates/service.yaml", "nodePort: {{ .Values.service.nodePort }}");
        byte[] archive = archive(files);

        var result = inspector.inspect(archive);

        assertThat(result.name()).isEqualTo("sample");
        assertThat(result.version()).isEqualTo("1.2.3");
        assertThat(result.appVersion()).isEqualTo("4.5.6");
        assertThat(result.defaultValuesYaml()).contains("type: ClusterIP");
        assertThat(result.valuesSchemaJson()).contains("\"type\":\"object\"");
        assertThat(result.fileCount()).isEqualTo(4);
        assertThat(result.templateValuePaths()).contains("/service/nodePort");
    }

    /** HelmChartArchiveInspectorTest의 rejectsPathTraversalBeforeImport 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsPathTraversalBeforeImport() throws IOException {
        byte[] archive = archive("sample/../Chart.yaml", "name: sample\nversion: 1.0.0\n");

        assertThatThrownBy(() -> inspector.inspect(archive))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe path");
    }

    /** HelmChartArchiveInspectorTest의 rejectsTruncatedTarHeader 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsTruncatedTarHeader() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(new byte[200]);
        }

        assertThatThrownBy(() -> inspector.inspect(bytes.toByteArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Helm chart archive");
    }

    /** 동일 dependency의 복수 alias를 보존하고 내부 archive도 전체 파일 상한에 포함한다. */
    @Test void inspectsNestedAliasesWithinSharedBudget() throws IOException {
        byte[] child = archive(Map.of("database/Chart.yaml", "name: database\nversion: 1.0.0\n",
                "database/values.yaml", "service:\n  port: 5432\n"));
        byte[] parent = archiveBytes(Map.of(
                "sample/Chart.yaml", ("name: sample\nversion: 1.0.0\ndependencies:\n"
                        + "- name: database\n  alias: primary\n- name: database\n  alias: secondary\n").getBytes(StandardCharsets.UTF_8),
                "sample/values.yaml", "enabled: true\n".getBytes(StandardCharsets.UTF_8),
                "sample/charts/database.tgz", child));
        assertThat(inspector.inspect(parent).referenceFiles()).containsKeys("charts/primary/values.yaml", "charts/secondary/values.yaml");
        assertThatThrownBy(() -> new HelmChartArchiveInspector(1024 * 1024, 1024 * 1024, 3).inspect(parent))
                .hasMessageContaining("safety limit");
    }

    /** HelmChartArchiveInspectorTest의 archive 처리에 필요한 업무 로직을 수행한다. */
    private byte[] archive(String path, String content) throws IOException {
        return archive(Map.of(path, content));
    }

    /** HelmChartArchiveInspectorTest의 archive 처리에 필요한 업무 로직을 수행한다. */
    private byte[] archive(Map<String, String> files) throws IOException {
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        files.forEach((name, content) -> bytes.put(name, content.getBytes(StandardCharsets.UTF_8)));
        return archiveBytes(bytes);
    }

    /** binary 하위 Chart를 포함한 tar fixture를 구성한다. */
    private byte[] archiveBytes(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            byte[] payload = file.getValue();
            byte[] header = new byte[512];
            write(header, 0, 100, file.getKey());
            write(header, 100, 8, "0000644");
            write(header, 108, 8, "0000000");
            write(header, 116, 8, "0000000");
            write(header, 124, 12, String.format("%011o", payload.length));
            write(header, 136, 12, "00000000000");
            for (int index = 148; index < 156; index++) header[index] = ' ';
            header[156] = '0';
            write(header, 257, 6, "ustar");
            long checksum = 0;
            for (byte value : header) checksum += Byte.toUnsignedInt(value);
            write(header, 148, 8, String.format("%06o\0 ", checksum));
            tar.write(header);
            tar.write(payload);
            tar.write(new byte[(512 - payload.length % 512) % 512]);
        }
        tar.write(new byte[1024]);
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(zipped)) {
            gzip.write(tar.toByteArray());
        }
        return zipped.toByteArray();
    }

    /** HelmChartArchiveInspectorTest의 write 처리에 필요한 업무 로직을 수행한다. */
    private void write(byte[] target, int offset, int length, String value) {
        byte[] source = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(source, 0, target, offset, Math.min(source.length, length));
    }
}
