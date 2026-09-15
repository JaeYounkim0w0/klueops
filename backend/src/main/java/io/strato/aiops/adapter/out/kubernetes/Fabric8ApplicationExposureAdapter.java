package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.strato.aiops.application.port.out.ApplicationExposurePort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
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
        try (KubernetesClient client = client(credential)) {
            validateBackendService(client, request);
            GenericKubernetesResource gateway = requireGateway(client, request);
            String scheme = gatewayScheme(gateway);
            GenericKubernetesResource route = route(request, scheme);
            // 임의 YAML을 받지 않고 검증된 typed 입력만으로 companion resource를 조립한다.
            client.resource(route).inNamespace(request.namespace()).fieldManager("klueops").serverSideApply();
            return new ExposureResult(scheme + "://" + request.hostname() + normalizedPath(request.path()), "APPLIED");
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

    @Override
    public GatewayDiscovery discoverHttpGateways(KubernetesConnectionCredential credential) {
        try (KubernetesClient client = client(credential)) {
            List<GatewayOption> gateways = client.genericKubernetesResources(
                            "gateway.networking.k8s.io/v1", "Gateway")
                    .inAnyNamespace().list().getItems().stream()
                    .map(this::gatewayOption)
                    .filter(option -> !option.listeners().isEmpty())
                    .sorted(java.util.Comparator.comparing(GatewayOption::namespace)
                            .thenComparing(GatewayOption::name))
                    .toList();
            String status = gateways.isEmpty() ? "EMPTY" : "AVAILABLE";
            return new GatewayDiscovery(status, gateways.isEmpty()
                    ? "No Gateway with an HTTP or HTTPS listener was found" : null, gateways);
        } catch (KubernetesClientException exception) {
            // Gateway API 미설치와 조회 권한 부족을 사용자 선택 화면의 부분 결과로 전달한다.
            String message = switch (exception.getCode()) {
                case 401 -> "클러스터 자격증명이 만료되었거나 유효하지 않습니다";
                case 403 -> "등록한 클러스터 자격증명에 Gateway get/list 권한이 없습니다";
                case 404 -> "대상 클러스터에 Gateway API가 설치되어 있지 않습니다";
                default -> "대상 클러스터의 Gateway API를 조회할 수 없습니다";
            };
            return new GatewayDiscovery("UNAVAILABLE", message, List.of());
        } catch (RuntimeException exception) {
            return new GatewayDiscovery("UNAVAILABLE", "Gateway discovery failed", List.of());
        }
    }

    private GenericKubernetesResource route(HttpRouteRequest request, String scheme) {
        GenericKubernetesResource route = new GenericKubernetesResource();
        route.setApiVersion("gateway.networking.k8s.io/v1");
        route.setKind("HTTPRoute");
        route.setMetadata(new ObjectMetaBuilder().withName(request.routeName()).withNamespace(request.namespace())
                .addToLabels("app.kubernetes.io/managed-by", "klueops")
                .addToLabels("app.kubernetes.io/instance", request.routeName().replaceFirst("-klueops$", ""))
                .addToAnnotations("klueops.io/url-scheme", scheme).build());
        Map<String, Object> backend = Map.of("name", request.serviceName(), "port", request.servicePort());
        Map<String, Object> match = Map.of("path", Map.of("type", "PathPrefix", "value", normalizedPath(request.path())));
        Map<String, Object> parent = request.gatewayNamespace().equals(request.namespace())
                ? Map.of("group", "gateway.networking.k8s.io", "kind", "Gateway", "name", request.gatewayName())
                : Map.of("group", "gateway.networking.k8s.io", "kind", "Gateway", "name", request.gatewayName(),
                "namespace", request.gatewayNamespace());
        route.setAdditionalProperty("spec", Map.of("parentRefs", List.of(parent), "hostnames", List.of(request.hostname()),
                "rules", List.of(Map.of("matches", List.of(match), "backendRefs", List.of(backend)))));
        return route;
    }

    private void validateBackendService(KubernetesClient client, HttpRouteRequest request) {
        var service = client.services().inNamespace(request.namespace()).withName(request.serviceName()).get();
        boolean hasPort = service != null && service.getSpec() != null && service.getSpec().getPorts() != null
                && service.getSpec().getPorts().stream().anyMatch(port -> port.getPort() != null
                && port.getPort() == request.servicePort());
        if (!hasPort) {
            throw new IllegalArgumentException("HTTPRoute backend Service or port does not exist");
        }
    }

    private GenericKubernetesResource requireGateway(KubernetesClient client, HttpRouteRequest request) {
        GenericKubernetesResource gateway = client.genericKubernetesResources(
                        "gateway.networking.k8s.io/v1", "Gateway")
                .inNamespace(request.gatewayNamespace()).withName(request.gatewayName()).get();
        if (gateway == null) throw new IllegalArgumentException("HTTPRoute parent Gateway does not exist");
        return gateway;
    }

    private String gatewayScheme(GenericKubernetesResource gateway) {
        Object listenersValue = gateway.get("spec", "listeners");
        if (!(listenersValue instanceof List<?> listeners) || listeners.isEmpty()) {
            throw new IllegalArgumentException("Gateway has no listener");
        }
        boolean http = false;
        for (Object listenerValue : listeners) {
            if (!(listenerValue instanceof Map<?, ?> listener)) continue;
            String protocol = String.valueOf(listener.get("protocol"));
            if ("HTTPS".equalsIgnoreCase(protocol)) return "https";
            http |= "HTTP".equalsIgnoreCase(protocol);
        }
        if (http) return "http";
        throw new IllegalArgumentException("Gateway has no HTTP or HTTPS listener");
    }

    private GatewayOption gatewayOption(GenericKubernetesResource gateway) {
        List<GatewayListener> listeners = new ArrayList<>();
        Object listenerValue = gateway.get("spec", "listeners");
        if (listenerValue instanceof List<?> values) {
            for (Object value : values) {
                if (!(value instanceof Map<?, ?> listener)) continue;
                String protocol = String.valueOf(listener.get("protocol"));
                if (!"HTTP".equalsIgnoreCase(protocol) && !"HTTPS".equalsIgnoreCase(protocol)) continue;
                Object port = listener.get("port");
                Object listenerName = listener.get("name");
                listeners.add(new GatewayListener(listenerName == null ? "listener" : String.valueOf(listenerName),
                        protocol.toUpperCase(), port instanceof Number number ? number.intValue() : null,
                        listener.get("hostname") == null ? null : String.valueOf(listener.get("hostname"))));
            }
        }
        String namespace = gateway.getMetadata().getNamespace() == null ? "default" : gateway.getMetadata().getNamespace();
        return new GatewayOption(namespace, gateway.getMetadata().getName(), gatewayReadiness(gateway), listeners);
    }

    private String gatewayReadiness(GenericKubernetesResource gateway) {
        Object conditionsValue = gateway.get("status", "conditions");
        if (!(conditionsValue instanceof List<?> conditions)) return "UNKNOWN";
        boolean accepted = false;
        boolean programmed = false;
        for (Object conditionValue : conditions) {
            if (!(conditionValue instanceof Map<?, ?> condition)) continue;
            if (!"True".equals(condition.get("status"))) continue;
            accepted |= "Accepted".equals(condition.get("type"));
            programmed |= "Programmed".equals(condition.get("type"));
        }
        // Accepted만 참인 Gateway는 아직 dataplane에 반영되지 않았으므로 배포 대상으로 노출하지 않는다.
        return accepted && programmed ? "READY" : "NOT_READY";
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
