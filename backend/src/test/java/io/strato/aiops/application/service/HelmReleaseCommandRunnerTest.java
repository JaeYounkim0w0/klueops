package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.HelmDeploymentPort.HelmExecutionResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class HelmReleaseCommandRunnerTest {
    /** 같은 archive와 Values를 lint 후 template에 전달하고 lint 오류는 즉시 중지한다. */
    @Test void lintsBeforeRendering() {
        List<List<String>> calls = new ArrayList<>();
        var runner = new HelmReleaseCommandRunner((args, timeout) -> {
            calls.add(List.copyOf(args));
            return new HelmExecutionResult(0, args.get(0).equals("template") ? "manifest" : "", "");
        });
        assertThat(runner.render("test", "default", new byte[]{1}, "replicaCount: 1")).isEqualTo("manifest");
        assertThat(calls.stream().map(args -> args.get(0))).containsExactly("lint", "template");
        assertThat(calls.get(0).get(1)).isEqualTo(calls.get(1).get(2));
        assertThat(calls.get(0)).contains("--values");
        var failed = new HelmReleaseCommandRunner((args, timeout) -> {
            assertThat(args.get(0)).isEqualTo("lint");
            return new HelmExecutionResult(1, "invalid", "");
        });
        assertThatThrownBy(() -> failed.render("test", "default", new byte[]{1}, "{}"))
                .hasMessageContaining("Helm lint failed");
    }
}
