package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helm 렌더 결과에 Chart가 직접 관리하는 노출 리소스가 있는지 검사한다.
 */
final class RenderedExposureInspector {
    private static final int DEFAULT_NODE_PORT_MIN = 30_000;
    private static final int DEFAULT_NODE_PORT_MAX = 32_767;
    private static final Pattern ROUTABLE_KIND = Pattern.compile(
            "(?m)^kind:\\s*(Ingress|HTTPRoute)\\s*(?:#.*)?$");
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Set<Integer> COMMON_HTTP_PORTS = Set.of(80, 443, 3000, 8000, 8080, 8443);
    private static final Set<Integer> COMMON_NON_HTTP_PORTS = Set.of(5432, 3306, 6379, 27017, 5672, 9092);
    private static final Set<String> NON_HTTP_NAMES = Set.of(
            "postgres", "postgresql", "mysql", "mariadb", "redis", "mongodb", "amqp", "kafka", "tcp");

    /** RenderedExposureInspector 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private RenderedExposureInspector() { }

    /** RenderedExposureInspector의 detect 처리에 필요한 업무 로직을 수행한다. */
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

    /** RenderedExposureInspector의 requireChartManagedExposure 처리 입력과 현재 상태의 유효성을 검증한다. */
    static void requireChartManagedExposure(String manifest) {
        if (!detect(manifest).present()) {
            throw new IllegalArgumentException(
                    "Chart-managed exposure requires the rendered Chart to contain an Ingress or HTTPRoute");
        }
    }

    /** RenderedExposureInspector의 services 처리에 필요한 업무 로직을 수행한다. */
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
                    int servicePort = port.path("port").asInt();
                    String portName = port.path("name").asText(null);
                    String protocol = port.path("protocol").asText("TCP");
                    String appProtocol = port.path("appProtocol").asText(null);
                    Compatibility compatibility = httpRouteCompatibility(protocol, appProtocol, portName, servicePort);
                    services.add(new ServiceOption(namespace, name, type, portName, servicePort,
                            targetPort.isMissingNode() ? null : targetPort.asText(),
                            port.path("nodePort").canConvertToInt() ? port.path("nodePort").asInt() : null,
                            protocol, appProtocol, compatibility.status(), compatibility.message()));
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

    /** 렌더링된 Service가 Kubernetes 기본 포트 계약을 만족하는지 배포 전에 검증한다. */
    static void requireValidServices(String manifest, String defaultNamespace) {
        for (ServiceOption service : services(manifest, defaultNamespace)) {
            if (service.port() < 1 || service.port() > 65_535) {
                throw new IllegalArgumentException("Rendered Service " + service.name() + " uses invalid port "
                        + service.port() + ". Service port must be between 1 and 65535");
            }
            if (service.nodePort() == null || service.nodePort() == 0) continue;
            if (!"NodePort".equals(service.type()) && !"LoadBalancer".equals(service.type())) {
                throw new IllegalArgumentException("Rendered Service " + service.name() + " sets nodePort "
                        + service.nodePort() + " while Service type is " + service.type()
                        + ". Set service.type to NodePort or LoadBalancer, or remove service.nodePort");
            }
            if (service.nodePort() < DEFAULT_NODE_PORT_MIN || service.nodePort() > DEFAULT_NODE_PORT_MAX) {
                throw new IllegalArgumentException("Rendered Service " + service.name() + " uses nodePort "
                        + service.nodePort() + ". Kubernetes default NodePort range is " + DEFAULT_NODE_PORT_MIN
                        + "-" + DEFAULT_NODE_PORT_MAX + "; update the Chart Custom Values before deployment");
            }
        }
    }

    /** RenderedExposureInspector의 requireService 처리 입력과 현재 상태의 유효성을 검증한다. */
    static void requireService(String manifest, String namespace, String name, int port) {
        if (findService(manifest, namespace, name, port) == null) {
            throw new IllegalArgumentException("HTTPRoute backend Service and port are not present in the rendered Chart");
        }
    }

    /** RenderedExposureInspector의 requireHttpService 처리 입력과 현재 상태의 유효성을 검증한다. */
    static void requireHttpService(String manifest, String namespace, String name, int port) {
        ServiceOption service = findService(manifest, namespace, name, port);
        if (service == null) {
            throw new IllegalArgumentException("HTTPRoute backend Service and port are not present in the rendered Chart");
        }
        if ("NON_HTTP".equals(service.httpRouteCompatibility())) {
            throw new IllegalArgumentException(service.compatibilityMessage());
        }
    }

    /** RenderedExposureInspector의 findService 처리 결과를 조회해 반환한다. */
    private static ServiceOption findService(String manifest, String namespace, String name, int port) {
        return services(manifest, namespace).stream()
                .filter(item -> item.namespace().equals(namespace) && item.name().equals(name) && item.port() == port)
                .findFirst().orElse(null);
    }

    /** RenderedExposureInspector의 httpRouteCompatibility 처리에 필요한 업무 로직을 수행한다. */
    private static Compatibility httpRouteCompatibility(String protocol, String appProtocol, String portName, int port) {
        if (!"TCP".equalsIgnoreCase(protocol)) {
            return new Compatibility("NON_HTTP", "HTTPRoute supports an HTTP application over TCP; selected Service uses "
                    + protocol);
        }
        String normalizedAppProtocol = normalized(appProtocol);
        if (!normalizedAppProtocol.isBlank()) {
            if (normalizedAppProtocol.contains("http") || normalizedAppProtocol.contains("h2c")) {
                return new Compatibility("HTTP", "Service appProtocol declares HTTP compatibility");
            }
            return new Compatibility("NON_HTTP", "Selected Service appProtocol '" + appProtocol
                    + "' is not compatible with HTTPRoute");
        }
        String normalizedName = normalized(portName);
        if (normalizedName.startsWith("http") || normalizedName.startsWith("web")) {
            return new Compatibility("HTTP", "Service port name indicates HTTP traffic");
        }
        if (NON_HTTP_NAMES.stream().anyMatch(normalizedName::contains) || COMMON_NON_HTTP_PORTS.contains(port)) {
            return new Compatibility("NON_HTTP", "Selected Service " + nameOrPort(portName, port)
                    + " is a non-HTTP TCP endpoint. Use port-forward, NodePort, LoadBalancer, or a TCPRoute-capable Gateway");
        }
        if (COMMON_HTTP_PORTS.contains(port)) {
            return new Compatibility("HTTP", "Service port is a common HTTP/HTTPS port");
        }
        return new Compatibility("UNKNOWN", "Service does not declare appProtocol or an HTTP port name; verify that it speaks HTTP");
    }

    /** RenderedExposureInspector의 normalized 처리 데이터를 필요한 표현으로 변환한다. */
    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** RenderedExposureInspector의 nameOrPort 처리에 필요한 업무 로직을 수행한다. */
    private static String nameOrPort(String portName, int port) {
        return portName == null || portName.isBlank() ? "port " + port : "port '" + portName + "' (" + port + ")";
    }

    record Detection(boolean ingress, boolean httpRoute) {
        /** Detection의 present 처리에 필요한 업무 로직을 수행한다. */
        boolean present() { return ingress || httpRoute; }
    }

    record ServiceOption(String namespace, String name, String type, String portName, int port,
                         String targetPort, Integer nodePort, String protocol, String appProtocol,
                         String httpRouteCompatibility, String compatibilityMessage) { }
    private record Compatibility(String status, String message) { }
}
