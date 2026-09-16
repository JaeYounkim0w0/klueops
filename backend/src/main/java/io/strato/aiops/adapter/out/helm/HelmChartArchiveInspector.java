package io.strato.aiops.adapter.out.helm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.strato.aiops.application.port.out.ChartArchiveInspectionPort;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

@Component
public class HelmChartArchiveInspector implements ChartArchiveInspectionPort {
    private static final int TAR_BLOCK = 512;
    private static final int MAXIMUM_AI_REFERENCE_BYTES = 512 * 1024;
    private final long maximumCompressedBytes;
    private final long maximumExpandedBytes;
    private final int maximumFiles;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /** HelmChartArchiveInspector 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public HelmChartArchiveInspector(
            @Value("${aiops.application-delivery.maximum-chart-bytes:20971520}") long maximumCompressedBytes,
            @Value("${aiops.application-delivery.maximum-expanded-bytes:52428800}") long maximumExpandedBytes,
            @Value("${aiops.application-delivery.maximum-chart-files:2000}") int maximumFiles) {
        this.maximumCompressedBytes = maximumCompressedBytes;
        this.maximumExpandedBytes = maximumExpandedBytes;
        this.maximumFiles = maximumFiles;
    }

    /** HelmChartArchiveInspector의 inspect 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public InspectedArchive inspect(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > maximumCompressedBytes) {
            throw new IllegalArgumentException("Chart archive size is outside the allowed range");
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            byte[] header = new byte[TAR_BLOCK];
            long expanded = 0;
            int files = 0;
            String chartYaml = null;
            String defaultValuesYaml = null;
            String valuesSchemaJson = null;
            while (readBlock(gzip, header)) {
                if (allZero(header)) break;
                String name = text(header, 0, 100);
                validatePath(name);
                long size = octal(header, 124, 12);
                byte type = header[156];
                if (type == '1' || type == '2' || type == '3' || type == '4' || type == '6') {
                    throw new IllegalArgumentException("Chart archive links and device entries are not allowed");
                }
                files++;
                if (files > maximumFiles || expanded + size > maximumExpandedBytes) {
                    throw new IllegalArgumentException("Chart archive expands beyond the configured safety limit");
                }
                boolean rootFile = name.chars().filter(ch -> ch == '/').count() == 1;
                boolean metadata = rootFile && name.endsWith("/Chart.yaml");
                boolean defaultValues = rootFile && name.endsWith("/values.yaml");
                boolean valuesSchema = rootFile && name.endsWith("/values.schema.json");
                boolean reference = defaultValues || valuesSchema;
                // LLM 참조 자료는 Chart 전체가 아니라 bounded root Values/Schema만 수집한다.
                ByteArrayOutputStream capture = (metadata || (reference && size <= MAXIMUM_AI_REFERENCE_BYTES))
                        ? new ByteArrayOutputStream() : null;
                copyEntry(gzip, size, capture);
                expanded += size;
                skipPadding(gzip, size);
                if (capture != null) {
                    String content = capture.toString(StandardCharsets.UTF_8);
                    if (metadata) chartYaml = content;
                    else if (defaultValues) defaultValuesYaml = content;
                    else if (valuesSchema) valuesSchemaJson = content;
                }
            }
            if (chartYaml == null || chartYaml.isBlank()) {
                throw new IllegalArgumentException("Chart.yaml was not found at the chart root");
            }
            JsonNode metadata = yamlMapper.readTree(chartYaml);
            String name = requiredMetadata(metadata, "name");
            String version = requiredMetadata(metadata, "version");
            return new InspectedArchive(name, version, metadata.path("appVersion").asText(null),
                    metadata.path("description").asText(null), chartYaml, defaultValuesYaml, valuesSchemaJson,
                    files, expanded);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid Helm chart archive", exception);
        }
    }

    /** HelmChartArchiveInspector의 validatePath 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void validatePath(String name) {
        if (name.isBlank() || name.startsWith("/") || name.contains("../") || name.contains("\\")) {
            throw new IllegalArgumentException("Chart archive contains an unsafe path");
        }
    }

    /** HelmChartArchiveInspector의 copyEntry 처리에 필요한 업무 로직을 수행한다. */
    private void copyEntry(GZIPInputStream input, long size, ByteArrayOutputStream capture) throws IOException {
        byte[] buffer = new byte[8192];
        long remaining = size;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) throw new IOException("Unexpected end of tar entry");
            if (capture != null) capture.write(buffer, 0, read);
            remaining -= read;
        }
    }

    /** HelmChartArchiveInspector의 skipPadding 처리에 필요한 업무 로직을 수행한다. */
    private void skipPadding(GZIPInputStream input, long size) throws IOException {
        long padding = (TAR_BLOCK - size % TAR_BLOCK) % TAR_BLOCK;
        while (padding > 0) {
            long skipped = input.skip(padding);
            if (skipped <= 0 && input.read() < 0) throw new IOException("Unexpected end of tar padding");
            padding -= Math.max(1, skipped);
        }
    }

    /** HelmChartArchiveInspector의 readBlock 처리 결과를 조회해 반환한다. */
    private boolean readBlock(GZIPInputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = input.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                if (offset == 0) return false;
                throw new IOException("Unexpected end of tar header");
            }
            offset += read;
        }
        return true;
    }

    /** HelmChartArchiveInspector의 allZero 처리에 필요한 업무 로직을 수행한다. */
    private boolean allZero(byte[] value) {
        for (byte item : value) if (item != 0) return false;
        return true;
    }

    /** HelmChartArchiveInspector의 text 처리에 필요한 업무 로직을 수행한다. */
    private String text(byte[] value, int offset, int length) {
        int end = offset;
        while (end < offset + length && value[end] != 0) end++;
        return new String(value, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    /** HelmChartArchiveInspector의 octal 처리에 필요한 업무 로직을 수행한다. */
    private long octal(byte[] value, int offset, int length) {
        String raw = text(value, offset, length).replace("\u0000", "").trim();
        return raw.isEmpty() ? 0 : Long.parseLong(raw, 8);
    }

    /** HelmChartArchiveInspector의 requiredMetadata 처리 입력과 현재 상태의 유효성을 검증한다. */
    private String requiredMetadata(JsonNode metadata, String key) {
        String value = metadata.path(key).asText();
        if (value.isBlank()) throw new IllegalArgumentException("Chart.yaml " + key + " is required");
        return value;
    }

}
