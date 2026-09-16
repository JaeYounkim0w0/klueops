package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.KubernetesClusterPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesConnectionTestResult;
import io.strato.aiops.application.port.out.KubernetesNamespace;
import io.strato.aiops.application.port.out.KubernetesNode;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
public class Fabric8KubernetesClusterAdapter implements KubernetesClusterPort {

    private static final int NAMESPACE_SAMPLE_SIZE = 20;

    private final ObjectMapper objectMapper;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final int runtimeLookupTimeoutMs;

    /** Fabric8KubernetesClusterAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public Fabric8KubernetesClusterAdapter(
            ObjectMapper objectMapper,
            @Value("${aiops.kubernetes.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${aiops.kubernetes.request-timeout-ms:10000}") int requestTimeoutMs,
            @Value("${aiops.kubernetes.runtime-lookup-timeout-ms:5000}") int runtimeLookupTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.runtimeLookupTimeoutMs = runtimeLookupTimeoutMs;
    }

    /** Fabric8KubernetesClusterAdapter의 testConnection 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public KubernetesConnectionTestResult testConnection(KubernetesConnectionCredential credential) {
        try (KubernetesClient client = createClient(credential)) {
            List<String> namespaces = client.namespaces().list().getItems().stream()
                    .map(namespace -> namespace.getMetadata().getName())
                    .limit(NAMESPACE_SAMPLE_SIZE)
                    .toList();
            String kubernetesVersion = safeKubernetesVersion(client);
            return KubernetesConnectionTestResult.success(kubernetesVersion, namespaces);
        } catch (RuntimeException exception) {
            return KubernetesConnectionTestResult.failure(connectionFailureMessage(exception));
        }
    }

    /** Fabric8KubernetesClusterAdapter의 listNamespaces 처리 결과를 조회해 반환한다. */
    @Override
    public List<KubernetesNamespace> listNamespaces(KubernetesConnectionCredential credential) {
        try (KubernetesClient client = createClient(credential)) {
            return callWithTimeout(client, () -> client.namespaces().list().getItems().stream()
                            .map(namespace -> new KubernetesNamespace(
                                    namespace.getMetadata().getName(),
                                    namespace.getStatus() == null ? null : namespace.getStatus().getPhase()
                            ))
                            .toList(),
                    "namespaces");
        } catch (RuntimeException exception) {
            if (exception instanceof KubernetesApiException) {
                throw exception;
            }
            throw new KubernetesApiException(connectionFailureMessage(exception), exception);
        }
    }

    /** Fabric8KubernetesClusterAdapter의 listNodes 처리 결과를 조회해 반환한다. */
    @Override
    public List<KubernetesNode> listNodes(KubernetesConnectionCredential credential) {
        try (KubernetesClient client = createClient(credential)) {
            return callWithTimeout(client, () -> client.nodes().list().getItems().stream()
                            .map(node -> new KubernetesNode(
                                    node.getMetadata().getName(),
                                    node.getStatus() == null || node.getStatus().getConditions() == null ? null : node.getStatus().getConditions().stream()
                                            .filter(condition -> "Ready".equals(condition.getType()))
                                            .findFirst()
                                            .map(condition -> "True".equals(condition.getStatus()) ? "Ready" : "NotReady")
                                            .orElse(null),
                                    node.getStatus() == null || node.getStatus().getNodeInfo() == null ? null : node.getStatus().getNodeInfo().getKubeletVersion(),
                                    node.getStatus() == null || node.getStatus().getNodeInfo() == null ? null : node.getStatus().getNodeInfo().getOsImage(),
                                    node.getStatus() == null || node.getStatus().getNodeInfo() == null ? null : node.getStatus().getNodeInfo().getContainerRuntimeVersion()
                            ))
                            .toList(),
                    "nodes");
        } catch (RuntimeException exception) {
            if (exception instanceof KubernetesApiException) {
                throw exception;
            }
            throw new KubernetesApiException(connectionFailureMessage(exception), exception);
        }
    }

