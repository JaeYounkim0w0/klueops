package io.strato.aiops.adapter.out.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.strato.aiops.application.port.out.ApplicationExposurePort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Gateway Listener가 대상 Namespace의 HTTPRoute 연결을 허용하는지 보수적으로 판정한다.
 */
final class GatewayHttpRouteAdmission {
    /** GatewayHttpRouteAdmission 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private GatewayHttpRouteAdmission() { }

    /** GatewayHttpRouteAdmission의 allowedListeners 처리에 필요한 업무 로직을 수행한다. */
    static List<ApplicationExposurePort.GatewayListener> allowedListeners(
            GenericKubernetesResource gateway,
            String routeNamespace,
            Map<String, String> routeNamespaceLabels
    ) {
        return allowedListeners(gateway, routeNamespace, routeNamespaceLabels, "HTTPRoute", List.of("HTTP", "HTTPS"));
    }

    /** Route kind와 listener protocol 조합을 함께 검사한다. */
    static List<ApplicationExposurePort.GatewayListener> allowedListeners(GenericKubernetesResource gateway,
            String routeNamespace, Map<String, String> routeNamespaceLabels, String routeKind,
            Collection<String> protocols) {
        List<ApplicationExposurePort.GatewayListener> listeners = new ArrayList<>();
        Object values = gateway.get("spec", "listeners");
        if (!(values instanceof List<?> rawListeners)) return listeners;
        String gatewayNamespace = gateway.getMetadata().getNamespace() == null
                ? "default" : gateway.getMetadata().getNamespace();
        for (Object value : rawListeners) {
            if (!(value instanceof Map<?, ?> listener)) continue;
            String protocol = text(listener.get("protocol"));
            if (protocols.stream().noneMatch(item -> item.equalsIgnoreCase(protocol))) continue;
            if (!allowsRouteKind(listener, routeKind) || !allowsNamespace(listener, gatewayNamespace,
                    routeNamespace, routeNamespaceLabels)) continue;
            Object port = listener.get("port");
            String name = listener.get("name") == null ? "listener" : text(listener.get("name"));
            listeners.add(new ApplicationExposurePort.GatewayListener(name, protocol.toUpperCase(),
                    port instanceof Number number ? number.intValue() : null,
                    listener.get("hostname") == null ? null : text(listener.get("hostname"))));
        }
        return listeners;
    }

    /** GatewayHttpRouteAdmission의 allowsHttpRouteKind 처리에 필요한 업무 로직을 수행한다. */
    private static boolean allowsRouteKind(Map<?, ?> listener, String routeKind) {
        Object allowedRoutes = listener.get("allowedRoutes");
        if (!(allowedRoutes instanceof Map<?, ?> allowed)) return true;
        Object kinds = allowed.get("kinds");
        if (!(kinds instanceof Collection<?> configuredKinds) || configuredKinds.isEmpty()) return true;
        return configuredKinds.stream().anyMatch(value -> value instanceof Map<?, ?> kind
                && routeKind.equals(text(kind.get("kind")))
                && (kind.get("group") == null || "gateway.networking.k8s.io".equals(text(kind.get("group")))));
    }

    /** GatewayHttpRouteAdmission의 allowsNamespace 처리에 필요한 업무 로직을 수행한다. */
    private static boolean allowsNamespace(Map<?, ?> listener, String gatewayNamespace, String routeNamespace,
                                           Map<String, String> routeNamespaceLabels) {
        Object allowedRoutes = listener.get("allowedRoutes");
        if (!(allowedRoutes instanceof Map<?, ?> allowed)) return gatewayNamespace.equals(routeNamespace);
        Object namespaces = allowed.get("namespaces");
        if (!(namespaces instanceof Map<?, ?> namespacePolicy)) return gatewayNamespace.equals(routeNamespace);
        String from = namespacePolicy.get("from") == null ? "Same" : text(namespacePolicy.get("from"));
        if ("All".equalsIgnoreCase(from)) return true;
        if ("Same".equalsIgnoreCase(from)) return gatewayNamespace.equals(routeNamespace);
        if (!"Selector".equalsIgnoreCase(from)) return false;
        return matchesSelector(namespacePolicy.get("selector"), routeNamespaceLabels);
    }

    /** GatewayHttpRouteAdmission의 matchesSelector 처리 조건의 충족 여부를 판단한다. */
    private static boolean matchesSelector(Object selectorValue, Map<String, String> labels) {
        if (!(selectorValue instanceof Map<?, ?> selector)) return false;
        Object matchLabels = selector.get("matchLabels");
        if (matchLabels instanceof Map<?, ?> requiredLabels) {
            for (Map.Entry<?, ?> entry : requiredLabels.entrySet()) {
                if (!text(entry.getValue()).equals(labels.get(text(entry.getKey())))) return false;
            }
        }
        Object expressions = selector.get("matchExpressions");
        if (!(expressions instanceof Collection<?> values)) return true;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> expression)) return false;
            String key = text(expression.get("key"));
            String operator = text(expression.get("operator"));
            List<String> expected = expression.get("values") instanceof Collection<?> configured
                    ? configured.stream().map(GatewayHttpRouteAdmission::text).toList() : List.of();
            String actual = labels.get(key);
            boolean matches = switch (operator) {
                case "In" -> actual != null && expected.contains(actual);
                case "NotIn" -> actual != null && !expected.contains(actual);
                case "Exists" -> actual != null;
                case "DoesNotExist" -> actual == null;
                default -> false;
            };
            if (!matches) return false;
        }
        return true;
    }

    /** GatewayHttpRouteAdmission의 text 처리에 필요한 업무 로직을 수행한다. */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
