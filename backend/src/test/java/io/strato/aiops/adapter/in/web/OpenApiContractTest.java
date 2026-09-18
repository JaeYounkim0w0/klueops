package io.strato.aiops.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** OpenApiContractTest의 publishesCriticalOperationsWithResponseSchemas 처리 결과를 지정된 대상에 전달한다. */
    @Test
    void publishesCriticalOperationsWithResponseSchemas() throws Exception {
        String specification = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Path output = Path.of("target", "generated-openapi.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, specification);
        JsonNode root = objectMapper.readTree(specification);

        assertThat(root.path("openapi").asText()).startsWith("3.");
        List<String> criticalPaths = List.of(
                "/api/clusters",
                "/api/clusters/{clusterId}/connection-test",
                "/api/clusters/{clusterId}/resources/{resourceType}/{resourceName}/logs/targets",
                "/api/clusters/{clusterId}/resources/{resourceType}/{resourceName}/logs",
                "/api/clusters/{clusterId}/resources/{resourceType}/{resourceName}/logs/stream",
                "/api/clusters/{clusterId}/command-capabilities",
                "/api/clusters/{clusterId}/commands/validate",
                "/api/clusters/{clusterId}/command-executions",
                "/api/clusters/{clusterId}/command-sessions",
                "/api/clusters/{clusterId}/command-favorites",
                "/api/analysis/namespaces/{namespace}",
                "/api/jobs/{jobId}",
                "/api/v2/application-delivery/values-assistance/jobs",
                "/api/v2/application-delivery/values-assistance/jobs/{id}",
                "/api/v2/application-delivery/values-assistance/jobs/{id}/result",
                "/api/operations/runtime-readiness"
        );
        for (String path : criticalPaths) {
            JsonNode operations = root.path("paths").path(path);
            assertThat(operations.isObject())
                    .as("OpenAPI path %s must be published", path)
                    .isTrue();
            assertThat(operations.elements().hasNext())
                    .as("OpenAPI path %s must contain an operation", path)
                    .isTrue();
            operations.elements().forEachRemaining(operation -> {
                assertThat(operation.path("responses").isObject())
                        .as("OpenAPI path %s must document responses", path)
                        .isTrue();
            });
        }

        assertThat(root.path("components").path("schemas").isObject()).isTrue();
    }
}
