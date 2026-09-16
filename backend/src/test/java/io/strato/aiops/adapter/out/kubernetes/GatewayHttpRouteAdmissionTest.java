package io.strato.aiops.adapter.out.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayHttpRouteAdmissionTest {
    /** GatewayHttpRouteAdmissionTest의 rejectsCrossNamespaceRouteWhenListenerAllowsSameNamespaceOnly 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsCrossNamespaceRouteWhenListenerAllowsSameNamespaceOnly() {
        GenericKubernetesResource gateway = gateway("gateway-system", Map.of(
                "namespaces", Map.of("from", "Same")));

        var listeners = GatewayHttpRouteAdmission.allowedListeners(gateway, "database", Map.of());

        assertThat(listeners).isEmpty();
    }

    /** GatewayHttpRouteAdmissionTest의 acceptsCrossNamespaceRouteWhenListenerAllowsAllNamespaces 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void acceptsCrossNamespaceRouteWhenListenerAllowsAllNamespaces() {
        GenericKubernetesResource gateway = gateway("gateway-system", Map.of(
                "namespaces", Map.of("from", "All")));

        var listeners = GatewayHttpRouteAdmission.allowedListeners(gateway, "database", Map.of());

        assertThat(listeners).singleElement().satisfies(listener -> {
            assertThat(listener.protocol()).isEqualTo("HTTP");
            assertThat(listener.port()).isEqualTo(80);
        });
    }

    /** GatewayHttpRouteAdmissionTest의 acceptsOnlyNamespacesMatchingAListenerSelector 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void acceptsOnlyNamespacesMatchingAListenerSelector() {
        GenericKubernetesResource gateway = gateway("gateway-system", Map.of(
                "namespaces", Map.of("from", "Selector", "selector", Map.of(
                        "matchLabels", Map.of("exposure", "allowed")))));

        assertThat(GatewayHttpRouteAdmission.allowedListeners(
                gateway, "database", Map.of("exposure", "allowed"))).hasSize(1);
        assertThat(GatewayHttpRouteAdmission.allowedListeners(
                gateway, "database", Map.of("exposure", "denied"))).isEmpty();
    }

    /** TCP listener가 TCPRoute kind만 허용할 때 HTTPRoute와 구분해 승인한다. */
    @Test
    void distinguishesTcpRouteKindAndProtocol() {
        GenericKubernetesResource gateway = new GenericKubernetesResource();
        gateway.setApiVersion("gateway.networking.k8s.io/v1");
        gateway.setKind("Gateway");
        gateway.setMetadata(new ObjectMetaBuilder().withName("database").withNamespace("gateway-system").build());
        gateway.setAdditionalProperty("spec", Map.of("listeners", List.of(Map.of("name", "postgres", "protocol", "TCP",
                "port", 5432, "allowedRoutes", Map.of("namespaces", Map.of("from", "All"), "kinds", List.of(
                        Map.of("group", "gateway.networking.k8s.io", "kind", "TCPRoute")))))));

        assertThat(GatewayHttpRouteAdmission.allowedListeners(gateway, "database", Map.of())).isEmpty();
        assertThat(GatewayHttpRouteAdmission.allowedListeners(gateway, "database", Map.of(),
                "TCPRoute", List.of("TCP"))).singleElement().satisfies(listener -> {
                    assertThat(listener.protocol()).isEqualTo("TCP");
                    assertThat(listener.port()).isEqualTo(5432);
                });
    }

    /** GatewayHttpRouteAdmissionTest의 gateway 처리에 필요한 업무 로직을 수행한다. */
    private GenericKubernetesResource gateway(String namespace, Map<String, ?> allowedRoutes) {
        GenericKubernetesResource gateway = new GenericKubernetesResource();
        gateway.setApiVersion("gateway.networking.k8s.io/v1");
        gateway.setKind("Gateway");
        gateway.setMetadata(new ObjectMetaBuilder().withName("shared").withNamespace(namespace).build());
        gateway.setAdditionalProperty("spec", Map.of("listeners", List.of(Map.of(
                "name", "http", "protocol", "HTTP", "port", 80, "allowedRoutes", allowedRoutes))));
        return gateway;
    }
}
