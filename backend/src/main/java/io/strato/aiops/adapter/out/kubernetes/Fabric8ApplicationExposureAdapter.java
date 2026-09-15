package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.strato.aiops.application.port.out.ApplicationExposurePort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class Fabric8ApplicationExposureAdapter implements ApplicationExposurePort {
    private final ObjectMapper mapper;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;

    public Fabric8ApplicationExposureAdapter(ObjectMapper mapper,
            @Value("${aiops.kubernetes.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${aiops.kubernetes.request-timeout-ms:10000}") int requestTimeoutMs) {
        this.mapper = mapper; this.connectTimeoutMs = connectTimeoutMs; this.requestTimeoutMs = requestTimeoutMs;
    }

    @Override
    public ExposureResult applyHttpRoute(KubernetesConnectionCredential credential, HttpRouteRequest request) {
        GenericKubernetesResource route = route(request);
        try (KubernetesClient client = client(credential)) {
            // 임의 YAML을 받지 않고 검증된 typed 입력만으로 companion resource를 조립한다.
            client.resource(route).inNamespace(request.namespace()).fieldManager("klueops").serverSideApply();
            return new ExposureResult("https://" + request.hostname() + normalizedPath(request.path()), "APPLIED");
        }
    }

    @Override
    public void deleteHttpRoute(KubernetesConnectionCredential credential, String namespace, String routeName) {
        GenericKubernetesResource route = new GenericKubernetesResource();
        route.setApiVersion("gateway.networking.k8s.io/v1");
        route.setKind("HTTPRoute");
        route.setMetadata(new ObjectMetaBuilder().withName(routeName).withNamespace(namespace).build());
        try (KubernetesClient client = client(credential)) {
            client.resource(route).inNamespace(namespace).delete();
        }
    }

    private GenericKubernetesResource route(HttpRouteRequest request) {
        GenericKubernetesResource route = new GenericKubernetesResource();
        route.setApiVersion("gateway.networking.k8s.io/v1");
        route.setKind("HTTPRoute");
        route.setMetadata(new ObjectMetaBuilder().withName(request.routeName()).withNamespace(request.namespace())
                .addToLabels("app.kubernetes.io/managed-by", "klueops")
                .addToLabels("app.kubernetes.io/instance", request.routeName().replaceFirst("-klueops$", "")).build());
        Map<String, Object> backend = Map.of("name", request.serviceName(), "port", request.servicePort());
        Map<String, Object> match = Map.of("path", Map.of("type", "PathPrefix", "value", normalizedPath(request.path())));
        Map<String, Object> parent = request.gatewayNamespace().equals(request.namespace())
                ? Map.of("name", request.gatewayName())
                : Map.of("name", request.gatewayName(), "namespace", request.gatewayNamespace());
        route.setAdditionalProperty("spec", Map.of("parentRefs", List.of(parent), "hostnames", List.of(request.hostname()),
                "rules", List.of(Map.of("matches", List.of(match), "backendRefs", List.of(backend)))));
        return route;
    }

    private String normalizedPath(String path) { return path == null || path.isBlank() ? "/" : path; }

    private KubernetesClient client(KubernetesConnectionCredential credential) {
        if (credential.credentialType() == ClusterCredentialType.KUBECONFIG) {
            Config config = Config.fromKubeconfig(credential.payload());
            config.setConnectionTimeout(connectTimeoutMs); config.setRequestTimeout(requestTimeoutMs);
            return new KubernetesClientBuilder().withConfig(config).build();
        }
        try {
            ServiceAccountPayload payload = mapper.readValue(credential.payload(), ServiceAccountPayload.class);
            String ca = payload.caCertificate() != null && payload.caCertificate().contains("BEGIN CERTIFICATE")
                    ? Base64.getEncoder().encodeToString(payload.caCertificate().getBytes(StandardCharsets.UTF_8))
                    : payload.caCertificate();
            Config config = new ConfigBuilder().withMasterUrl(payload.apiServerUrl()).withOauthToken(payload.token())
                    .withCaCertData(ca).withConnectionTimeout(connectTimeoutMs).withRequestTimeout(requestTimeoutMs).build();
            return new KubernetesClientBuilder().withConfig(config).build();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid ServiceAccount credential payload", exception);
        }
    }

    private record ServiceAccountPayload(String apiServerUrl, String token, String caCertificate) { }
}