    /** Fabric8KubernetesClusterAdapter의 metricsApiAvailable 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public boolean metricsApiAvailable(KubernetesConnectionCredential credential) {
        try (KubernetesClient client = createClient(credential)) {
            return callWithTimeout(client, () -> {
                var service = client.apiServices().withName("v1beta1.metrics.k8s.io").get();
                if (service == null || service.getStatus() == null || service.getStatus().getConditions() == null) {
                    return false;
                }
                return service.getStatus().getConditions().stream()
                        .anyMatch(condition -> "Available".equals(condition.getType())
                                && "True".equals(condition.getStatus()));
            }, "metrics APIService");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Fabric8KubernetesClusterAdapter의 callWithTimeout 처리에 필요한 업무 로직을 수행한다. */
    private <T> T callWithTimeout(KubernetesClient client, Supplier<T> supplier, String label) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(supplier::get).get(runtimeLookupTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            client.close();
            throw new KubernetesApiException("Kubernetes API " + label + " lookup timed out after " + runtimeLookupTimeoutMs + "ms", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KubernetesApiException("Kubernetes API " + label + " lookup interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new KubernetesApiException("Kubernetes API " + label + " lookup failed", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Fabric8KubernetesClusterAdapter의 createClient 처리에 필요한 데이터를 생성하거나 저장한다. */
    private KubernetesClient createClient(KubernetesConnectionCredential credential) {
        if (credential.credentialType() == ClusterCredentialType.KUBECONFIG) {
            Config config = Config.fromKubeconfig(credential.payload());
            applyTimeouts(config);
            return new KubernetesClientBuilder().withConfig(config).build();
        }

        ServiceAccountPayload payload = parseServiceAccountPayload(credential.payload());
        Config config = new ConfigBuilder()
                .withMasterUrl(payload.apiServerUrl())
                .withOauthToken(payload.token())
                .withCaCertData(normalizeCertificateAuthority(payload.caCertificate()))
                .withConnectionTimeout(connectTimeoutMs)
                .withRequestTimeout(requestTimeoutMs)
                .build();
        return new KubernetesClientBuilder().withConfig(config).build();
    }

    /** Fabric8KubernetesClusterAdapter의 applyTimeouts 처리에 필요한 업무 로직을 수행한다. */
    private void applyTimeouts(Config config) {
        config.setConnectionTimeout(connectTimeoutMs);
        config.setRequestTimeout(requestTimeoutMs);
    }

    /** Fabric8KubernetesClusterAdapter의 safeKubernetesVersion 처리에 필요한 업무 로직을 수행한다. */
    private String safeKubernetesVersion(KubernetesClient client) {
        try {
            var versionInfo = client.getKubernetesVersion();
            return versionInfo == null ? null : versionInfo.getGitVersion();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Fabric8KubernetesClusterAdapter의 connectionFailureMessage 처리에 필요한 업무 로직을 수행한다. */
    private String connectionFailureMessage(RuntimeException exception) {
        Throwable rootCause = rootCause(exception);
        String detail = rootCause.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = rootCause.getClass().getSimpleName();
        }
        detail = detail.replaceAll("\\s+", " ");
        if (detail.length() > 300) {
            detail = detail.substring(0, 300) + "...";
        }
        return "Kubernetes API connection failed: " + detail;
    }

    /** Fabric8KubernetesClusterAdapter의 rootCause 처리에 필요한 업무 로직을 수행한다. */
    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /** Fabric8KubernetesClusterAdapter의 parseServiceAccountPayload 처리 데이터를 필요한 표현으로 변환한다. */
    private ServiceAccountPayload parseServiceAccountPayload(String payload) {
        try {
            return objectMapper.readValue(payload, ServiceAccountPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid ServiceAccount credential payload", exception);
        }
    }

    /** Fabric8KubernetesClusterAdapter의 normalizeCertificateAuthority 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizeCertificateAuthority(String caCertificate) {
        if (caCertificate == null || caCertificate.isBlank()) {
            return null;
        }
        if (caCertificate.contains("BEGIN CERTIFICATE")) {
            return Base64.getEncoder().encodeToString(caCertificate.getBytes(StandardCharsets.UTF_8));
        }
        return caCertificate;
    }

    private record ServiceAccountPayload(
            String apiServerUrl,
            String caCertificate,
            String token
    ) {
    }
}
