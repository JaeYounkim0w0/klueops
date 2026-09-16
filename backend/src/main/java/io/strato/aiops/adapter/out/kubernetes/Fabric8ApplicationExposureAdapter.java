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
import java.util.List;
import java.util.Map;

@Component
public class Fabric8ApplicationExposureAdapter implements ApplicationExposurePort {
    private final ObjectMapper mapper;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;

    /** Fabric8ApplicationExposureAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public Fabric8ApplicationExposureAdapter(ObjectMapper mapper,
            @Value("${aiops.kubernetes.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${aiops.kubernetes.request-timeout-ms:10000}") int requestTimeoutMs) {
        this.mapper = mapper; this.connectTimeoutMs = connectTimeoutMs; this.requestTimeoutMs = requestTimeoutMs;
    }

    /** Fabric8ApplicationExposureAdapter의 applyHttpRoute 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public ExposureResult applyHttpRoute(KubernetesConnectionCredential credential, HttpRouteRequest request) {
        try (KubernetesClient client = client(credential)) {
            validateBackendService(client, request);
            GatewayOption gateway = requireGateway(client, request);
            String scheme = gatewayScheme(gateway);
            GenericKubernetesResource route = route(request, scheme);
            // 임의 YAML을 받지 않고 검증된 typed 입력만으로 companion resource를 조립한다.
            client.resource(route).inNamespace(request.namespace()).fieldManager("klueops").serverSideApply();
            return new ExposureResult(scheme + "://" + request.hostname() + normalizedPath(request.path()), "APPLIED");
        }
    }

    /** Fabric8ApplicationExposureAdapter의 deleteHttpRoute 처리 대상과 관련 상태를 안전하게 정리한다. */
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

    /** Fabric8ApplicationExposureAdapter의 discoverHttpGateways 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public GatewayDiscovery discoverHttpGateways(KubernetesConnectionCredential credential, String routeNamespace) {
        try (KubernetesClient client = client(credential)) {
            Map<String, String> namespaceLabels = namespaceLabels(client, routeNamespace);
            List<GatewayOption> gateways = client.genericKubernetesResources(
                            "gateway.networking.k8s.io/v1", "Gateway")
                    .inAnyNamespace().list().getItems().stream()
                    .map(gateway -> gatewayOption(gateway, routeNamespace, namespaceLabels))
                    .filter(option -> !option.listeners().isEmpty())
                    .sorted(java.util.Comparator.comparing(GatewayOption::namespace)
                            .thenComparing(GatewayOption::name))
                    .toList();
            String status = gateways.isEmpty() ? "EMPTY" : "AVAILABLE";
            return new GatewayDiscovery(status, gateways.isEmpty()
                    ? "No ready HTTP/HTTPS Gateway accepts HTTPRoute from namespace '" + routeNamespace + "'" : null,
                    gateways);
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

    /** TCPRoute를 허용하는 TCP listener만 조회한다. */
    @Override
    public GatewayDiscovery discoverTcpGateways(KubernetesConnectionCredential credential, String routeNamespace) {
        try (KubernetesClient client = client(credential)) {
            Map<String, String> labels = namespaceLabels(client, routeNamespace);
            List<GatewayOption> gateways = client.genericKubernetesResources("gateway.networking.k8s.io/v1", "Gateway")
                    .inAnyNamespace().list().getItems().stream().map(gateway -> {
                        String namespace = gateway.getMetadata().getNamespace() == null ? "default" : gateway.getMetadata().getNamespace();
                        return new GatewayOption(namespace, gateway.getMetadata().getName(), gatewayReadiness(gateway),
                                GatewayHttpRouteAdmission.allowedListeners(gateway, routeNamespace, labels,
                                        "TCPRoute", List.of("TCP")));
                    }).filter(item -> !item.listeners().isEmpty()).toList();
            return new GatewayDiscovery(gateways.isEmpty() ? "EMPTY" : "AVAILABLE",
                    gateways.isEmpty() ? "No ready TCP Gateway accepts TCPRoute from namespace '" + routeNamespace + "'" : null,
                    gateways);
        } catch (RuntimeException exception) {
            return new GatewayDiscovery("UNAVAILABLE", "대상 클러스터의 TCP Gateway를 조회할 수 없습니다", List.of());
        }
    }

