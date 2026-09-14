package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Fabric8KubernetesValidationLabAdapterTest {

    @Test
    void everyFixtureIsValidKubernetesYamlAndCarriesOwnershipLabels() {
        Fabric8KubernetesValidationLabAdapter adapter = new Fabric8KubernetesValidationLabAdapter(
                new ObjectMapper(), 1_000, 1_000, 1_000);
        try (var client = new KubernetesClientBuilder().withConfig(new ConfigBuilder()
                .withMasterUrl("https://127.0.0.1:6443").build()).build()) {
            for (String scenario : List.of("failed-mount", "crash-loop", "image-pull", "port-mismatch",
                    "probe-failure", "pvc-pending", "oom-risk", "rollback-guard")) {
                UUID runId = UUID.randomUUID();
                String manifest = adapter.manifest(scenario, runId);
                var resources = client.load(new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8))).items();
                assertThat(resources).as(scenario).isNotEmpty();
                assertThat(resources).allSatisfy(resource -> {
                    assertThat(resource.getMetadata().getLabels())
                            .containsEntry("aiops.platform/managed", "true")
                            .containsEntry("aiops.platform/validation-run", runId.toString());
                });
            }
        }
    }
}
