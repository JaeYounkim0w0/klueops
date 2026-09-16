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

    /** ArtifactHubChartCatalogAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ArtifactHubChartCatalogAdapter(ObjectMapper objectMapper,
                                          @Value("${aiops.application-delivery.artifact-hub-url:https://artifacthub.io/api/v1}") URI baseUri,
                                          @Value("${aiops.application-delivery.catalog-timeout:10s}") Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.baseUri = baseUri;
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(requestTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** ArtifactHubChartCatalogAdapter의 search 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public List<CatalogPackage> search(String query, int limit) {
        int bounded = Math.max(1, Math.min(limit, 50));
        String encoded = URLEncoder.encode(query == null ? "" : query.trim(), StandardCharsets.UTF_8);
        JsonNode root = get("/packages/search?kind=0&ts_query_web=" + encoded + "&limit=" + bounded + "&offset=0");
        List<CatalogPackage> result = new ArrayList<>();
        root.path("packages").forEach(node -> result.add(packageSummary(node)));
        return List.copyOf(result);
    }

    /** ArtifactHubChartCatalogAdapter의 details 처리에 필요한 업무 로직을 수행한다. */
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

    /** ArtifactHubChartCatalogAdapter의 packageSummary 처리에 필요한 업무 로직을 수행한다. */
    private CatalogPackage packageSummary(JsonNode node) {
        JsonNode repo = node.path("repository");
        return new CatalogPackage(node.path("package_id").asText(), repo.path("name").asText(),
                repo.path("display_name").asText(repo.path("name").asText()), repo.path("url").asText(),
                node.path("name").asText(), node.path("description").asText(), node.path("version").asText(),
                node.path("app_version").asText(null), node.path("content_url").asText(null),
                repo.path("official").asBoolean(false), repo.path("verified_publisher").asBoolean(false), List.of());
    }

    /** ArtifactHubChartCatalogAdapter의 get 처리 결과를 조회해 반환한다. */
    private JsonNode get(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(resolveApiPath(path)).timeout(requestTimeout)
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

    /** ArtifactHubChartCatalogAdapter의 resolveApiPath 처리에 필요한 결과를 조합해 반환한다. */
    private URI resolveApiPath(String path) {
        // 선행 슬래시가 /api/v1 경로를 제거하지 않도록 기준 URI를 디렉터리 URI로 정규화한다.
        String normalizedBase = baseUri.toString().endsWith("/") ? baseUri.toString() : baseUri + "/";
        String relativePath = path.startsWith("/") ? path.substring(1) : path;
        return URI.create(normalizedBase).resolve(relativePath);
    }

    /** ArtifactHubChartCatalogAdapter의 segment 처리에 필요한 업무 로직을 수행한다. */
    private String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
