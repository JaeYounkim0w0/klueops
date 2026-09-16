package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KubernetesPortTopologyAnalyzer {

    private static final List<String> SELECTABLE_RESOURCE_TYPES =
            List.of("Pod", "Deployment", "StatefulSet", "DaemonSet", "ReplicaSet");
    private static final int MAX_SIGNALS = 12;

    private final ObjectMapper objectMapper;

    /** KubernetesPortTopologyAnalyzer 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public KubernetesPortTopologyAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** KubernetesPortTopologyAnalyzer의 analyze 처리의 핵심 작업 흐름을 실행한다. */
    public List<PortMismatchSignal> analyze(KubernetesNamespaceDiagnostics diagnostics, boolean hasPortStartupLog) {
        if (diagnostics == null || diagnostics.resources() == null) {
            return List.of();
        }
        List<PortMismatchSignal> signals = new ArrayList<>();
        List<KubernetesNamespaceDiagnostics.DiagnosticResource> selectableResources = diagnostics.resources().stream()
                .filter(resource -> resource != null && SELECTABLE_RESOURCE_TYPES.contains(resource.resourceType()))
                .toList();

        diagnostics.resources().stream()
                .filter(resource -> resource != null && "Service".equals(resource.resourceType()))
                .forEach(service -> analyzeService(service, selectableResources, hasPortStartupLog, signals));
        return signals.stream().limit(MAX_SIGNALS).toList();
    }

    /** KubernetesPortTopologyAnalyzer의 analyzeService 처리의 핵심 작업 흐름을 실행한다. */
    private void analyzeService(KubernetesNamespaceDiagnostics.DiagnosticResource service,
                                List<KubernetesNamespaceDiagnostics.DiagnosticResource> selectableResources,
                                boolean hasPortStartupLog,
                                List<PortMismatchSignal> signals) {
        JsonNode serviceSummary = readObject(service.summaryJson());
        Map<String, String> selector = stringMap(serviceSummary.path("selector"));
        if (selector.isEmpty()) {
            return;
        }
        List<KubernetesNamespaceDiagnostics.DiagnosticResource> matchedResources = selectableResources.stream()
                .filter(resource -> labelsMatch(selector, labels(resource)))
                .toList();
        if (matchedResources.isEmpty()) {
            return;
        }
        List<ContainerPortRef> declaredPorts = matchedResources.stream()
                .flatMap(resource -> containerPorts(resource).stream())
                .toList();
        JsonNode ports = serviceSummary.path("ports");
        if (!ports.isArray()) {
            return;
        }
        for (JsonNode port : ports) {
            String servicePort = text(port.path("port").asText());
            String targetPort = text(port.path("targetPort").asText());
            if (targetPort.isBlank()) {
                targetPort = servicePort;
            }
            String protocol = text(port.path("protocol").asText("TCP"));
            if (targetPort.isBlank() || matchesDeclaredPort(targetPort, protocol, declaredPorts)) {
                continue;
            }
            boolean targetIsNumber = targetPort.chars().allMatch(Character::isDigit);
            boolean strongSignal = !targetIsNumber || !declaredPorts.isEmpty();
            if (!strongSignal && !hasPortStartupLog) {
                continue;
            }
            String reason = targetIsNumber
                    ? "Service targetPort=" + targetPort + "가 selector 대상의 선언된 containerPort "
                    + declaredPorts + "와 일치하지 않습니다."
                    : "Service named targetPort=" + targetPort
                    + "와 같은 이름의 containerPort가 selector 대상에서 확인되지 않습니다.";
            signals.add(new PortMismatchSignal(
                    text(service.namespace()), service.resourceName(), servicePort, targetPort,
                    selectorString(selector),
                    matchedResources.stream()
                            .map(resource -> resource.resourceType() + "/" + resource.resourceName())
                            .distinct().toList(),
                    declaredPorts.stream().map(ContainerPortRef::display).distinct().toList(),
                    reason, strongSignal
            ));
        }
    }

    /** KubernetesPortTopologyAnalyzer의 matchesDeclaredPort 처리 조건의 충족 여부를 판단한다. */
    private boolean matchesDeclaredPort(String targetPort, String protocol, List<ContainerPortRef> declaredPorts) {
        boolean targetIsNumber = targetPort.chars().allMatch(Character::isDigit);
        return targetIsNumber
                ? declaredPorts.stream().anyMatch(ref -> ref.port() != null
                && String.valueOf(ref.port()).equals(targetPort) && protocolMatches(protocol, ref.protocol()))
                : declaredPorts.stream().anyMatch(ref -> ref.name().equals(targetPort)
                && protocolMatches(protocol, ref.protocol()));
    }

    /** KubernetesPortTopologyAnalyzer의 labels 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, String> labels(KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        JsonNode summary = readObject(resource.summaryJson());
        return stringMap("Pod".equals(resource.resourceType()) ? summary.path("labels") : summary.path("templateLabels"));
    }

    /** KubernetesPortTopologyAnalyzer의 containerPorts 처리에 필요한 업무 로직을 수행한다. */
    private List<ContainerPortRef> containerPorts(KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        JsonNode containers = readObject(resource.summaryJson()).path("containers");
        if (!containers.isArray()) {
            return List.of();
        }
        List<ContainerPortRef> refs = new ArrayList<>();
        for (JsonNode container : containers) {
            JsonNode ports = container.path("ports");
            if (!ports.isArray()) {
                continue;
            }
            for (JsonNode port : ports) {
                refs.add(new ContainerPortRef(
                        resource.resourceType(), resource.resourceName(), text(container.path("name").asText()),
                        text(port.path("name").asText()),
                        port.path("containerPort").isMissingNode() || port.path("containerPort").isNull()
                                ? null : port.path("containerPort").asInt(),
                        text(port.path("protocol").asText("TCP"))
                ));
            }
        }
        return refs;
    }

    /** KubernetesPortTopologyAnalyzer의 readObject 처리 결과를 조회해 반환한다. */
    private JsonNode readObject(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null ? "{}" : json);
            return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    /** KubernetesPortTopologyAnalyzer의 stringMap 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, String> stringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        node.properties().forEach(entry -> result.put(entry.getKey(), text(entry.getValue().asText())));
        return result;
    }

    /** KubernetesPortTopologyAnalyzer의 labelsMatch 처리에 필요한 업무 로직을 수행한다. */
    private boolean labelsMatch(Map<String, String> selector, Map<String, String> labels) {
        return !selector.isEmpty() && !labels.isEmpty()
                && selector.entrySet().stream().allMatch(entry -> text(entry.getValue()).equals(text(labels.get(entry.getKey()))));
    }

    /** KubernetesPortTopologyAnalyzer의 protocolMatches 처리에 필요한 업무 로직을 수행한다. */
    private boolean protocolMatches(String serviceProtocol, String containerProtocol) {
        return serviceProtocol.isBlank() || containerProtocol.isBlank() || serviceProtocol.equalsIgnoreCase(containerProtocol);
    }

    /** KubernetesPortTopologyAnalyzer의 selectorString 처리에 필요한 업무 로직을 수행한다. */
    private String selectorString(Map<String, String> selector) {
        return selector.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "," + right)
                .orElse("-");
    }

    /** KubernetesPortTopologyAnalyzer의 text 처리에 필요한 업무 로직을 수행한다. */
    private String text(String value) {
        return value == null ? "" : value;
    }

    public record PortMismatchSignal(String namespace, String serviceName, String servicePort, String targetPort,
                                     String selector, List<String> matchedResources,
                                     List<String> declaredContainerPorts, String reason, boolean strongSignal) {
    }

    private record ContainerPortRef(String resourceKind, String resourceName, String containerName, String name,
                                    Integer port, String protocol) {
        /** ContainerPortRef의 display 처리에 필요한 업무 로직을 수행한다. */
        private String display() {
            String named = name.isBlank() ? "" : name + ":";
            return resourceKind + "/" + resourceName + "#" + containerName + "=" + named
                    + (port == null ? "-" : port) + "/" + protocol;
        }
    }
}
