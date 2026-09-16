package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Fabric8KubernetesClusterAdapterTest {

    /** Fabric8KubernetesClusterAdapterTest의 connectionTestAcceptsNewVersionFieldsWhenNamespacesAreReachable 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void connectionTestAcceptsNewVersionFieldsWhenNamespacesAreReachable() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                /** 익명 구현체의 dispatch 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String path = request.getPath() == null ? "" : request.getPath();
                    if (path.startsWith("/api/v1/namespaces")) {
                        return jsonResponse("""
                                {
                                  "kind": "NamespaceList",
                                  "apiVersion": "v1",
                                  "metadata": {},
                                  "items": [
                                    {
                                      "metadata": { "name": "default" },
                                      "status": { "phase": "Active" }
                                    }
                                  ]
                                }
                                """);
                    }
                    if (path.startsWith("/version")) {
                        return jsonResponse("""
                                {
                                  "major": "1",
                                  "minor": "32",
                                  "gitVersion": "v1.32.0",
                                  "gitCommit": "test",
                                  "gitTreeState": "clean",
                                  "buildDate": "2026-01-01T00:00:00Z",
                                  "goVersion": "go1.23",
                                  "compiler": "gc",
                                  "platform": "linux/amd64",
                                  "emulationMajor": "1"
                                }
                                """);
                    }
                    return new MockResponse().setResponseCode(404);
                }
            });
            server.start();

            var adapter = new Fabric8KubernetesClusterAdapter(new ObjectMapper(), 1_000, 1_000, 1_000);

            var result = adapter.testConnection(new KubernetesConnectionCredential(
                    ClusterCredentialType.KUBECONFIG,
                    kubeconfig(server.url("/").toString())
            ));

            assertThat(result.reachable()).isTrue();
            assertThat(result.namespaces()).containsExactly("default");
            assertThat(result.kubernetesVersion()).isEqualTo("v1.32.0");
            assertThat(result.message()).isEqualTo("Kubernetes API connection succeeded");
        }
    }

    /** Fabric8KubernetesClusterAdapterTest의 jsonResponse 처리에 필요한 업무 로직을 수행한다. */
    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    /** Fabric8KubernetesClusterAdapterTest의 kubeconfig 처리에 필요한 업무 로직을 수행한다. */
    private static String kubeconfig(String serverUrl) {
        return """
                apiVersion: v1
                kind: Config
                clusters:
                - name: test
                  cluster:
                    server: %s
                contexts:
                - name: test
                  context:
                    cluster: test
                    user: test
                current-context: test
                users:
                - name: test
                  user:
                    token: test-token
                """.formatted(serverUrl);
    }
}
