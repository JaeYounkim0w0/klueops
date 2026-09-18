package io.strato.aiops.adapter.out.helm;

import io.strato.aiops.application.port.out.ChartAcquisitionPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
class ProcessBuilderChartAcquisitionAdapterTest {
    @TempDir
    Path directory;

    /** HTTPS Repository 다운로드가 쓰기 가능한 요청별 Helm 환경에서 실행되는지 검증한다. */
    @Test
    void isolatesHelmEnvironmentForRepositoryDownload() throws Exception {
        Path log = directory.resolve("helm-cache-paths.log");
        Path executable = fakeHelm(log);
        ProcessBuilderChartAcquisitionAdapter adapter = adapter(executable);

        byte[] payload = adapter.fetch(request());

        assertThat(payload).isEqualTo("chart-archive".getBytes());
        List<String> cachePaths = Files.readAllLines(log);
        assertThat(cachePaths).hasSize(1);
        assertThat(cachePaths.get(0)).contains("klueops-chart-").endsWith("/helm-cache/repository");
    }

    /** 여러 사용자의 Chart 가져오기가 동시에 실행돼도 Helm 캐시를 공유하지 않는지 검증한다. */
    @Test
    void isolatesConcurrentRepositoryDownloads() throws Exception {
        Path log = directory.resolve("concurrent-cache-paths.log");
        ProcessBuilderChartAcquisitionAdapter adapter = adapter(fakeHelm(log));
        var executor = Executors.newFixedThreadPool(4);
        try {
            var tasks = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(ignored -> (java.util.concurrent.Callable<byte[]>) () -> adapter.fetch(request()))
                    .toList();

            for (var result : executor.invokeAll(tasks)) {
                assertThat(result.get()).isEqualTo("chart-archive".getBytes());
            }
        } finally {
            executor.shutdownNow();
        }

        List<String> cachePaths = Files.readAllLines(log);
        assertThat(cachePaths).hasSize(8);
        assertThat(cachePaths.stream().distinct()).hasSize(8);
    }

    /** 테스트용 Helm 실행 파일과 검증기를 사용해 다운로드 어댑터를 구성한다. */
    private ProcessBuilderChartAcquisitionAdapter adapter(Path executable) {
        RemoteChartSourceValidator validator = new RemoteChartSourceValidator() {
            /** 네트워크에 의존하지 않고 테스트용 공개 HTTPS 주소를 승인한다. */
            @Override
            public URI requirePublicHttps(String value) {
                return URI.create(value);
            }
        };
        return new ProcessBuilderChartAcquisitionAdapter(executable.toString(), 1024 * 1024, "", validator);
    }

    /** 테스트에서 사용하는 일반 HTTPS Helm Repository 요청을 생성한다. */
    private ChartAcquisitionPort.FetchRequest request() {
        return new ChartAcquisitionPort.FetchRequest("https://charts.example.com", "prometheus", "1.0.0", null,
                Duration.ofSeconds(3));
    }

    /** 전달된 Helm 환경을 검증하고 작은 Chart archive를 만드는 가짜 실행 파일을 생성한다. */
    private Path fakeHelm(Path log) throws Exception {
        Path executable = directory.resolve("fake-helm-" + Math.abs(log.hashCode()));
        String script = """
                #!/bin/sh
                set -eu
                test -d "$HELM_CACHE_HOME"
                test -d "$HELM_CONFIG_HOME"
                test -d "$HELM_DATA_HOME"
                test -d "$HELM_REPOSITORY_CACHE"
                test "$HELM_REPOSITORY_CACHE" = "$HELM_CACHE_HOME/repository"
                printf '%%s\n' "$HELM_REPOSITORY_CACHE" >> '%s'
                destination=''
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = '--destination' ]; then
                    shift
                    destination="$1"
                  fi
                  shift
                done
                test -n "$destination"
                printf 'chart-archive' > "$destination/prometheus-1.0.0.tgz"
                """.formatted(log.toString().replace("'", "'\\''"));
        Files.writeString(executable, script);
        executable.toFile().setExecutable(true);
        return executable;
    }
}
