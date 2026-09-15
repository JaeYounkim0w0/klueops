package io.strato.aiops.adapter.out.helm;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelmChartArchiveInspectorTest {
    private final HelmChartArchiveInspector inspector = new HelmChartArchiveInspector(1024 * 1024, 1024 * 1024, 20);

    @Test
    void readsRootChartMetadataFromSafeArchive() throws IOException {
        byte[] archive = archive("sample/Chart.yaml", "name: sample\nversion: 1.2.3\nappVersion: 4.5.6\n");

        var result = inspector.inspect(archive);

        assertThat(result.name()).isEqualTo("sample");
        assertThat(result.version()).isEqualTo("1.2.3");
        assertThat(result.appVersion()).isEqualTo("4.5.6");
        assertThat(result.fileCount()).isEqualTo(1);
    }

    @Test
    void rejectsPathTraversalBeforeImport() throws IOException {
        byte[] archive = archive("sample/../Chart.yaml", "name: sample\nversion: 1.0.0\n");

        assertThatThrownBy(() -> inspector.inspect(archive))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe path");
    }

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

    private byte[] archive(String path, String content) throws IOException {
        byte[] payload = content.getBytes(StandardCharsets.UTF_8);
        byte[] header = new byte[512];
        write(header, 0, 100, path);
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

        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        tar.write(header);
        tar.write(payload);
        tar.write(new byte[(512 - payload.length % 512) % 512]);
        tar.write(new byte[1024]);
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(zipped)) {
            gzip.write(tar.toByteArray());
        }
        return zipped.toByteArray();
    }

    private void write(byte[] target, int offset, int length, String value) {
        byte[] source = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(source, 0, target, offset, Math.min(source.length, length));
    }
}
