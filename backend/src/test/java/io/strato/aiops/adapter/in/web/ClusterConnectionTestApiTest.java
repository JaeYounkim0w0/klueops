package io.strato.aiops.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import io.strato.aiops.application.port.out.KubernetesClusterPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesConnectionTestResult;
import io.strato.aiops.application.port.out.KubernetesNamespace;
import io.strato.aiops.application.port.out.KubernetesNode;
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
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ClusterConnectionTestApiTest {

    @Autowired
    private MockMvc mockMvc;

    /** ClusterConnectionTestApiTest의 testsRegisteredClusterConnection 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void testsRegisteredClusterConnection() throws Exception {
        String clusterId = registerCluster("connection-test-cluster");

        mockMvc.perform(post("/api/clusters/{clusterId}/connection-test", clusterId)
                        .header(RequestAttributes.HEADER_REQUEST_ID, "cluster-connection-test-request"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestAttributes.HEADER_REQUEST_ID, "cluster-connection-test-request"))
                .andExpect(jsonPath("$.clusterId").value(clusterId))
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.kubernetesVersion").value("v1.30.0"))
                .andExpect(jsonPath("$.namespaces[0]").value("default"))
                .andExpect(jsonPath("$.checkedAt", notNullValue()));
    }

    /** ClusterConnectionTestApiTest의 listsNamespacesAndNodesFromKubernetesApi 처리 결과를 조회해 반환한다. */
    @Test
    void listsNamespacesAndNodesFromKubernetesApi() throws Exception {
        String clusterId = registerCluster("runtime-query-cluster");

        mockMvc.perform(get("/api/clusters/{clusterId}/namespaces", clusterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("default"))
                .andExpect(jsonPath("$[0].status").value("Active"));

        mockMvc.perform(get("/api/clusters/{clusterId}/nodes", clusterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("worker-1"))
                .andExpect(jsonPath("$[0].status").value("Ready"))
                .andExpect(jsonPath("$[0].kubernetesVersion").value("v1.30.0"));
    }

    /** ClusterConnectionTestApiTest의 returnsNotFoundWhenClusterDoesNotExist 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void returnsNotFoundWhenClusterDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/clusters/{clusterId}/connection-test", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    /** ClusterConnectionTestApiTest의 registerCluster 처리에 필요한 데이터를 생성하거나 저장한다. */
    private String registerCluster(String name) throws Exception {
        String response = mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "cluster for connection test",
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
    static class TestKubernetesConfig {

        /** TestKubernetesConfig의 kubernetesClusterPort 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        @Primary
        KubernetesClusterPort kubernetesClusterPort() {
            return new KubernetesClusterPort() {
                /** 익명 구현체의 testConnection 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesConnectionTestResult testConnection(KubernetesConnectionCredential credential) {
                    if (credential.payload().contains("apiVersion: v1")) {
                        return KubernetesConnectionTestResult.success("v1.30.0", List.of("default", "kube-system"));
                    }
                    return KubernetesConnectionTestResult.failure("Kubernetes API connection failed: invalid test credential");
                }

                /** 익명 구현체의 listNamespaces 처리 결과를 조회해 반환한다. */
                @Override
                public List<KubernetesNamespace> listNamespaces(KubernetesConnectionCredential credential) {
                    return List.of(new KubernetesNamespace("default", "Active"));
                }

                /** 익명 구현체의 listNodes 처리 결과를 조회해 반환한다. */
                @Override
                public List<KubernetesNode> listNodes(KubernetesConnectionCredential credential) {
                    return List.of(new KubernetesNode("worker-1", "Ready", "v1.30.0", "Ubuntu 22.04", "containerd://1.7"));
                }
            };
        }
    }
}
