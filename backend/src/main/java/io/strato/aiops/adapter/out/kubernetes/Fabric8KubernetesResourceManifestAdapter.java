package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesResourceManifest;
import io.strato.aiops.application.port.out.KubernetesResourceManifestPort;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class Fabric8KubernetesResourceManifestAdapter implements KubernetesResourceManifestPort {

    private final ObjectMapper objectMapper;
    private final AiManifestSanitizer aiManifestSanitizer;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final int manifestLookupTimeoutMs;

    /** Fabric8KubernetesResourceManifestAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public Fabric8KubernetesResourceManifestAdapter(
            ObjectMapper objectMapper,
            AiManifestSanitizer aiManifestSanitizer,
            @Value("${aiops.kubernetes.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${aiops.kubernetes.request-timeout-ms:10000}") int requestTimeoutMs,
            @Value("${aiops.kubernetes.manifest-lookup-timeout-ms:5000}") int manifestLookupTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.aiManifestSanitizer = aiManifestSanitizer;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.manifestLookupTimeoutMs = manifestLookupTimeoutMs;
    }

    /** Fabric8KubernetesResourceManifestAdapter의 getResourceManifest 처리 결과를 조회해 반환한다. */
    @Override
    public KubernetesResourceManifest getResourceManifest(
            KubernetesConnectionCredential credential,
            String namespace,
            String resourceType,
            String resourceName
    ) {
        return getResourceManifest(credential, namespace, resourceType, resourceName, false);
    }

    /** Fabric8KubernetesResourceManifestAdapter의 getAiSafeResourceManifest 처리 결과를 조회해 반환한다. */
    @Override
    public KubernetesResourceManifest getAiSafeResourceManifest(
            KubernetesConnectionCredential credential, String namespace, String resourceType, String resourceName) {
        return getResourceManifest(credential, namespace, resourceType, resourceName, true);
    }

    /** Fabric8KubernetesResourceManifestAdapter의 getResourceManifest 처리 결과를 조회해 반환한다. */
    private KubernetesResourceManifest getResourceManifest(
            KubernetesConnectionCredential credential, String namespace, String resourceType, String resourceName,
            boolean aiSafe) {
        ResourceMapping mapping = ResourceMapping.from(resourceType);
        try (KubernetesClient client = createClient(credential)) {
            HasMetadata resource = fetchWithTimeout(client, mapping, namespace, resourceName);
            if (resource == null) {
                throw new NoSuchElementException("Kubernetes resource not found: " + resourceType + "/" + resourceName);
            }
            sanitize(resource, mapping, !aiSafe);
            return new KubernetesResourceManifest(
                    resource.getMetadata() == null ? namespace : resource.getMetadata().getNamespace(),
                    mapping.kind(),
                    resourceName,
                    aiSafe ? aiManifestSanitizer.sanitize(resource) : Serialization.asYaml(resource),
                    mapping.secret() || aiSafe,
                    Instant.now()
            );
        } catch (RuntimeException exception) {
            if (exception instanceof NoSuchElementException) {
                throw exception;
            }
            if (exception instanceof KubernetesApiException) {
                throw exception;
            }
            throw new KubernetesApiException(connectionFailureMessage(exception), exception);
        }
    }

    /** Fabric8KubernetesResourceManifestAdapter의 fetchWithTimeout 처리 결과를 조회해 반환한다. */
    private HasMetadata fetchWithTimeout(KubernetesClient client, ResourceMapping mapping, String namespace, String resourceName) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(() -> fetch(client, mapping, namespace, resourceName))
                    .get(manifestLookupTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            client.close();
            throw new KubernetesApiException("Kubernetes API manifest lookup timed out after " + manifestLookupTimeoutMs + "ms", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KubernetesApiException("Kubernetes API manifest lookup interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new KubernetesApiException("Kubernetes API manifest lookup failed", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Fabric8KubernetesResourceManifestAdapter의 fetch 처리 결과를 조회해 반환한다. */
    private HasMetadata fetch(KubernetesClient client, ResourceMapping mapping, String namespace, String resourceName) {
        if (mapping.namespaced() && (namespace == null || namespace.isBlank())) {
            throw new IllegalArgumentException(mapping.kind() + " namespace is required");
        }
        return switch (mapping.normalizedType()) {
            case "namespace" -> client.namespaces().withName(resourceName).get();
            case "node" -> client.nodes().withName(resourceName).get();
            case "persistentvolume" -> client.persistentVolumes().withName(resourceName).get();
            case "pod" -> client.pods().inNamespace(namespace).withName(resourceName).get();
            case "service" -> client.services().inNamespace(namespace).withName(resourceName).get();
            case "endpoint" -> client.endpoints().inNamespace(namespace).withName(resourceName).get();
            case "configmap" -> client.configMaps().inNamespace(namespace).withName(resourceName).get();
            case "secret" -> client.secrets().inNamespace(namespace).withName(resourceName).get();
            case "serviceaccount" -> client.serviceAccounts().inNamespace(namespace).withName(resourceName).get();
            case "persistentvolumeclaim" -> client.persistentVolumeClaims().inNamespace(namespace).withName(resourceName).get();
            case "deployment" -> client.apps().deployments().inNamespace(namespace).withName(resourceName).get();
            case "replicaset" -> client.apps().replicaSets().inNamespace(namespace).withName(resourceName).get();
            case "statefulset" -> client.apps().statefulSets().inNamespace(namespace).withName(resourceName).get();
            case "daemonset" -> client.apps().daemonSets().inNamespace(namespace).withName(resourceName).get();
            case "job" -> client.batch().v1().jobs().inNamespace(namespace).withName(resourceName).get();
            case "cronjob" -> client.batch().v1().cronjobs().inNamespace(namespace).withName(resourceName).get();
            case "ingress" -> client.network().v1().ingresses().inNamespace(namespace).withName(resourceName).get();
            case "networkpolicy" -> client.network().v1().networkPolicies().inNamespace(namespace).withName(resourceName).get();
            default -> throw new IllegalArgumentException("Unsupported Kubernetes resource type: " + mapping.kind());
        };
    }

    /** Fabric8KubernetesResourceManifestAdapter의 sanitize 처리에 필요한 업무 로직을 수행한다. */
    private void sanitize(HasMetadata resource, ResourceMapping mapping, boolean removeStatus) {
        ObjectMeta metadata = resource.getMetadata();
        if (metadata != null) {
            metadata.setManagedFields(null);
            metadata.setResourceVersion(null);
            metadata.setUid(null);
            metadata.setCreationTimestamp(null);
        }
        if (removeStatus) {
            stripStatus(resource);
        }
        if (mapping.secret() && resource instanceof Secret secret) {
            redactSecret(secret);
        }
    }

    /** Fabric8KubernetesResourceManifestAdapter의 stripStatus 처리에 필요한 업무 로직을 수행한다. */
    private void stripStatus(HasMetadata resource) {
        try {
            resource.getClass().getMethod("setStatus", resource.getClass().getMethod("getStatus").getReturnType())
                    .invoke(resource, new Object[]{null});
        } catch (ReflectiveOperationException ignored) {
            // Some resources do not expose status; YAML can still be rendered safely.
        }
    }

    /** Fabric8KubernetesResourceManifestAdapter의 redactSecret 처리에 필요한 업무 로직을 수행한다. */
    private void redactSecret(Secret secret) {
        if (secret.getData() != null) {
            Map<String, String> redacted = new LinkedHashMap<>();
            secret.getData().keySet().forEach(key -> redacted.put(key, "***REDACTED***"));
            secret.setData(redacted);
        }
        secret.setStringData(null);
    }

    /** Fabric8KubernetesResourceManifestAdapter의 createClient 처리에 필요한 데이터를 생성하거나 저장한다. */
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

    /** Fabric8KubernetesResourceManifestAdapter의 applyTimeouts 처리에 필요한 업무 로직을 수행한다. */
    private void applyTimeouts(Config config) {
        config.setConnectionTimeout(connectTimeoutMs);
        config.setRequestTimeout(requestTimeoutMs);
    }

    /** Fabric8KubernetesResourceManifestAdapter의 parseServiceAccountPayload 처리 데이터를 필요한 표현으로 변환한다. */
    private ServiceAccountPayload parseServiceAccountPayload(String payload) {
        try {
            return objectMapper.readValue(payload, ServiceAccountPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid ServiceAccount credential payload", exception);
        }
    }

    /** Fabric8KubernetesResourceManifestAdapter의 normalizeCertificateAuthority 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizeCertificateAuthority(String caCertificate) {
        if (caCertificate == null || caCertificate.isBlank()) {
            return null;
        }
        if (caCertificate.contains("BEGIN CERTIFICATE")) {
            return Base64.getEncoder().encodeToString(caCertificate.getBytes(StandardCharsets.UTF_8));
        }
        return caCertificate;
    }

    /** Fabric8KubernetesResourceManifestAdapter의 connectionFailureMessage 처리에 필요한 업무 로직을 수행한다. */
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
        return "Kubernetes API manifest lookup failed: " + detail;
    }

    /** Fabric8KubernetesResourceManifestAdapter의 rootCause 처리에 필요한 업무 로직을 수행한다. */
    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private record ServiceAccountPayload(
            String apiServerUrl,
            String caCertificate,
            String token
    ) {
    }

    private record ResourceMapping(String normalizedType, String kind, boolean namespaced, boolean secret) {

        /** ResourceMapping의 from 처리 데이터를 필요한 표현으로 변환한다. */
        private static ResourceMapping from(String resourceType) {
            String normalized = resourceType == null ? "" : resourceType.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "namespace", "namespaces", "ns" -> cluster("namespace", "Namespace");
                case "node", "nodes" -> cluster("node", "Node");
                case "persistentvolume", "persistentvolumes", "pv" -> cluster("persistentvolume", "PersistentVolume");
                case "pod", "pods" -> namespaced("pod", "Pod");
                case "service", "services", "svc" -> namespaced("service", "Service");
                case "endpoint", "endpoints", "ep" -> namespaced("endpoint", "Endpoints");
                case "configmap", "configmaps", "cm" -> namespaced("configmap", "ConfigMap");
                case "secret", "secrets" -> new ResourceMapping("secret", "Secret", true, true);
                case "serviceaccount", "serviceaccounts", "sa" -> namespaced("serviceaccount", "ServiceAccount");
                case "persistentvolumeclaim", "persistentvolumeclaims", "pvc" -> namespaced("persistentvolumeclaim", "PersistentVolumeClaim");
                case "deployment", "deployments", "deploy" -> namespaced("deployment", "Deployment");
                case "replicaset", "replicasets", "rs" -> namespaced("replicaset", "ReplicaSet");
                case "statefulset", "statefulsets", "sts" -> namespaced("statefulset", "StatefulSet");
                case "daemonset", "daemonsets", "ds" -> namespaced("daemonset", "DaemonSet");
                case "job", "jobs" -> namespaced("job", "Job");
                case "cronjob", "cronjobs", "cj" -> namespaced("cronjob", "CronJob");
                case "ingress", "ingresses", "ing" -> namespaced("ingress", "Ingress");
                case "networkpolicy", "networkpolicies", "netpol" -> namespaced("networkpolicy", "NetworkPolicy");
                default -> throw new IllegalArgumentException("Unsupported Kubernetes resource type: " + resourceType);
            };
        }

        /** ResourceMapping의 namespaced 처리에 필요한 업무 로직을 수행한다. */
        private static ResourceMapping namespaced(String normalizedType, String kind) {
            return new ResourceMapping(normalizedType, kind, true, false);
        }

        /** ResourceMapping의 cluster 처리에 필요한 업무 로직을 수행한다. */
        private static ResourceMapping cluster(String normalizedType, String kind) {
            return new ResourceMapping(normalizedType, kind, false, false);
        }
    }
}
