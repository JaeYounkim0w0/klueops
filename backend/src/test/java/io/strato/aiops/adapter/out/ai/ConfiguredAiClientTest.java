package io.strato.aiops.adapter.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.AiProviderConfigurationRepositoryPort;
import io.strato.aiops.domain.ai.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ConfiguredAiClientTest {
    /** Values 호출은 JSON 결과에 예산을 사용하고 별도 thinking trace를 요청하지 않는다. */
    @Test void requestsBoundedJsonWithoutThinkingForValues() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                    .setBody("{\"message\":{\"content\":\"{}\"}}"));
            UUID tenant = UUID.randomUUID(), profileId = UUID.randomUUID();
            var now = Instant.now();
            var profile = new AiProviderProfile(profileId, tenant, "local", "OLLAMA",
                    server.url("/").toString().replaceAll("/$", ""), null, "sample", "[]", true, false, "VALID", now, "test", now, now);
            var repo = (AiProviderConfigurationRepositoryPort) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{AiProviderConfigurationRepositoryPort.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "findRouting" -> List.of(new TenantAiRoutingPolicy(tenant, "HELM_VALUES", profileId,
                                "sample", null, null, false, 42000, 4096, "test", now));
                        case "findProfile" -> Optional.of(profile);
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            var mapper = new ObjectMapper();
            var client = new ConfiguredAiClient(repo, null, mapper, 16384, 30000, 600000);
            assertThat(client.complete(tenant, "HELM_VALUES", "contract", "evidence")).isPresent();
            var body = mapper.readTree(server.takeRequest().getBody().readUtf8());
            assertThat(body.path("format").path("anyOf").size()).isEqualTo(2);
            assertThat(body.path("think").asBoolean(true)).isFalse();
            assertThat(body.path("options").path("num_predict").asInt()).isGreaterThan(0).isLessThanOrEqualTo(4096);
        }
    }
}
