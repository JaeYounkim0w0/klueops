package io.strato.aiops.adapter.out.helm;

import io.strato.aiops.application.port.out.ChartAcquisitionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessBuilderChartAcquisitionAdapter implements ChartAcquisitionPort {
    private static final int MAXIMUM_PROCESS_OUTPUT = 64 * 1024;
    private final String helmPath;
    private final long maximumChartBytes;
    private final RemoteChartSourceValidator sourceValidator;

    public ProcessBuilderChartAcquisitionAdapter(
            @Value("${aiops.application-delivery.helm-path:helm}") String helmPath,
            @Value("${aiops.application-delivery.maximum-chart-bytes:20971520}") long maximumChartBytes,
            RemoteChartSourceValidator sourceValidator) {
        this.helmPath = helmPath;
        this.maximumChartBytes = maximumChartBytes;
        this.sourceValidator = sourceValidator;
    }

    @Override
    public byte[] fetch(FetchRequest request) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("klueops-chart-");
            List<String> command = command(request, directory);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> drain(process.getInputStream(), output), "helm-pull-output");
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(Math.max(1, request.timeout().toSeconds()), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Helm chart download timed out");
            }
            reader.join(1000);
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Helm chart download failed: " + output.toString(StandardCharsets.UTF_8));
            }
            Path archive = Files.list(directory).filter(path -> path.getFileName().toString().endsWith(".tgz"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Helm did not produce a chart archive"));
            long size = Files.size(archive);
            if (size <= 0 || size > maximumChartBytes) {
                throw new IllegalArgumentException("Downloaded chart exceeds the configured size limit");
            }
            return Files.readAllBytes(archive);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Helm chart download was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Helm chart download failed", exception);
        } finally {
            deleteDirectory(directory);
        }
    }

    private List<String> command(FetchRequest request, Path directory) {
        List<String> command = new ArrayList<>();
        command.add(helmPath);
        command.add("pull");
        if (request.contentUrl() != null && request.contentUrl().startsWith("oci://")) {
            String reference = request.contentUrl().replaceFirst(":" + java.util.regex.Pattern.quote(request.version()) + "$", "");
            command.add(reference);
        } else {
            requireRemoteSource(request.repositoryUrl());
            command.add(request.packageName());
            command.add("--repo");
            command.add(request.repositoryUrl());
        }
        command.add("--version");
        command.add(request.version());
        command.add("--destination");
        command.add(directory.toString());
        return List.copyOf(command);
    }

    private void requireRemoteSource(String value) {
        sourceValidator.requirePublicHttps(value);
    }

    private void drain(InputStream input, ByteArrayOutputStream output) {
        try (input) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                int allowed = Math.min(read, Math.max(0, MAXIMUM_PROCESS_OUTPUT - output.size()));
                if (allowed > 0) output.write(buffer, 0, allowed);
            }
        } catch (IOException ignored) {
            // Process 종료 중 stream이 닫히는 경우 결과 상태가 최종 오류를 결정한다.
        }
    }

    private void deleteDirectory(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
            // 임시 디렉터리 정리 실패는 원래 Helm 결과를 덮지 않는다.
        }
    }
}
