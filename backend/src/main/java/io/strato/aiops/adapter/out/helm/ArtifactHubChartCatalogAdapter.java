package io.strato.aiops.adapter.out.helm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.ChartCatalogPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class ArtifactHubChartCatalogAdapter implements ChartCatalogPort {
    private final ObjectMapper objectMapper;
    private final HttpClient client;
    private final URI baseUri;
    private final Duration requestTimeout;

    public ArtifactHubChartCatalogAdapter(ObjectMapper objectMapper,
                                          @Value("${aiops.application-delivery.artifact-hub-url:https://artifacthub.io/api/v1}") URI baseUri,
                                          @Value("${aiops.application-delivery.catalog-timeout:10s}") Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.baseUri = baseUri;
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(requestTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public List<CatalogPackage> search(String query, int limit) {
        int bounded = Math.max(1, Math.min(limit, 50));
        String encoded = URLEncoder.encode(query == null ? "" : query.trim(), StandardCharsets.UTF_8);
        JsonNode root = get("/packages/search?kind=0&ts_query_web=" + encoded + "&limit=" + bounded + "&offset=0");
        List<CatalogPackage> result = new ArrayList<>();
        root.path("packages").forEach(node -> result.add(packageSummary(node)));
        return List.copyOf(result);
    }

    @Override
    public CatalogPackage details(String repository, String name, String version) {
        String path = "/packages/helm/" + segment(repository) + "/" + segment(name)
                + (version == null || version.isBlank() ? "" : "/" + segment(version));
        JsonNode node = get(path);
        JsonNode repo = node.path("repository");
        List<String> versions = new ArrayList<>();
        node.path("available_versions").forEach(item -> versions.add(item.path("version").asText()));
        return new CatalogPackage(node.path("package_id").asText(), repo.path("name").asText(repository),
                repo.path("display_name").asText(repository), repo.path("url").asText(), node.path("name").asText(name),
                node.path("description").asText(), node.path("version").asText(version),
                node.path("app_version").asText(null), node.path("content_url").asText(null),
                repo.path("official").asBoolean(false), repo.path("verified_publisher").asBoolean(false),
                List.copyOf(versions));
    }

    private CatalogPackage packageSummary(JsonNode node) {
        JsonNode repo = node.path("repository");
        return new CatalogPackage(node.path("package_id").asText(), repo.path("name").asText(),
                repo.path("display_name").asText(repo.path("name").asText()), repo.path("url").asText(),
                node.path("name").asText(), node.path("description").asText(), node.path("version").asText(),
                node.path("app_version").asText(null), node.path("content_url").asText(null),
                repo.path("official").asBoolean(false), repo.path("verified_publisher").asBoolean(false), List.of());
    }

    private JsonNode get(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(requestTimeout)
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Artifact Hub returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Artifact Hub request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Artifact Hub is unavailable", exception);
        }
    }

    private String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
