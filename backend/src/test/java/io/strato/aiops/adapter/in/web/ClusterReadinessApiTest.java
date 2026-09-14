package io.strato.aiops.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import io.strato.aiops.application.port.out.KubernetesAccessReviewResult;
import io.strato.aiops.application.port.out.KubernetesClusterPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesConnectionTestResult;
import io.strato.aiops.application.port.out.KubernetesDeploymentRevision;
import io.strato.aiops.application.port.out.KubernetesMutationPort;
import io.strato.aiops.application.port.out.KubernetesMutationResult;
import io.strato.aiops.application.port.out.KubernetesNamespace;
import io.strato.aiops.application.port.out.KubernetesNode;
import io.strato.aiops.application.port.out.KubernetesRollbackPlan;
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

import java.util.List;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ClusterReadinessApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsEvidenceBasedReadinessViews() throws Exception {
        String clusterId = registerCluster();

        mockMvc.perform(get("/api/clusters/{clusterId}/readiness", clusterId)
                        .param("namespace", "default")
                        .param("targetVersion", "v1.32"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterId").value(clusterId))
                .andExpect(jsonPath("$.capabilities.checks.length()", greaterThan(5)))
                .andExpect(jsonPath("$.capabilities.checks[0].evidenceSource").value("SELF_SUBJECT_ACCESS_REVIEW"))
                .andExpect(jsonPath("$.credential.connectionReachable").value(true))
                .andExpect(jsonPath("$.credential.secretValueExposed").value(false))
                .andExpect(jsonPath("$.upgrade.currentVersion").value("v1.31.2"))
                .andExpect(jsonPath("$.upgrade.targetVersion").value("v1.32"))
                .andExpect(jsonPath("$.checkedAt").exists());

        mockMvc.perform(get("/api/clusters/{clusterId}/capabilities", clusterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks.length()", greaterThan(5)));

        mockMvc.perform(get("/api/clusters/{clusterId}/credential-health", clusterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialType").value("KUBECONFIG"));

        mockMvc.perform(get("/api/clusters/{clusterId}/upgrade-readiness", clusterId)
                        .param("targetVersion", "v1.32"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value("v1.31.2"));
    }

    private String registerCluster() throws Exception {
        String response = mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "readiness-cluster",
                                  "environment": "DEV",
                                  "provider": "KIND",
                                  "credentialType": "KUBECONFIG",
                                  "kubeconfig": "apiVersion: v1\\nkind: Config\\nclusters: []\\nusers: []",
                                  "syncSettings": {"autoSyncEnabled": false, "syncIntervalSeconds": 300}
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @TestConfiguration
    static class ReadinessTestConfig {

        @Bean
        @Primary
        KubernetesClusterPort readinessClusterPort() {
            return new KubernetesClusterPort() {
                @Override
                public KubernetesConnectionTestResult testConnection(KubernetesConnectionCredential credential) {
                    return KubernetesConnectionTestResult.success("v1.31.2", List.of("default"));
                }

                @Override
                public List<KubernetesNamespace> listNamespaces(KubernetesConnectionCredential credential) {
                    return List.of(new KubernetesNamespace("default", "Active"));
                }

                @Override
                public List<KubernetesNode> listNodes(KubernetesConnectionCredential credential) {
                    return List.of(new KubernetesNode("worker-1", "Ready", "v1.31.2", "Linux", "containerd://1.7"));
                }
            };
        }

        @Bean
        @Primary
        KubernetesMutationPort readinessMutationPort() {
            return new KubernetesMutationPort() {
                @Override
                public KubernetesAccessReviewResult canI(KubernetesConnectionCredential credential, String namespace,
                                                          String verb, String group, String resource,
                                                          String subresource, String resourceName) {
                    return new KubernetesAccessReviewResult(!"delete".equals(verb), verb, resource, subresource,
                            namespace, "test access review");
                }

                @Override public KubernetesMutationResult dryRunRolloutRestartDeployment(KubernetesConnectionCredential c, String n, String d) { throw new UnsupportedOperationException(); }
                @Override public KubernetesMutationResult rolloutRestartDeployment(KubernetesConnectionCredential c, String n, String d) { throw new UnsupportedOperationException(); }
                @Override public KubernetesMutationResult dryRunScaleDeployment(KubernetesConnectionCredential c, String n, String d, int r) { throw new UnsupportedOperationException(); }
                @Override public KubernetesMutationResult scaleDeployment(KubernetesConnectionCredential c, String n, String d, int r) { throw new UnsupportedOperationException(); }
                @Override public List<KubernetesDeploymentRevision> listDeploymentRevisions(KubernetesConnectionCredential c, String n, String d) { return List.of(); }
                @Override public KubernetesRollbackPlan previewRollbackDeployment(KubernetesConnectionCredential c, String n, String d, Integer r) { throw new UnsupportedOperationException(); }
                @Override public KubernetesMutationResult rollbackDeployment(KubernetesConnectionCredential c, String n, String d, Integer r) { throw new UnsupportedOperationException(); }
            };
        }
    }
}
