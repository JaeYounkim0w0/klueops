package io.strato.aiops.application.service;

import com.jayway.jsonpath.JsonPath;
import io.strato.aiops.adapter.in.web.RequestAttributes;
import io.strato.aiops.application.port.out.ClusterSyncExecutorPort;
import io.strato.aiops.application.port.out.KubernetesEventSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesResourceSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesStateInventory;
import io.strato.aiops.application.port.out.KubernetesStateSyncPort;
import io.strato.aiops.application.port.out.SyncJobRepositoryPort;
import io.strato.aiops.domain.job.AsyncJobStatus;
import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import io.strato.aiops.domain.sync.SyncJob;
import io.strato.aiops.domain.sync.SyncJobStatus;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ClusterSyncWorkerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClusterSyncWorker clusterSyncWorker;

    @Autowired
    private SyncJobRepositoryPort syncJobRepositoryPort;

    @Autowired
    private KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort;

    @Autowired
    private KubernetesEventSnapshotRepositoryPort eventSnapshotRepositoryPort;

    /** ClusterSyncWorkerTest의 storesResourceAndEventSnapshotsWhenClusterSyncRuns 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Test
    void storesResourceAndEventSnapshotsWhenClusterSyncRuns() throws Exception {
        String clusterId = registerCluster("sync-worker-cluster");
        UUID asyncJobId = startSync(clusterId);

        clusterSyncWorker.runClusterSync(asyncJobId);

        SyncJob syncJob = syncJobRepositoryPort.findByAsyncJobId(asyncJobId).orElseThrow();
        assertThat(syncJob.status()).isEqualTo(SyncJobStatus.SUCCEEDED);
        assertThat(syncJob.resourceCount()).isEqualTo(1);
        assertThat(syncJob.eventCount()).isEqualTo(1);
        assertThat(resourceSnapshotRepositoryPort.countBySyncJobId(syncJob.id())).isEqualTo(1);
        assertThat(eventSnapshotRepositoryPort.countBySyncJobId(syncJob.id())).isEqualTo(1);

        mockMvc.perform(get("/api/jobs/{jobId}", asyncJobId))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.status"))
                        .isEqualTo(AsyncJobStatus.SUCCEEDED.name()));

        mockMvc.perform(get("/api/clusters/{clusterId}/resources", clusterId)
                        .param("namespace", "default")
                        .param("resourceType", "Pod"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resourceType").value("Pod"))
                .andExpect(jsonPath("$[0].resourceName").value("sample-pod"));

        mockMvc.perform(get("/api/clusters/{clusterId}/events", clusterId)
                        .param("namespace", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("Started"))
                .andExpect(jsonPath("$[0].message").value("Started container sample"));

        mockMvc.perform(get("/api/clusters/{clusterId}/sync-status", clusterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asyncJobId").value(asyncJobId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resourceCount").value(1))
                .andExpect(jsonPath("$.eventCount").value(1));
    }

    /** ClusterSyncWorkerTest의 resourceAndEventApisReturnOnlyLatestSucceededSyncSnapshot 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void resourceAndEventApisReturnOnlyLatestSucceededSyncSnapshot() throws Exception {
        String clusterId = registerCluster("sync-latest-snapshot-cluster");
        UUID firstAsyncJobId = startSync(clusterId);
        clusterSyncWorker.runClusterSync(firstAsyncJobId);
        UUID firstSyncJobId = syncJobRepositoryPort.findByAsyncJobId(firstAsyncJobId).orElseThrow().id();

        UUID secondAsyncJobId = startSync(clusterId);
        clusterSyncWorker.runClusterSync(secondAsyncJobId);
        UUID secondSyncJobId = syncJobRepositoryPort.findByAsyncJobId(secondAsyncJobId).orElseThrow().id();

        assertThat(firstSyncJobId).isNotEqualTo(secondSyncJobId);

        mockMvc.perform(get("/api/clusters/{clusterId}/resources", clusterId)
                        .param("namespace", "default")
                        .param("resourceType", "Pod"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].syncJobId").value(secondSyncJobId.toString()))
                .andExpect(jsonPath("$[0].resourceName").value("sample-pod"));

        mockMvc.perform(get("/api/clusters/{clusterId}/events", clusterId)
                        .param("namespace", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].syncJobId").value(secondSyncJobId.toString()))
                .andExpect(jsonPath("$[0].reason").value("Started"));
    }

    /** ClusterSyncWorkerTest의 registerCluster 처리에 필요한 데이터를 생성하거나 저장한다. */
    private String registerCluster(String name) throws Exception {
        String response = mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "cluster for sync worker test",
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

    /** ClusterSyncWorkerTest의 startSync 처리에 필요한 업무 로직을 수행한다. */
    private UUID startSync(String clusterId) throws Exception {
        String response = mockMvc.perform(post("/api/clusters/{clusterId}/sync", clusterId)
                        .header(RequestAttributes.HEADER_REQUEST_ID, "sync-worker-request"))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.jobId"));
    }

    @TestConfiguration
    static class ClusterSyncWorkerTestConfig {

        /** ClusterSyncWorkerTestConfig의 clusterSyncExecutorPort 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        @Primary
        ClusterSyncExecutorPort clusterSyncExecutorPort() {
            return asyncJobId -> {
            };
        }

        /** ClusterSyncWorkerTestConfig의 kubernetesStateSyncPort 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        @Primary
        KubernetesStateSyncPort kubernetesStateSyncPort() {
            return credential -> new KubernetesStateInventory(
                    List.of(new KubernetesResourceSnapshot.CollectedResource(
                            "default",
                            "Pod",
                            "sample-pod",
                            "pod-uid",
                            "Running",
                            "{\"phase\":\"Running\"}",
                            null,
                            false,
                            Instant.now()
                    )),
                    List.of(new KubernetesEventSnapshot.CollectedEvent(
                            "default",
                            "Pod",
                            "sample-pod",
                            "Started",
                            "Normal",
                            "Started container sample",
                            Instant.now(),
                            1,
                            Instant.now()
                    ))
            );
        }
    }
}
