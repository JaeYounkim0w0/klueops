package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.KubectlRunRequest;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteKubectlRunnerAdapterTest {
    @Test
    void streamsNdjsonAndAuthenticatesTheInternalRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/x-ndjson")
                    .setBody("{\"type\":\"output\",\"channel\":\"stdout\",\"text\":\"pod/api\\n\"}\n"
                            + "{\"type\":\"result\",\"exitCode\":0,\"stdout\":\"pod/api\\n\",\"stderr\":\"\",\"timedOut\":false,\"canceled\":false,\"truncated\":false,\"durationMs\":12}\n"));
            RemoteKubectlRunnerAdapter adapter = new RemoteKubectlRunnerAdapter(new ObjectMapper(), server.url("/").uri(), "a".repeat(32));
            List<String> streamed = new ArrayList<>();
            var request = new KubectlRunRequest(UUID.randomUUID(),
                    new KubernetesConnectionCredential(ClusterCredentialType.KUBECONFIG, "config"), "default",
                    List.of("get", "pods"), null, Duration.ofSeconds(10), 65_536);

            var result = adapter.run(request, (channel, text) -> streamed.add(channel + ":" + text));

            assertThat(result.exitCode()).isZero();
            assertThat(streamed).containsExactly("stdout:pod/api\n");
            var recorded = server.takeRequest();
            assertThat(recorded.getHeader("X-AIOPS-Runner-Token")).isEqualTo("a".repeat(32));
            assertThat(recorded.getBody().readUtf8()).doesNotContain("X-AIOPS-Runner-Token");
            assertThat(adapter.executionBoundary()).isEqualTo("ISOLATED_RUNNER");
        }
    }
}
