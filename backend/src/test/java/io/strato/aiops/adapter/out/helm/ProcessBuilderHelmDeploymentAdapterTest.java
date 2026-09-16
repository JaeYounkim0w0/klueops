package io.strato.aiops.adapter.out.helm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessBuilderHelmDeploymentAdapterTest {
    @TempDir
    Path directory;

    /** ProcessBuilderHelmDeploymentAdapterTest의 executesArgumentsWithoutShellInterpolation 처리의 핵심 작업 흐름을 실행한다. */
    @Test
    void executesArgumentsWithoutShellInterpolation() throws Exception {
        Path executable = directory.resolve("fake-helm");
        Files.writeString(executable, "#!/bin/sh\nprintf '%s\\n' \"$@\"\n");
        executable.toFile().setExecutable(true);
        ProcessBuilderHelmDeploymentAdapter adapter = new ProcessBuilderHelmDeploymentAdapter(executable.toString());

        var result = adapter.execute(List.of("template", "$(touch should-not-exist)", "chart.tgz"), Duration.ofSeconds(2));

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("$(touch should-not-exist)");
        assertThat(Files.exists(Path.of("should-not-exist"))).isFalse();
    }

    /** ProcessBuilderHelmDeploymentAdapterTest의 boundsExecutionTime 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void boundsExecutionTime() throws Exception {
        Path executable = directory.resolve("slow-helm");
        Files.writeString(executable, "#!/bin/sh\nsleep 3\n");
        executable.toFile().setExecutable(true);
        ProcessBuilderHelmDeploymentAdapter adapter = new ProcessBuilderHelmDeploymentAdapter(executable.toString());

        var result = adapter.execute(List.of("template"), Duration.ofSeconds(1));

        assertThat(result.exitCode()).isEqualTo(124);
        assertThat(result.stderr()).contains("timed out");
    }
}