    /** KlueOps 관리형 Ingress를 생성한다. */
    @Override
    public ExposureResult applyIngress(KubernetesConnectionCredential credential, IngressRequest request) {
        GenericKubernetesResource ingress = new GenericKubernetesResource();
        ingress.setApiVersion("networking.k8s.io/v1"); ingress.setKind("Ingress");
        ingress.setMetadata(managedMetadata(request.name(), request.namespace()));
        ingress.setAdditionalProperty("spec", Map.of("rules", List.of(Map.of("host", request.hostname(), "http",
                Map.of("paths", List.of(Map.of("path", normalizedPath(request.path()), "pathType", "Prefix", "backend",
                        Map.of("service", Map.of("name", request.serviceName(), "port", Map.of("number", request.servicePort()))))))))));
        try (KubernetesClient client = client(credential)) {
            validateService(client, request.namespace(), request.serviceName(), request.servicePort());
            client.resource(ingress).inNamespace(request.namespace()).fieldManager("klueops").serverSideApply();
            return new ExposureResult("http://" + request.hostname() + normalizedPath(request.path()), "APPLIED");
        }
    }

    /** 관리형 Ingress를 제거한다. */
    @Override
    public void deleteIngress(KubernetesConnectionCredential credential, String namespace, String name) {
        deleteGeneric(credential, "networking.k8s.io/v1", "Ingress", namespace, name);
    }

    /** KlueOps 관리형 TCPRoute를 생성한다. */
    @Override
    public ExposureResult applyTcpRoute(KubernetesConnectionCredential credential, TcpRouteRequest request) {
        try (KubernetesClient client = client(credential)) {
            validateService(client, request.serviceNamespace(), request.serviceName(), request.servicePort());
            requireReferenceGrant(client, request.namespace(), request.serviceNamespace(), request.serviceName(), "TCPRoute");
            GenericKubernetesResource gateway = client.genericKubernetesResources("gateway.networking.k8s.io/v1", "Gateway")
                    .inNamespace(request.gatewayNamespace()).withName(request.gatewayName()).get();
            if (gateway == null) throw new IllegalArgumentException("TCPRoute parent Gateway does not exist");
            var listeners = GatewayHttpRouteAdmission.allowedListeners(gateway, request.namespace(),
                    namespaceLabels(client, request.namespace()), "TCPRoute", List.of("TCP"));
            if (listeners.isEmpty()) throw new IllegalArgumentException("Gateway listeners do not allow TCPRoute");
            GenericKubernetesResource route = new GenericKubernetesResource();
            route.setApiVersion("gateway.networking.k8s.io/v1alpha2"); route.setKind("TCPRoute");
            route.setMetadata(managedMetadata(request.routeName(), request.namespace()));
            Map<String, Object> parent = parent(request.namespace(), request.gatewayNamespace(), request.gatewayName());
            Map<String, Object> backend = backend(request.namespace(), request.serviceNamespace(), request.serviceName(), request.servicePort());
            route.setAdditionalProperty("spec", Map.of("parentRefs", List.of(parent), "rules", List.of(Map.of("backendRefs", List.of(backend)))));
            client.resource(route).inNamespace(request.namespace()).fieldManager("klueops").serverSideApply();
            Integer port = listeners.get(0).port();
            return new ExposureResult("tcp://" + request.gatewayName() + "." + request.gatewayNamespace()
                    + (port == null ? "" : ":" + port), "APPLIED");
        }
    }

    /** 관리형 TCPRoute를 제거한다. */
    @Override
    public void deleteTcpRoute(KubernetesConnectionCredential credential, String namespace, String name) {
        deleteGeneric(credential, "gateway.networking.k8s.io/v1alpha2", "TCPRoute", namespace, name);
    }

