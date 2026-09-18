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
        return inspect(payload, new Budget(), 0);
    }

    /** 하위 Chart도 동일한 파일·해제 크기 예산 안에서 확인한다. */
    private InspectedArchive inspect(byte[] payload, Budget budget, int depth) {
        if (depth > 8) throw new IllegalArgumentException("Chart dependency nesting exceeds the supported limit");
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
            java.util.Set<String> templatePaths = new java.util.LinkedHashSet<>();
            java.util.Map<String, String> references = new java.util.LinkedHashMap<>();
            java.util.List<InspectedArchive> children = new java.util.ArrayList<>();
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
                budget.files++;
                budget.bytes += size;
                if (size < 0 || budget.files > maximumFiles || budget.bytes > maximumExpandedBytes) {
                    throw new IllegalArgumentException("Chart archive expands beyond the configured safety limit");
                }
                boolean rootFile = name.chars().filter(ch -> ch == '/').count() == 1;
                boolean metadata = rootFile && name.endsWith("/Chart.yaml");
                boolean defaultValues = rootFile && name.endsWith("/values.yaml");
                boolean valuesSchema = rootFile && name.endsWith("/values.schema.json");
                boolean reference = defaultValues || valuesSchema;
                boolean template = name.matches("[^/]+/templates/.+");
                boolean documentation = rootFile && name.endsWith("/README.md");
                boolean childArchive = name.matches("[^/]+/charts/[^/]+\\.tgz");
                boolean unpackedValues = name.matches("[^/]+/charts/[^/]+/values\\.yaml");
                // 큰 참조를 누락된 파일로 오인하지 않도록 별도 오류로 구분한다.
                if (reference && size > MAXIMUM_AI_REFERENCE_BYTES)
                    throw new IllegalArgumentException("Chart Values/Schema exceeds the supported reference size (512 KiB)");
                ByteArrayOutputStream capture = (metadata || childArchive || ((reference || template || documentation || unpackedValues) && size <= MAXIMUM_AI_REFERENCE_BYTES))
                        ? new ByteArrayOutputStream() : null;
                copyEntry(gzip, size, capture);
                expanded += size;
                skipPadding(gzip, size);
                if (capture != null) {
                    if (childArchive) {
                        children.add(inspect(capture.toByteArray(), budget, depth + 1));
                        continue;
                    }
                    String content = capture.toString(StandardCharsets.UTF_8);
                    if (metadata) chartYaml = content;
                    else if (defaultValues) defaultValuesYaml = content;
                    else if (valuesSchema) valuesSchemaJson = content;
                    else if (template) collectTemplatePaths(content, templatePaths);
                    if (reference || documentation || template || unpackedValues)
                        references.put(name.substring(name.indexOf('/') + 1), content);
                }
            }
            if (chartYaml == null || chartYaml.isBlank()) {
                throw new IllegalArgumentException("Chart.yaml was not found at the chart root");
            }
            JsonNode metadata = yamlMapper.readTree(chartYaml);
            String name = requiredMetadata(metadata, "name");
            String version = requiredMetadata(metadata, "version");
            for (InspectedArchive child : children) {
                java.util.Set<String> aliases = new java.util.LinkedHashSet<>();
                for (JsonNode dependency : metadata.path("dependencies")) {
                    if (child.name().equals(dependency.path("name").asText())) {
                        aliases.add(dependency.path("alias").asText(child.name()));
                    }
                }
                if (aliases.isEmpty()) aliases.add(child.name());
                // 같은 dependency를 여러 alias로 사용하는 Chart도 각각의 Values 경로를 보존한다.
                for (String alias : aliases) {
                    String prefix = "charts/" + alias + "/";
                    if (child.defaultValuesYaml() != null) references.put(prefix + "values.yaml", child.defaultValuesYaml());
                    child.referenceFiles().forEach((path, content) -> references.put(prefix + path, content));
                    for (String path : child.templateValuePaths()) templatePaths.add("/" + alias + path);
                }
            }
            return new InspectedArchive(name, version, metadata.path("appVersion").asText(null),
                    metadata.path("description").asText(null), chartYaml, defaultValuesYaml, valuesSchemaJson,
                    files, expanded, java.util.Set.copyOf(templatePaths), java.util.Map.copyOf(references));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid Helm chart archive", exception);
        }
    }

    /** 중첩 tgz 전체에서 공유하는 해제 상한이다. */
    private static final class Budget { long bytes; int files; }

    /** 템플릿 본문 대신 정적으로 확인되는 루트 Values 경로만 제한된 개수로 추출한다. */
    private void collectTemplatePaths(String content, java.util.Set<String> paths) {
        var matcher = java.util.regex.Pattern.compile("\\.Values((?:\\.[A-Za-z_][A-Za-z0-9_-]*)+)").matcher(content);
        while (matcher.find() && paths.size() < 4000) paths.add(matcher.group(1).replace('.', '/'));
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
