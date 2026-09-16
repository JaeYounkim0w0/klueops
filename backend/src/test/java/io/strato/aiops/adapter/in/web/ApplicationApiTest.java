package io.strato.aiops.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import io.strato.aiops.application.port.out.KubernetesAccessReviewResult;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesDeploymentRevision;
import io.strato.aiops.application.port.out.KubernetesMutationPort;
import io.strato.aiops.application.port.out.KubernetesMutationResult;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ApplicationApiTest {

    @Autowired
    private MockMvc mockMvc;

    /** ApplicationApiTest의 deploysDockerApplicationAndCreatesOperationJobs 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void deploysDockerApplicationAndCreatesOperationJobs() throws Exception {
        String clusterId = registerCluster("application-api-cluster-" + UUID.randomUUID());
        String applicationId = deployDockerApplication(clusterId, "api-service-" + UUID.randomUUID());

        mockMvc.perform(get("/api/applications/{applicationId}", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(applicationId))
                .andExpect(jsonPath("$.clusterId").value(clusterId))
                .andExpect(jsonPath("$.deploymentType").value("DOCKER_IMAGE"))
                .andExpect(jsonPath("$.status").value("DEPLOY_REQUESTED"));

        mockMvc.perform(get("/api/applications/{applicationId}/status", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.status").value("DEPLOY_REQUESTED"));

        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", notNullValue()));

        String syncJobId = startApplicationOperation(applicationId, "sync");
        mockMvc.perform(get("/api/jobs/{jobId}", syncJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        String restartJobId = startApplicationOperation(applicationId, "restart");
        mockMvc.perform(get("/api/jobs/{jobId}", restartJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        String rollbackPreview = mockMvc.perform(get("/api/applications/{applicationId}/rollback/preview", applicationId)
                        .queryParam("targetRevision", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRevision").value("3"))
                .andExpect(jsonPath("$.targetRevision").value("2"))
                .andExpect(jsonPath("$.executable").value(true))
                .andExpect(jsonPath("$.confirmationText", notNullValue()))
                .andExpect(jsonPath("$.revisions[0].revision").value("3"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String rollbackConfirmation = JsonPath.read(rollbackPreview, "$.confirmationText");

        String rollbackJobId = startApplicationRollback(applicationId, 2, rollbackConfirmation);
        mockMvc.perform(get("/api/jobs/{jobId}", rollbackJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    /** ApplicationApiTest의 deploysHelmApplication 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void deploysHelmApplication() throws Exception {
        String clusterId = registerCluster("helm-api-cluster-" + UUID.randomUUID());

        mockMvc.perform(post("/api/applications/deploy/helm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clusterId": "%s",
                                  "namespace": "default",
                                  "name": "helm-service-%s",
                                  "releaseName": "helm-release",
                                  "chart": "oci://registry.example.com/charts/api"
                                }
                                """.formatted(clusterId, UUID.randomUUID())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.application.id", notNullValue()))
                .andExpect(jsonPath("$.application.deploymentType").value("HELM_CHART"))
                .andExpect(jsonPath("$.jobId", notNullValue()));
    }

    /** ApplicationApiTest의 deployDockerApplication 처리에 필요한 업무 로직을 수행한다. */
    private String deployDockerApplication(String clusterId, String name) throws Exception {
        String response = mockMvc.perform(post("/api/applications/deploy/docker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clusterId": "%s",
                                  "namespace": "default",
                                  "name": "%s",
                                  "image": "registry.example.com/api:1.0.0"
                                }
                                """.formatted(clusterId, name)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.application.id", notNullValue()))
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.application.id");
    }

    /** ApplicationApiTest의 registerCluster 처리에 필요한 데이터를 생성하거나 저장한다. */
    private String registerCluster(String name) throws Exception {
        String response = mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "cluster for application api test",
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

    /** ApplicationApiTest의 startApplicationOperation 처리에 필요한 업무 로직을 수행한다. */
    private String startApplicationOperation(String applicationId, String operation) throws Exception {
        String response = mockMvc.perform(post("/api/applications/{applicationId}/{operation}", applicationId, operation))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.jobId");
    }

    /** ApplicationApiTest의 startApplicationRollback 처리에 필요한 업무 로직을 수행한다. */
    private String startApplicationRollback(String applicationId, int targetRevision, String confirmText) throws Exception {
        String response = mockMvc.perform(post("/api/applications/{applicationId}/rollback", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetRevision": %s,
                                  "confirmText": "%s"
                                }
                                """.formatted(targetRevision, confirmText)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.jobId");
    }

    @TestConfiguration
    static class TestConfig {

        /** TestConfig의 kubernetesMutationPort 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        @Primary
        KubernetesMutationPort kubernetesMutationPort() {
            return new KubernetesMutationPort() {
                /** 익명 구현체의 canI 처리 조건의 충족 여부를 판단한다. */
                @Override
                public KubernetesAccessReviewResult canI(KubernetesConnectionCredential credential, String namespace, String verb,
                                                         String group, String resource, String subresource, String resourceName) {
                    return new KubernetesAccessReviewResult(true, verb, resource, subresource, namespace, "allowed");
                }

                /** 익명 구현체의 dryRunRolloutRestartDeployment 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesMutationResult dryRunRolloutRestartDeployment(KubernetesConnectionCredential credential,
                                                                                String namespace,
                                                                                String deploymentName) {
                    return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "rollout-restart-dry-run",
                            "generation=1", "generation=2 dryRun=true", Instant.now());
                }

                /** 익명 구현체의 rolloutRestartDeployment 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesMutationResult rolloutRestartDeployment(KubernetesConnectionCredential credential, String namespace,
                                                                         String deploymentName) {
                    return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "rollout-restart",
                            "generation=1", "generation=2", Instant.now());
                }

                /** 익명 구현체의 dryRunScaleDeployment 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesMutationResult dryRunScaleDeployment(KubernetesConnectionCredential credential, String namespace,
                                                                      String deploymentName, int replicas) {
                    return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "scale-dry-run",
                            "replicas=1", "replicas=" + replicas + " dryRun=true", Instant.now());
                }

                /** 익명 구현체의 scaleDeployment 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesMutationResult scaleDeployment(KubernetesConnectionCredential credential, String namespace,
                                                                String deploymentName, int replicas) {
                    return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "scale",
                            "replicas=1", "replicas=" + replicas, Instant.now());
                }

                /** 익명 구현체의 listDeploymentRevisions 처리 결과를 조회해 반환한다. */
                @Override
                public List<KubernetesDeploymentRevision> listDeploymentRevisions(KubernetesConnectionCredential credential,
                                                                                  String namespace,
                                                                                  String deploymentName) {
                    return List.of(
                            new KubernetesDeploymentRevision("3", true, deploymentName + "-rs3", 3,
                                    "registry.example.com/api:1.0.1", "revision=3 replicas=3 image=registry.example.com/api:1.0.1",
                                    Instant.parse("2026-07-13T00:00:00Z")),
                            new KubernetesDeploymentRevision("2", false, deploymentName + "-rs2", 3,
                                    "registry.example.com/api:1.0.0", "revision=2 replicas=3 image=registry.example.com/api:1.0.0",
                                    Instant.parse("2026-07-12T00:00:00Z"))
                    );
                }

                /** 익명 구현체의 previewRollbackDeployment 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesRollbackPlan previewRollbackDeployment(KubernetesConnectionCredential credential,
                                                                        String namespace, String deploymentName,
                                                                        Integer targetRevision) {
                    return new KubernetesRollbackPlan(namespace, deploymentName, "3", "2", true,
                            "Rollback guard passed", "ROLLBACK " + namespace + "/" + deploymentName + " TO REVISION 2",
                            "revision=3", "revision=2", Instant.now());
                }

                /** 익명 구현체의 rollbackDeployment 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesMutationResult rollbackDeployment(KubernetesConnectionCredential credential, String namespace,
                                                                   String deploymentName, Integer targetRevision) {
                    return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "rollback",
                            "revision=3", "revision=2", Instant.now());
                }
            };
        }
    }
}