    /** cross-namespace backend Service와 ReferenceGrant를 사전 검증한다. */
    @Override
    public void requireBackendReference(KubernetesConnectionCredential credential, String routeNamespace,
                                        String serviceNamespace, String serviceName, int servicePort, String routeKind) {
        try (KubernetesClient client = client(credential)) {
            validateService(client, serviceNamespace, serviceName, servicePort);
            requireReferenceGrant(client, routeNamespace, serviceNamespace, serviceName, routeKind);
        }
    }

    /** 보존 선택 자원에 Helm keep annotation을 적용하고 계획 수량을 반환한다. */
    @Override
    public CleanupInventory prepareUninstall(KubernetesConnectionCredential credential, String namespace,
                                             String releaseName, boolean preservePvcs, boolean preserveTls) {
        try (KubernetesClient client = client(credential)) {
            var pvcs = client.persistentVolumeClaims().inNamespace(namespace)
                    .withLabel("app.kubernetes.io/instance", releaseName).list().getItems();
            var tlsSecrets = client.secrets().inNamespace(namespace).withLabel("app.kubernetes.io/instance", releaseName)
                    .list().getItems().stream().filter(item -> "kubernetes.io/tls".equals(item.getType())).toList();
            if (preservePvcs) pvcs.forEach(item -> annotateKeep(client, item));
            if (preserveTls) tlsSecrets.forEach(item -> annotateKeep(client, item));
            return new CleanupInventory(pvcs.size(), tlsSecrets.size());
        }
    }

    /** 보존하지 않기로 한 데이터 자원을 label 범위 안에서만 삭제한다. */
    @Override
    public void finalizeUninstall(KubernetesConnectionCredential credential, String namespace, String releaseName,
                                  boolean preservePvcs, boolean preserveTls) {
        try (KubernetesClient client = client(credential)) {
            if (!preservePvcs) client.persistentVolumeClaims().inNamespace(namespace)
                    .withLabel("app.kubernetes.io/instance", releaseName).delete();
            if (!preserveTls) client.secrets().inNamespace(namespace).withLabel("app.kubernetes.io/instance", releaseName)
                    .list().getItems().stream().filter(item -> "kubernetes.io/tls".equals(item.getType()))
                    .forEach(item -> client.resource(item).inNamespace(namespace).delete());
        }
    }

    /** Fabric8 resource에 Helm keep annotation을 병합한다. */
    private void annotateKeep(KubernetesClient client, io.fabric8.kubernetes.api.model.HasMetadata resource) {
        if (resource.getMetadata().getAnnotations() == null) resource.getMetadata().setAnnotations(new java.util.HashMap<>());
        resource.getMetadata().getAnnotations().put("helm.sh/resource-policy", "keep");
        client.resource(resource).inNamespace(resource.getMetadata().getNamespace()).update();
    }

    /** Fabric8ApplicationExposureAdapter의 route 처리에 필요한 업무 로직을 수행한다. */
    private GenericKubernetesResource route(HttpRouteRequest request, String scheme) {
        GenericKubernetesResource route = new GenericKubernetesResource();
        route.setApiVersion("gateway.networking.k8s.io/v1");
        route.setKind("HTTPRoute");
        route.setMetadata(new ObjectMetaBuilder().withName(request.routeName()).withNamespace(request.namespace())
                .addToLabels("app.kubernetes.io/managed-by", "klueops")
                .addToLabels("app.kubernetes.io/instance", request.routeName().replaceFirst("-klueops$", ""))
                .addToAnnotations("klueops.io/url-scheme", scheme).build());
        Map<String, Object> backend = backend(request.namespace(), request.serviceNamespace(), request.serviceName(), request.servicePort());
        Map<String, Object> match = Map.of("path", Map.of("type", "PathPrefix", "value", normalizedPath(request.path())));
        Map<String, Object> parent = request.gatewayNamespace().equals(request.namespace())
                ? Map.of("group", "gateway.networking.k8s.io", "kind", "Gateway", "name", request.gatewayName())
                : Map.of("group", "gateway.networking.k8s.io", "kind", "Gateway", "name", request.gatewayName(),
                "namespace", request.gatewayNamespace());
        route.setAdditionalProperty("spec", Map.of("parentRefs", List.of(parent), "hostnames", List.of(request.hostname()),
                "rules", List.of(Map.of("matches", List.of(match), "backendRefs", List.of(backend)))));
        return route;
    }

