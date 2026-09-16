package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Fabric8KubernetesResourceLogAdapterTest {

    /** Fabric8KubernetesResourceLogAdapterTest의 resolvesDeploymentPodsAndReadsOnlyAnOwnedContainer 처리에 필요한 결과를 조합해 반환한다. */
    @Test
    void resolvesDeploymentPodsAndReadsOnlyAnOwnedContainer() throws Exception {
        try (MockWebServer server = kubernetesServer(); SchedulerFixture scheduler = new SchedulerFixture()) {
            var adapter = adapter(scheduler.scheduler());
            var credential = credential(server);

            var targets = adapter.findTargets(credential, "default", "Deployment", "checkout");
            var logs = adapter.getRecentLogs(credential, "default", "Deployment", "checkout",
                    "checkout-abc", "app", 100, false);

            assertThat(targets.supported()).isTrue();
            assertThat(targets.pods()).hasSize(1);
            assertThat(targets.pods().get(0).podName()).isEqualTo("checkout-abc");
            assertThat(targets.pods().get(0).containers().get(0).containerName()).isEqualTo("app");
            assertThat(logs.log()).contains("server started", "request completed");
        }
    }

    /** Fabric8KubernetesResourceLogAdapterTest의 rejectsAPodThatDoesNotBelongToTheSelectedWorkload 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsAPodThatDoesNotBelongToTheSelectedWorkload() throws Exception {
        try (MockWebServer server = kubernetesServer(); SchedulerFixture scheduler = new SchedulerFixture()) {
            var adapter = adapter(scheduler.scheduler());

            assertThatThrownBy(() -> adapter.getRecentLogs(credential(server), "default", "Deployment", "checkout",
                    "another-app-123", "app", 100, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not belong");
        }
    }

    /** Fabric8KubernetesResourceLogAdapterTest의 kubernetesServer 처리에 필요한 업무 로직을 수행한다. */
    private MockWebServer kubernetesServer() throws Exception {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            /** 익명 구현체의 dispatch 처리에 필요한 업무 로직을 수행한다. */
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/apis/apps/v1/namespaces/default/deployments/checkout")) {
                    return json("""
                            {"apiVersion":"apps/v1","kind":"Deployment","metadata":{"name":"checkout"},
                             "spec":{"selector":{"matchLabels":{"app":"checkout"}}}}
                            """);
                }
                if (path.startsWith("/api/v1/namespaces/default/pods?") || path.equals("/api/v1/namespaces/default/pods")) {
                    return json("""
                            {"apiVersion":"v1","kind":"PodList","metadata":{},"items":[
                              {"apiVersion":"v1","kind":"Pod","metadata":{"name":"checkout-abc"},
                               "spec":{"containers":[{"name":"app","image":"checkout:test"}]},
                               "status":{"phase":"Running","startTime":"2026-09-03T00:00:00Z",
                                 "containerStatuses":[{"name":"app","ready":true,"restartCount":1,
                                   "state":{"running":{"startedAt":"2026-09-03T00:00:01Z"}},"image":"checkout:test","imageID":"test"}]}}
                            ]}
                            """);
                }
                if (path.startsWith("/api/v1/namespaces/default/pods/checkout-abc/log")) {
                    return new MockResponse().setResponseCode(200).setHeader("Content-Type", "text/plain")
                            .setBody("2026-09-03T00:00:01Z server started\n2026-09-03T00:00:02Z request completed\n");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        return server;
    }

    /** Fabric8KubernetesResourceLogAdapterTest의 adapter 처리에 필요한 업무 로직을 수행한다. */
    private Fabric8KubernetesResourceLogAdapter adapter(ThreadPoolTaskScheduler scheduler) {
        return new Fabric8KubernetesResourceLogAdapter(new ObjectMapper(), scheduler,
                1_000, 1_000, 30_000, 50);
    }

    /** Fabric8KubernetesResourceLogAdapterTest의 credential 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesConnectionCredential credential(MockWebServer server) {
        return new KubernetesConnectionCredential(ClusterCredentialType.KUBECONFIG, """
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
                """.formatted(server.url("/").toString()));
    }

    /** Fabric8KubernetesResourceLogAdapterTest의 json 처리에 필요한 업무 로직을 수행한다. */
    private MockResponse json(String body) {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
    }

    private static final class SchedulerFixture implements AutoCloseable {
        private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        /** SchedulerFixture 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
        private SchedulerFixture() {
            scheduler.setPoolSize(2);
            scheduler.initialize();
        }

        /** SchedulerFixture의 scheduler 처리에 필요한 업무 로직을 수행한다. */
        private ThreadPoolTaskScheduler scheduler() {
            return scheduler;
        }

        /** SchedulerFixture의 close 처리 대상과 관련 상태를 안전하게 정리한다. */
        @Override
        public void close() {
            scheduler.shutdown();
        }
    }
}
