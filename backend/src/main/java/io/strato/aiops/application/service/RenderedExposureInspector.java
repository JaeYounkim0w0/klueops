package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helm 렌더 결과에 Chart가 직접 관리하는 노출 리소스가 있는지 검사한다.
 */
final class RenderedExposureInspector {
    private static final Pattern ROUTABLE_KIND = Pattern.compile(
            "(?m)^kind:\\s*(Ingress|HTTPRoute)\\s*(?:#.*)?$");
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private RenderedExposureInspector() { }

    static Detection detect(String manifest) {
        boolean ingress = false;
        boolean httpRoute = false;
        Matcher matcher = ROUTABLE_KIND.matcher(manifest == null ? "" : manifest);
        while (matcher.find()) {
            ingress |= "Ingress".equals(matcher.group(1));
            httpRoute |= "HTTPRoute".equals(matcher.group(1));
        }
        return new Detection(ingress, httpRoute);
    }

    static void requireChartManagedExposure(String manifest) {
        if (!detect(manifest).present()) {
            throw new IllegalArgumentException(
                    "Chart-managed exposure requires the rendered Chart to contain an Ingress or HTTPRoute");
        }
    }

    static List<ServiceOption> services(String manifest, String defaultNamespace) {
        List<ServiceOption> services = new ArrayList<>();
        try (MappingIterator<JsonNode> documents = YAML.readerFor(JsonNode.class)
                .readValues(manifest == null ? "" : manifest)) {
            while (documents.hasNextValue()) {
                JsonNode document = documents.nextValue();
                if (!"Service".equals(document.path("kind").asText())) continue;
                String name = document.path("metadata").path("name").asText();
                if (name.isBlank()) continue;
                String namespace = document.path("metadata").path("namespace").asText(defaultNamespace);
                String type = document.path("spec").path("type").asText("ClusterIP");
                JsonNode ports = document.path("spec").path("ports");
                if (!ports.isArray()) continue;
                for (JsonNode port : ports) {
                    if (!port.path("port").canConvertToInt()) continue;
                    JsonNode targetPort = port.path("targetPort");
                    services.add(new ServiceOption(namespace, name, type, port.path("name").asText(null),
                            port.path("port").asInt(), targetPort.isMissingNode() ? null : targetPort.asText(),
                            port.path("nodePort").canConvertToInt() ? port.path("nodePort").asInt() : null));
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Rendered Helm manifest is not valid YAML", exception);
        }
        return services.stream()
                .sorted(Comparator.comparing(ServiceOption::name).thenComparingInt(ServiceOption::port))
                .distinct()
                .toList();
    }

    static void requireService(String manifest, String namespace, String name, int port) {
        if (services(manifest, namespace).stream()
                .noneMatch(item -> item.namespace().equals(namespace) && item.name().equals(name) && item.port() == port)) {
            throw new IllegalArgumentException("HTTPRoute backend Service and port are not present in the rendered Chart");
        }
    }

    record Detection(boolean ingress, boolean httpRoute) {
        boolean present() { return ingress || httpRoute; }
    }

    record ServiceOption(String namespace, String name, String type, String portName, int port,
                         String targetPort, Integer nodePort) { }
}