    /** Fabric8ApplicationExposureAdapter의 validateBackendService 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void validateBackendService(KubernetesClient client, HttpRouteRequest request) {
        var service = client.services().inNamespace(request.serviceNamespace()).withName(request.serviceName()).get();
        boolean hasPort = service != null && service.getSpec() != null && service.getSpec().getPorts() != null
                && service.getSpec().getPorts().stream().anyMatch(port -> port.getPort() != null
                && port.getPort() == request.servicePort());
        if (!hasPort) {
            throw new IllegalArgumentException("HTTPRoute backend Service or port does not exist");
        }
        requireReferenceGrant(client, request.namespace(), request.serviceNamespace(), request.serviceName(), "HTTPRoute");
    }

    /** Service와 port의 실제 존재를 확인한다. */
    private void validateService(KubernetesClient client, String namespace, String name, int port) {
        var service = client.services().inNamespace(namespace).withName(name).get();
        if (service == null || service.getSpec() == null || service.getSpec().getPorts() == null
                || service.getSpec().getPorts().stream().noneMatch(item -> item.getPort() != null && item.getPort() == port))
            throw new IllegalArgumentException("Backend Service or port does not exist");
    }

    /** 다른 Namespace의 Service 참조는 명시적 ReferenceGrant가 있을 때만 허용한다. */
    private void requireReferenceGrant(KubernetesClient client, String routeNamespace, String serviceNamespace,
                                       String serviceName, String routeKind) {
        if (routeNamespace.equals(serviceNamespace)) return;
        boolean granted = client.genericKubernetesResources("gateway.networking.k8s.io/v1beta1", "ReferenceGrant")
                .inNamespace(serviceNamespace).list().getItems().stream().anyMatch(grant -> {
                    Object fromValue = grant.get("spec", "from"); Object toValue = grant.get("spec", "to");
                    if (!(fromValue instanceof List<?> from) || !(toValue instanceof List<?> to)) return false;
                    boolean source = from.stream().anyMatch(item -> item instanceof Map<?, ?> value
                            && "gateway.networking.k8s.io".equals(String.valueOf(value.get("group")))
                            && routeKind.equals(String.valueOf(value.get("kind")))
                            && routeNamespace.equals(String.valueOf(value.get("namespace"))));
                    boolean target = to.stream().anyMatch(item -> item instanceof Map<?, ?> value
                            && "".equals(String.valueOf(value.get("group"))) && "Service".equals(String.valueOf(value.get("kind")))
                            && (value.get("name") == null || serviceName.equals(String.valueOf(value.get("name")))));
                    return source && target;
                });
        if (!granted) throw new IllegalArgumentException("Cross-namespace backend requires an explicit ReferenceGrant");
    }

    /** namespace 차이를 포함한 Gateway parentRef를 생성한다. */
    private Map<String, Object> parent(String routeNamespace, String gatewayNamespace, String gatewayName) {
        return routeNamespace.equals(gatewayNamespace)
                ? Map.of("group", "gateway.networking.k8s.io", "kind", "Gateway", "name", gatewayName)
                : Map.of("group", "gateway.networking.k8s.io", "kind", "Gateway", "name", gatewayName, "namespace", gatewayNamespace);
    }

