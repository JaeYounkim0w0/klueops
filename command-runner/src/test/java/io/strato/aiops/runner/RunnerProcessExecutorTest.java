package io.strato.aiops.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RunnerProcessExecutorTest {
    @TempDir Path directory;

    @Test
    void streamsOutputAndReturnsBoundedResult() throws Exception {
        Path kubectl = directory.resolve("kubectl-test");
        Files.writeString(kubectl, "#!/bin/sh\nprintf 'pod/api\\n'\n");
        kubectl.toFile().setExecutable(true);
        RunnerProcessExecutor executor = new RunnerProcessExecutor(new ObjectMapper(), kubectl.toString());
        RunnerRequest request = new RunnerRequest(UUID.randomUUID(), "KUBECONFIG", "apiVersion: v1\nkind: Config\n",
                "default", List.of("get", "pods"), "", 5_000, 65_536);
        List<Map<String, Object>> events = new ArrayList<>();

        executor.execute(request, events::add);

        assertThat(events).anySatisfy(event -> {
            assertThat(event.get("type")).isEqualTo("output");
            assertThat(event.get("text")).isEqualTo("pod/api\n");
        }).anySatisfy(event -> {
            assertThat(event.get("type")).isEqualTo("result");
            assertThat(event.get("exitCode")).isEqualTo(0);
        });
        assertThat(executor.cancel(request.executionId())).isFalse();
    }

    @Test
    void reportsCancellationInTheFinalResult() throws Exception {
        Path kubectl = directory.resolve("kubectl-slow");
        Files.writeString(kubectl, "#!/bin/sh\nsleep 10\n");
        kubectl.toFile().setExecutable(true);
        RunnerProcessExecutor executor = new RunnerProcessExecutor(new ObjectMapper(), kubectl.toString());
        RunnerRequest request = new RunnerRequest(UUID.randomUUID(), "KUBECONFIG", "apiVersion: v1\nkind: Config\n",
                "default", List.of("get", "pods"), "", 30_000, 65_536);
        List<Map<String, Object>> events = java.util.Collections.synchronizedList(new ArrayList<>());

        CompletableFuture<Void> execution = CompletableFuture.runAsync(() -> {
            try { executor.execute(request, events::add); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        });
        boolean accepted = false;
        for (int attempt = 0; attempt < 100 && !accepted; attempt++) {
            accepted = executor.cancel(request.executionId());
            if (!accepted) Thread.sleep(10);
        }
        execution.get(5, TimeUnit.SECONDS);

        assertThat(accepted).isTrue();
        assertThat(events).anySatisfy(event -> {
            assertThat(event.get("type")).isEqualTo("result");
            assertThat(event.get("canceled")).isEqualTo(true);
        });
    }
}
