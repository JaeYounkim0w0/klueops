package io.strato.aiops.adapter.out.helm;

import io.strato.aiops.application.port.out.ChartAcquisitionPort;
import io.strato.aiops.domain.applicationdelivery.ChartTrustStatus;
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
    private final String provenanceKeyring;

    /** ProcessBuilderChartAcquisitionAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ProcessBuilderChartAcquisitionAdapter(
            @Value("${aiops.application-delivery.helm-path:helm}") String helmPath,
            @Value("${aiops.application-delivery.maximum-chart-bytes:20971520}") long maximumChartBytes,
            @Value("${aiops.application-delivery.provenance-keyring:}") String provenanceKeyring,
            RemoteChartSourceValidator sourceValidator) {
        this.helmPath = helmPath;
        this.maximumChartBytes = maximumChartBytes;
        this.sourceValidator = sourceValidator;
        this.provenanceKeyring = provenanceKeyring == null ? "" : provenanceKeyring.trim();
    }

    /** ProcessBuilderChartAcquisitionAdapter의 fetch 처리 결과를 조회해 반환한다. */
    @Override
    public byte[] fetch(FetchRequest request) {
        return fetchVerified(request).payload();
    }

    /** 키링이 설정되면 Helm provenance를 검증하고 결과 신뢰 수준을 함께 반환한다. */
    @Override
    public VerifiedChart fetchVerified(FetchRequest request) {
        if (provenanceKeyring.isBlank()) {
            return new VerifiedChart(download(request, false), ChartTrustStatus.CHECKSUMMED,
                    "Provenance keyring is not configured; SHA-256 checksum was recorded");
        }
        try {
            return new VerifiedChart(download(request, true), ChartTrustStatus.VERIFIED,
                    "Helm provenance signature verified");
        } catch (MissingProvenanceException exception) {
            return new VerifiedChart(download(request, false), ChartTrustStatus.CHECKSUMMED,
                    "Chart has no provenance file; SHA-256 checksum was recorded");
        }
    }

    /** 임시 디렉터리에서 Helm pull을 실행하고 크기 제한을 통과한 아카이브만 읽는다. */
    private byte[] download(FetchRequest request, boolean verify) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("klueops-chart-");
            List<String> command = command(request, directory, verify);
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
                String safeOutput = bounded(output.toString(StandardCharsets.UTF_8));
                if (verify && missingProvenance(safeOutput)) throw new MissingProvenanceException();
                throw new IllegalStateException(verify ? "Helm provenance verification failed" :
                        "Helm chart download failed: " + safeOutput);
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

    /** ProcessBuilderChartAcquisitionAdapter의 command 처리에 필요한 업무 로직을 수행한다. */
    private List<String> command(FetchRequest request, Path directory, boolean verify) {
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
        if (verify) {
            command.add("--verify");
            command.add("--keyring");
            command.add(provenanceKeyring);
        }
        return List.copyOf(command);
    }

    /** Helm 오류 중 서명 위조와 provenance 미제공을 구분한다. */
    private boolean missingProvenance(String output) {
        String normalized = output.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("provenance") && (normalized.contains("not found")
                || normalized.contains("no such file") || normalized.contains("404"));
    }

    /** 프로세스 출력이 API 오류에 과도하게 노출되지 않도록 길이를 제한한다. */
    private String bounded(String value) {
        String normalized = value == null ? "" : value.replaceAll("(?i)(token|password|secret)=\\S+", "$1=***");
        return normalized.length() <= 2000 ? normalized : normalized.substring(0, 2000);
    }

    /** ProcessBuilderChartAcquisitionAdapter의 requireRemoteSource 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireRemoteSource(String value) {
        sourceValidator.requirePublicHttps(value);
    }

    /** ProcessBuilderChartAcquisitionAdapter의 drain 처리에 필요한 업무 로직을 수행한다. */
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

    /** ProcessBuilderChartAcquisitionAdapter의 deleteDirectory 처리 대상과 관련 상태를 안전하게 정리한다. */
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

    private static final class MissingProvenanceException extends RuntimeException { }
}
