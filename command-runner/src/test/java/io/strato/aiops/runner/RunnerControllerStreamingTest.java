package io.strato.aiops.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RunnerControllerStreamingTest {
    @TempDir Path directory;

    @Test
    void keepsStreamOpenUntilOutputAndFinalResultAreWritten() throws Exception {
        Path kubectl = directory.resolve("kubectl-test");
        Files.writeString(kubectl, "#!/bin/sh\nprintf 'pod/api\\n'\n");
        kubectl.toFile().setExecutable(true);
        ObjectMapper objectMapper = new ObjectMapper();
        RunnerController controller = new RunnerController(
                new RunnerProcessExecutor(objectMapper, kubectl.toString()),
                new RunnerReplayGuard(Clock.systemUTC(), Duration.ofMinutes(5)),
                objectMapper,
                kubectl.toString()
        );
        RunnerRequest request = new RunnerRequest(
                UUID.randomUUID(), "KUBECONFIG", "apiVersion: v1\nkind: Config\n",
                "default", List.of("get", "pods"), "", 5_000, 65_536
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        controller.execute(request).writeTo(output);

        List<String> events = output.toString(StandardCharsets.UTF_8).lines().toList();
        assertThat(events).hasSize(2);
        assertThat(objectMapper.readTree(events.get(0)).path("type").asText()).isEqualTo("output");
        assertThat(objectMapper.readTree(events.get(1)).path("type").asText()).isEqualTo("result");
    }
}
