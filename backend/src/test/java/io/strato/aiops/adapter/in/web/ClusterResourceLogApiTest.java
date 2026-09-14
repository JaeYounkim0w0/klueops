package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.port.in.ClusterResourceLogLine;
import io.strato.aiops.application.port.in.ClusterResourceLogResult;
import io.strato.aiops.application.port.in.ClusterResourceLogStreamResult;
import io.strato.aiops.application.port.in.ClusterResourceLogTargetsResult;
import io.strato.aiops.application.port.in.GetClusterResourceLogsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "aiops.cluster-logs.heartbeat-ms=5")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ClusterResourceLogApiTest {

    private static final UUID CLUSTER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsPodsAndContainersThatBelongToAWorkload() throws Exception {
        mockMvc.perform(get("/api/clusters/{clusterId}/resources/{resourceType}/{resourceName}/logs/targets",
                        CLUSTER_ID, "Deployment", "checkout")
                        .param("namespace", "shop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supported").value(true))
                .andExpect(jsonPath("$.pods[0].podName").value("checkout-7d9f"))
                .andExpect(jsonPath("$.pods[0].containers[0].containerName").value("app"))
                .andExpect(jsonPath("$.pods[0].containers[0].restartCount").value(2));
    }

    @Test
    void returnsRecentLogsForTheExplicitPodAndContainerTarget() throws Exception {
        mockMvc.perform(get("/api/clusters/{clusterId}/resources/{resourceType}/{resourceName}/logs",
                        CLUSTER_ID, "Deployment", "checkout")
                        .param("namespace", "shop")
                        .param("podName", "checkout-7d9f")
                        .param("containerName", "app")
                        .param("tailLines", "300")
                        .param("previous", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tailLines").value(300))
                .andExpect(jsonPath("$.previous").value(true))
                .andExpect(jsonPath("$.log").value("line one\nline two"));
    }

    @Test
    void streamsHeartbeatLogAndCompletionEvents() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/clusters/{clusterId}/resources/{resourceType}/{resourceName}/logs/stream",
                        CLUSTER_ID, "Deployment", "checkout")
                        .param("namespace", "shop")
                        .param("podName", "checkout-7d9f")
                        .param("containerName", "app")
                        .param("tailLines", "50"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: heartbeat")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: log")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("streamed line")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done")));
    }

    @TestConfiguration
    static class FakeLogUseCaseConfig {

        @Bean
        @Primary
        GetClusterResourceLogsUseCase getClusterResourceLogsUseCase() {
            return new GetClusterResourceLogsUseCase() {
                @Override
                public ClusterResourceLogTargetsResult getTargets(UUID clusterId, String namespace,
                                                                  String resourceType, String resourceName) {
                    return new ClusterResourceLogTargetsResult(clusterId, namespace, resourceType, resourceName,
                            true, null, List.of(new ClusterResourceLogTargetsResult.PodTarget(
                            "checkout-7d9f", "Running", Instant.parse("2026-09-03T00:00:00Z"),
                            List.of(new ClusterResourceLogTargetsResult.ContainerTarget(
                                    "app", true, 2, "RUNNING", false)))));
                }

                @Override
                public ClusterResourceLogResult getRecentLogs(UUID clusterId, String namespace, String resourceType,
                                                              String resourceName, String podName, String containerName,
                                                              int tailLines, boolean previous) {
                    return new ClusterResourceLogResult(clusterId, namespace, resourceType, resourceName, podName,
                            containerName, tailLines, previous, "line one\nline two", false, Instant.now());
                }

                @Override
                public ClusterResourceLogStreamResult streamLogs(UUID clusterId, String namespace, String resourceType,
                                                                 String resourceName, String podName, String containerName,
                                                                 int tailLines, Consumer<ClusterResourceLogLine> onLine,
                                                                 BooleanSupplier cancelled) {
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    onLine.accept(new ClusterResourceLogLine(podName, containerName, "streamed line", Instant.now()));
                    return new ClusterResourceLogStreamResult("COMPLETED", 1, 30);
                }
            };
        }
    }
}