    /** cross-namespace일 때만 namespace를 포함한 backendRef를 생성한다. */
    private Map<String, Object> backend(String routeNamespace, String serviceNamespace, String name, int port) {
        return routeNamespace.equals(serviceNamespace) ? Map.of("name", name, "port", port)
                : Map.of("name", name, "namespace", serviceNamespace, "port", port);
    }

    /** KlueOps 소유권 label을 일관되게 부여한다. */
    private io.fabric8.kubernetes.api.model.ObjectMeta managedMetadata(String name, String namespace) {
        return new ObjectMetaBuilder().withName(name).withNamespace(namespace)
                .addToLabels("app.kubernetes.io/managed-by", "klueops").build();
    }

    /** 종류별 companion resource 삭제를 공통 처리한다. */
    private void deleteGeneric(KubernetesConnectionCredential credential, String apiVersion, String kind,
                               String namespace, String name) {
        GenericKubernetesResource resource = new GenericKubernetesResource(); resource.setApiVersion(apiVersion);
        resource.setKind(kind); resource.setMetadata(new ObjectMetaBuilder().withName(name).withNamespace(namespace).build());
        try (KubernetesClient client = client(credential)) { client.resource(resource).inNamespace(namespace).delete(); }
    }

    /** Fabric8ApplicationExposureAdapter의 requireGateway 처리 입력과 현재 상태의 유효성을 검증한다. */
    private GatewayOption requireGateway(KubernetesClient client, HttpRouteRequest request) {
        GenericKubernetesResource gateway = client.genericKubernetesResources(
                        "gateway.networking.k8s.io/v1", "Gateway")
                .inNamespace(request.gatewayNamespace()).withName(request.gatewayName()).get();
        if (gateway == null) throw new IllegalArgumentException("HTTPRoute parent Gateway does not exist");
        GatewayOption option = gatewayOption(gateway, request.namespace(), namespaceLabels(client, request.namespace()));
        if (option.listeners().isEmpty()) {
            throw new IllegalArgumentException("Gateway listeners do not allow HTTPRoute from the application namespace");
        }
        if (!"READY".equals(option.readiness())) {
            throw new IllegalArgumentException("HTTPRoute parent Gateway is not ready");
        }
        return option;
    }

    /** Fabric8ApplicationExposureAdapter의 gatewayScheme 처리에 필요한 업무 로직을 수행한다. */
    private String gatewayScheme(GatewayOption gateway) {
        return gateway.listeners().stream().anyMatch(listener -> "HTTPS".equals(listener.protocol()))
                ? "https" : "http";
    }

    /** Fabric8ApplicationExposureAdapter의 gatewayOption 처리에 필요한 업무 로직을 수행한다. */
    private GatewayOption gatewayOption(GenericKubernetesResource gateway, String routeNamespace,
                                        Map<String, String> namespaceLabels) {
        List<GatewayListener> listeners = GatewayHttpRouteAdmission.allowedListeners(
                gateway, routeNamespace, namespaceLabels);
        String namespace = gateway.getMetadata().getNamespace() == null ? "default" : gateway.getMetadata().getNamespace();
        return new GatewayOption(namespace, gateway.getMetadata().getName(), gatewayReadiness(gateway), listeners);
    }

    /** Fabric8ApplicationExposureAdapter의 namespaceLabels 처리에 필요한 업무 로직을 수행한다. */
    private Map<String, String> namespaceLabels(KubernetesClient client, String namespace) {
        var value = client.namespaces().withName(namespace).get();
        return value == null || value.getMetadata() == null || value.getMetadata().getLabels() == null
                ? Map.of() : value.getMetadata().getLabels();
    }

    /** Fabric8ApplicationExposureAdapter의 gatewayReadiness 처리에 필요한 업무 로직을 수행한다. */
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

    /** Fabric8ApplicationExposureAdapter의 normalizedPath 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizedPath(String path) { return path == null || path.isBlank() ? "/" : path; }

    /** Fabric8ApplicationExposureAdapter의 client 처리에 필요한 업무 로직을 수행한다. */
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
