package io.strato.aiops.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ClusterRegistrationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registersClusterWithKubeconfigCredential() throws Exception {
        mockMvc.perform(post("/api/clusters")
                        .header(RequestAttributes.HEADER_REQUEST_ID, "register-kubeconfig-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "dev-cluster",
                                  "description": "development cluster",
                                  "environment": "DEV",
                                  "provider": "KIND",
                                  "region": "local",
                                  "credentialType": "KUBECONFIG",
                                  "kubeconfig": "apiVersion: v1\\nkind: Config\\nclusters: []",
                                  "namespaceAccess": {
                                    "clusterWide": false,
                                    "allowedNamespaces": ["default"],
                                    "defaultNamespace": "default"
                                  },
                                  "syncSettings": {
                                    "autoSyncEnabled": true,
                                    "syncIntervalSeconds": 300
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(RequestAttributes.HEADER_REQUEST_ID, "register-kubeconfig-request"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("dev-cluster"))
                .andExpect(jsonPath("$.status").value("REGISTERED"));
    }

    @Test
    void registersClusterWithServiceAccountCredential() throws Exception {
        mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "sa-cluster",
                                  "description": "service account cluster",
                                  "environment": "DEV",
                                  "provider": "ON_PREM",
                                  "region": "local",
                                  "credentialType": "SERVICE_ACCOUNT_TOKEN",
                                  "serviceAccount": {
                                    "apiServerUrl": "https://127.0.0.1:6443",
                                    "caCertificate": "-----BEGIN CERTIFICATE-----",
                                    "token": "sample-token"
                                  },
                                  "syncSettings": {
                                    "autoSyncEnabled": true,
                                    "syncIntervalSeconds": 300
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("sa-cluster"))
                .andExpect(jsonPath("$.status").value("REGISTERED"));
    }

    @Test
    void rejectsUnsupportedExecPluginKubeconfig() throws Exception {
        mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "exec-cluster",
                                  "description": "unsupported exec plugin cluster",
                                  "environment": "DEV",
                                  "provider": "EKS",
                                  "region": "ap-northeast-2",
                                  "credentialType": "KUBECONFIG",
                                  "kubeconfig": "apiVersion: v1\\nusers:\\n- user:\\n    exec:\\n      command: aws"
                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.requestId", notNullValue()));
    }

    @Test
    void deletesClusterRegistration() throws Exception {
        String response = mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "delete-cluster",
                                  "description": "cluster to delete",
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
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String clusterId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(delete("/api/clusters/{clusterId}", clusterId)
                        .header(RequestAttributes.HEADER_REQUEST_ID, "delete-cluster-request"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(RequestAttributes.HEADER_REQUEST_ID, "delete-cluster-request"));

        mockMvc.perform(get("/api/clusters/{clusterId}", clusterId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deletesClusterRegistrationWithSyncData() throws Exception {
        String response = mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "delete-synced-cluster",
                                  "description": "cluster to delete with sync data",
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
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String clusterId = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        UUID clusterUuid = UUID.fromString(clusterId);
        UUID asyncJobId = UUID.randomUUID();
        UUID syncJobId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update("""
                insert into async_jobs (id, type, status, created_at)
                values (?, ?, ?, ?)
                """, asyncJobId, "CLUSTER_SYNC", "COMPLETED", java.sql.Timestamp.from(now));
        jdbcTemplate.update("""
                insert into sync_jobs (
                    id, async_job_id, cluster_id, sync_type, status, requested_by,
                    resource_count, event_count, completed_at, created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, syncJobId, asyncJobId, clusterUuid, "MANUAL", "COMPLETED", "test", 1, 1,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        jdbcTemplate.update("""
                insert into kubernetes_resource_snapshots (
                    id, cluster_id, sync_job_id, namespace, resource_type, resource_name,
                    status, summary_json, raw_json, truncated, collected_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), clusterUuid, syncJobId, "default", "Pod", "sample-pod", "Running", "{}", "{}", false,
                java.sql.Timestamp.from(now));
        jdbcTemplate.update("""
                insert into kubernetes_event_snapshots (
                    id, cluster_id, sync_job_id, namespace, involved_kind, involved_name,
                    reason, type, message, event_time, count, collected_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), clusterUuid, syncJobId, "default", "Pod", "sample-pod", "Started", "Normal", "Started",
                java.sql.Timestamp.from(now), 1, java.sql.Timestamp.from(now));

        mockMvc.perform(delete("/api/clusters/{clusterId}", clusterId)
                        .header(RequestAttributes.HEADER_REQUEST_ID, "delete-synced-cluster-request"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(RequestAttributes.HEADER_REQUEST_ID, "delete-synced-cluster-request"));

        mockMvc.perform(get("/api/clusters/{clusterId}", clusterId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
