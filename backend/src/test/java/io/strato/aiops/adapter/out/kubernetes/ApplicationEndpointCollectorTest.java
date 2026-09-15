package io.strato.aiops.adapter.out.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationEndpointCollectorTest {
    @Test
    void collectsReadyTlsIngressOwnedByHelmAnnotation() {
        var ingress = new IngressBuilder()
                .withNewMetadata().withName("web").addToAnnotations("meta.helm.sh/release-name", "sample").endMetadata()
                .withNewSpec()
                .addNewTl().withHosts("web.example.test").endTl()
                .addNewRule().withHost("web.example.test").withNewHttp()
                .addNewPath().withPath("/app").withPathType("Prefix")
                .withNewBackend().withNewService().withName("sample")
                .withNewPort().withNumber(80).endPort().endService().endBackend()
                .endPath().endHttp().endRule()
                .endSpec()
                .withNewStatus().withNewLoadBalancer().addNewIngress().withIp("127.0.0.1").endIngress()
                .endLoadBalancer().endStatus()
                .build();

        var endpoints = ApplicationEndpointCollector.ingressEndpoints(List.of(ingress), "sample");

        assertThat(endpoints).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.type()).isEqualTo("INGRESS");
            assertThat(endpoint.url()).isEqualTo("https://web.example.test/app");
            assertThat(endpoint.status()).isEqualTo("READY");
        });
    }

    @Test
    void collectsHttpRouteAndMapsAcceptedConditions() {
        GenericKubernetesResource route = new GenericKubernetesResource();
        route.setApiVersion("gateway.networking.k8s.io/v1");
        route.setKind("HTTPRoute");
        route.setMetadata(new ObjectMetaBuilder().withName("sample-klueops")
                .addToLabels("app.kubernetes.io/instance", "sample")
                .addToAnnotations("klueops.io/url-scheme", "http").build());
        route.setAdditionalProperty("spec", Map.of(
                "hostnames", List.of("sample.example.test"),
                "rules", List.of(Map.of("matches", List.of(Map.of("path", Map.of("value", "/")))))));
        route.setAdditionalProperty("status", Map.of("parents", List.of(Map.of("conditions", List.of(
                Map.of("type", "Accepted", "status", "True"),
                Map.of("type", "ResolvedRefs", "status", "True"))))));

        var endpoints = ApplicationEndpointCollector.httpRouteEndpoints(List.of(route), "sample");

        assertThat(endpoints).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.type()).isEqualTo("HTTP_ROUTE");
            assertThat(endpoint.url()).isEqualTo("http://sample.example.test/");
            assertThat(endpoint.status()).isEqualTo("READY");
        });
    }
}
