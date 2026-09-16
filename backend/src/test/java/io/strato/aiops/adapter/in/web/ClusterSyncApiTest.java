package io.strato.aiops.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import io.strato.aiops.application.port.out.ClusterSyncExecutorPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ClusterSyncApiTest {

    @Autowired
    private MockMvc mockMvc;

    /** ClusterSyncApiTest의 clusterSyncCreatesAsyncJobAndReturnsRequestId 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void clusterSyncCreatesAsyncJobAndReturnsRequestId() throws Exception {
        String clusterId = registerCluster("sync-api-cluster");

        String response = mockMvc.perform(post("/api/clusters/{clusterId}/sync", clusterId)
                        .header(RequestAttributes.HEADER_REQUEST_ID, "test-request-id"))
                .andExpect(status().isAccepted())
                .andExpect(header().string(RequestAttributes.HEADER_REQUEST_ID, "test-request-id"))
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String jobId = response.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId))
                .andExpect(jsonPath("$.type").value("CLUSTER_SYNC"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(jobId));
    }

    /** ClusterSyncApiTest의 clusterSyncReusesActiveJobForSameCluster 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void clusterSyncReusesActiveJobForSameCluster() throws Exception {
        String clusterId = registerCluster("sync-reuse-cluster");

        String firstResponse = mockMvc.perform(post("/api/clusters/{clusterId}/sync", clusterId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstJobId = JsonPath.read(firstResponse, "$.jobId");

        String secondResponse = mockMvc.perform(post("/api/clusters/{clusterId}/sync", clusterId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(firstJobId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secondJobId = JsonPath.read(secondResponse, "$.jobId");

        mockMvc.perform(get("/api/jobs/{jobId}", secondJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstJobId))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    /** ClusterSyncApiTest의 concurrentClusterSyncRequestsReuseExactlyOneJob 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void concurrentClusterSyncRequestsReuseExactlyOneJob() throws Exception {
        String clusterId = registerCluster("sync-concurrent-reuse-cluster");
        var executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<String>> requests = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                requests.add(() -> {
                    String response = mockMvc.perform(post("/api/clusters/{clusterId}/sync", clusterId))
                            .andExpect(status().isAccepted())
                            .andReturn().getResponse().getContentAsString();
                    return JsonPath.read(response, "$.jobId");
                });
            }
            List<String> jobIds = new ArrayList<>();
            for (var future : executor.invokeAll(requests)) {
                jobIds.add(future.get());
            }
            assertEquals(1, new HashSet<>(jobIds).size());
        } finally {
            executor.shutdownNow();
        }
    }

    /** ClusterSyncApiTest의 canceledClusterSyncDoesNotBlockNextSync 처리 조건의 충족 여부를 판단한다. */
    @Test
    void canceledClusterSyncDoesNotBlockNextSync() throws Exception {
        String clusterId = registerCluster("sync-cancel-retry-cluster");

        String firstResponse = mockMvc.perform(post("/api/clusters/{clusterId}/sync", clusterId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstJobId = JsonPath.read(firstResponse, "$.jobId");

        mockMvc.perform(post("/api/jobs/{jobId}/cancel", firstJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        String secondResponse = mockMvc.perform(post("/api/clusters/{clusterId}/sync", clusterId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secondJobId = JsonPath.read(secondResponse, "$.jobId");

        assertNotEquals(firstJobId, secondJobId);
        mockMvc.perform(get("/api/jobs/{jobId}", secondJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    /** ClusterSyncApiTest의 updatesClusterSyncSettings 처리 대상의 상태를 갱신한다. */
    @Test
    void updatesClusterSyncSettings() throws Exception {
        String clusterId = registerCluster("sync-settings-cluster");

        mockMvc.perform(put("/api/clusters/{clusterId}/sync-settings", clusterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "autoSyncEnabled": false,
                                  "syncIntervalSeconds": 600
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterId").value(clusterId))
                .andExpect(jsonPath("$.autoSyncEnabled").value(false))
                .andExpect(jsonPath("$.syncIntervalSeconds").value(600));
    }

    /** ClusterSyncApiTest의 rejectsTooShortSyncInterval 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsTooShortSyncInterval() throws Exception {
        String clusterId = registerCluster("sync-settings-invalid-cluster");

        mockMvc.perform(put("/api/clusters/{clusterId}/sync-settings", clusterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "autoSyncEnabled": true,
                                  "syncIntervalSeconds": 10
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /** ClusterSyncApiTest의 registerCluster 처리에 필요한 데이터를 생성하거나 저장한다. */
    private String registerCluster(String name) throws Exception {
        String response = mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "cluster for sync api test",
                                  "environment": "DEV",
                                  "provider": "KIND",
                                  "region": "local",
                                  "credentialType": "KUBECONFIG",
                                  "kubeconfig": "apiVersion: v1\\nkind: Config\\nclusters: []",
                                  "syncSettings": {
                                    "autoSyncEnabled": true,
                                    "syncIntervalSeconds": 300
                                  }
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @TestConfiguration
    static class NoopSyncExecutorConfig {

        /** NoopSyncExecutorConfig의 clusterSyncExecutorPort 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        @Primary
        ClusterSyncExecutorPort clusterSyncExecutorPort() {
            return asyncJobId -> {
            };
        }
    }
}
