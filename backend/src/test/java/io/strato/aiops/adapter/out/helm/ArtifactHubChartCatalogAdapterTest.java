package io.strato.aiops.adapter.out.helm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactHubChartCatalogAdapterTest {

    @Test
    void keepsConfiguredApiBasePathWhenSearching() throws Exception {
        AtomicReference<String> requestedPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/packages/search", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            byte[] body = "{\"packages\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1");
            ArtifactHubChartCatalogAdapter adapter = new ArtifactHubChartCatalogAdapter(
                    new ObjectMapper(), baseUri, Duration.ofSeconds(2));

            assertThat(adapter.search("nginx", 10)).isEmpty();
            // 설정된 API prefix가 URI.resolve 과정에서 유실되지 않아야 한다.
            assertThat(requestedPath.get()).isEqualTo("/api/v1/packages/search");
        } finally {
            server.stop(0);
        }
    }
}
