package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.strato.aiops.application.port.out.ApplicationRuntimeInspectionPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class Fabric8ApplicationRuntimeInspectionAdapter implements ApplicationRuntimeInspectionPort {
    private final ObjectMapper mapper;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;

    public Fabric8ApplicationRuntimeInspectionAdapter(ObjectMapper mapper,
            @Value("${aiops.kubernetes.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${aiops.kubernetes.request-timeout-ms:10000}") int requestTimeoutMs) {
        this.mapper = mapper;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
    }

    @Override
    public RuntimeOverview inspect(KubernetesConnectionCredential credential, String namespace, String releaseName) {
        try (KubernetesClient client = client(credential)) {
            List<Pod> pods = client.pods().inNamespace(namespace)
                    .withLabel("app.kubernetes.io/instance", releaseName).list().getItems();
            int ready = (int) pods.stream().filter(this::ready).count();
            int restarts = pods.stream().flatMap(pod -> pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null
                            ? java.util.stream.Stream.empty() : pod.getStatus().getContainerStatuses().stream())
                    .mapToInt(status -> status.getRestartCount() == null ? 0 : status.getRestartCount()).sum();
            List<Workload> workloads = new ArrayList<>();
            client.apps().deployments().inNamespace(namespace).withLabel("app.kubernetes.io/instance", releaseName)
                    .list().getItems().forEach(item -> workloads.add(new Workload("Deployment", item.getMetadata().getName(),
                            value(item.getStatus() == null ? null : item.getStatus().getReadyReplicas()),
                            value(item.getSpec() == null ? null : item.getSpec().getReplicas()), status(item.getStatus() == null ? null : item.getStatus().getReadyReplicas(), item.getSpec() == null ? null : item.getSpec().getReplicas()))));
            client.apps().statefulSets().inNamespace(namespace).withLabel("app.kubernetes.io/instance", releaseName)
                    .list().getItems().forEach(item -> workloads.add(new Workload("StatefulSet", item.getMetadata().getName(),
                            value(item.getStatus() == null ? null : item.getStatus().getReadyReplicas()),
                            value(item.getSpec() == null ? null : item.getSpec().getReplicas()), status(item.getStatus() == null ? null : item.getStatus().getReadyReplicas(), item.getSpec() == null ? null : item.getSpec().getReplicas()))));
            List<Endpoint> endpoints = new ArrayList<>();
            client.services().inNamespace(namespace).withLabel("app.kubernetes.io/instance", releaseName)
                    .list().getItems().forEach(service -> appendEndpoints(namespace, service, endpoints));
            // Chart마다 label 관례가 다르므로 namespace 범위에서 Helm annotation도 함께 확인한다.
            endpoints.addAll(ApplicationEndpointCollector.ingressEndpoints(
                    client.network().v1().ingresses().inNamespace(namespace).list().getItems(), releaseName));
            endpoints.addAll(ApplicationEndpointCollector.httpRouteEndpoints(httpRoutes(client, namespace), releaseName));
            return new RuntimeOverview(ready, pods.size(), restarts, List.copyOf(workloads), List.copyOf(endpoints));
        }
    }

    private List<io.fabric8.kubernetes.api.model.GenericKubernetesResource> httpRoutes(KubernetesClient client,
                                                                                       String namespace) {
        try {
            return client.genericKubernetesResources("gateway.networking.k8s.io/v1", "HTTPRoute")
                    .inNamespace(namespace).list().getItems();
        } catch (KubernetesClientException exception) {
            // Gateway API 미설치 Cluster에서는 Service/Ingress 상태 조회를 계속 제공한다.
            if (exception.getCode() == 403 || exception.getCode() == 404) return List.of();
            throw exception;
        }
    }

    private void appendEndpoints(String namespace, Service service, List<Endpoint> target) {
        if (service.getSpec() == null || service.getSpec().getPorts() == null) return;
        service.getSpec().getPorts().forEach(port -> {
            String scheme = "https".equalsIgnoreCase(port.getName()) || Integer.valueOf(443).equals(port.getPort()) ? "https" : "http";
            String name = service.getMetadata().getName();
            target.add(new Endpoint("CLUSTER_SERVICE", name,
                    scheme + "://" + name + "." + namespace + ".svc.cluster.local:" + port.getPort(), "INTERNAL"));
        });
    }

    private boolean ready(Pod pod) {
        return pod.getStatus() != null && pod.getStatus().getConditions() != null && pod.getStatus().getConditions().stream()
                .anyMatch(condition -> "Ready".equals(condition.getType()) && "True".equals(condition.getStatus()));
    }

    private int value(Integer value) { return value == null ? 0 : value; }
    private String status(Integer ready, Integer desired) { return value(ready) >= value(desired) ? "HEALTHY" : "DEGRADED"; }

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
