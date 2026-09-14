package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesValidationLabPort;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Component
public class Fabric8KubernetesValidationLabAdapter implements KubernetesValidationLabPort {

    private static final String MANAGED_LABEL = "aiops.platform/managed";
    private static final String RUN_LABEL = "aiops.platform/validation-run";

    private final ObjectMapper objectMapper;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final int observationTimeoutMs;

    public Fabric8KubernetesValidationLabAdapter(
            ObjectMapper objectMapper,
            @Value("${aiops.kubernetes.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${aiops.kubernetes.request-timeout-ms:10000}") int requestTimeoutMs,
            @Value("${aiops.validation-lab.observation-timeout-ms:15000}") int observationTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.observationTimeoutMs = Math.max(1_000, Math.min(observationTimeoutMs, 30_000));
    }

    @Override
    public Preflight preflight(KubernetesConnectionCredential credential, String namespace, String scenarioId) {
        List<String> passed = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        try (KubernetesClient client = createClient(credential)) {
            if (allowed(client, null, "create", "", "namespaces")) {
                passed.add("Kubernetes RBAC allows creation of the isolated validation namespace.");
            } else {
                blocked.add("Kubernetes RBAC denies namespace creation.");
            }
            if (allowed(client, null, "delete", "", "namespaces")) {
                passed.add("Kubernetes RBAC allows cleanup of the isolated validation namespace.");
            } else {
                blocked.add("Kubernetes RBAC denies namespace cleanup.");
            }
            for (ResourceAccess access : requiredResourceAccess(scenarioId)) {
                if (allowed(client, namespace, "create", access.apiGroup(), access.resource())) {
                    passed.add("Kubernetes RBAC allows create on " + access.resource()
                            + " in the validation namespace.");
                } else {
                    blocked.add("Kubernetes RBAC denies create on " + access.resource()
                            + " in the validation namespace.");
                }
            }
        } catch (RuntimeException exception) {
            blocked.add("Kubernetes preflight failed: " + failureDetail(exception));
        }
        return new Preflight(blocked.isEmpty(), List.copyOf(passed), List.copyOf(blocked));
    }

    private List<ResourceAccess> requiredResourceAccess(String scenarioId) {
        return switch (scenarioId) {
            case "port-mismatch" -> List.of(
                    new ResourceAccess("apps", "deployments"),
                    new ResourceAccess("", "services"));
            case "pvc-pending" -> List.of(new ResourceAccess("", "persistentvolumeclaims"));
            case "rollback-guard" -> List.of(new ResourceAccess("apps", "deployments"));
            case "failed-mount", "crash-loop", "image-pull", "probe-failure", "oom-risk" ->
                    List.of(new ResourceAccess("", "pods"));
            default -> throw new IllegalArgumentException("Unsupported live validation scenario: " + scenarioId);
        };
    }

    @Override
    public Execution apply(KubernetesConnectionCredential credential, UUID runId, String namespace, String scenarioId) {
        try (KubernetesClient client = createClient(credential)) {
            Namespace validationNamespace = new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(namespace)
                    .addToLabels(MANAGED_LABEL, "true")
                    .addToLabels(RUN_LABEL, runId.toString())
                    .addToAnnotations("aiops.platform/created-at", Instant.now().toString())
                    .endMetadata()
                    .build();
            client.namespaces().resource(validationNamespace).create();
            String yaml = manifest(scenarioId, runId);
            var resources = client.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)))
                    .inNamespace(namespace).create();
            List<String> resourceNames = resources.stream()
                    .map(item -> item.getKind() + "/" + item.getMetadata().getName())
                    .toList();
            Observation observation = observe(client, namespace, scenarioId, runId);
            return new Execution(resourceNames, observation.detected(), observation.signal(), observation.detail());
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Live validation apply failed: " + failureDetail(exception), exception);
        }
    }

    @Override
    public Cleanup cleanup(KubernetesConnectionCredential credential, UUID runId, String namespace) {
        try (KubernetesClient client = createClient(credential)) {
            Namespace current = client.namespaces().withName(namespace).get();
            if (current == null) {
                return new Cleanup(true, "Validation namespace was already absent.");
            }
            String managed = current.getMetadata().getLabels().get(MANAGED_LABEL);
            String owner = current.getMetadata().getLabels().get(RUN_LABEL);
            if (!"true".equals(managed) || !runId.toString().equals(owner)) {
                return new Cleanup(false, "Cleanup blocked because namespace ownership labels do not match this run.");
            }
            List<?> deleted = client.namespaces().withName(namespace).delete();
            return new Cleanup(!deleted.isEmpty(), deleted.isEmpty()
                    ? "Kubernetes did not confirm namespace deletion."
                    : "Managed validation namespace deletion was requested.");
        } catch (RuntimeException exception) {
            return new Cleanup(false, "Validation cleanup failed: " + failureDetail(exception));
        }
    }

    private Observation observe(KubernetesClient client, String namespace, String scenarioId, UUID runId) {
        long deadline = System.currentTimeMillis() + observationTimeoutMs;
        Observation latest = new Observation(false, null, "Kubernetes has not exposed the expected signal yet.");
        do {
            latest = inspect(client, namespace, scenarioId, runId);
            if (latest.detected()) return latest;
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new Observation(false, null, "Validation observation was interrupted.");
            }
        } while (System.currentTimeMillis() < deadline);
        return latest;
    }

    private Observation inspect(KubernetesClient client, String namespace, String scenarioId, UUID runId) {
        String label = runId.toString();
        if ("port-mismatch".equals(scenarioId)) {
            var service = client.services().inNamespace(namespace).withName("aiops-port-mismatch").get();
            var deployment = client.apps().deployments().inNamespace(namespace).withName("aiops-port-mismatch").get();
            if (service != null && deployment != null
                    && !service.getSpec().getPorts().isEmpty()
                    && !deployment.getSpec().getTemplate().getSpec().getContainers().isEmpty()) {
                String target = String.valueOf(service.getSpec().getPorts().get(0).getTargetPort());
                Integer containerPort = deployment.getSpec().getTemplate().getSpec().getContainers().get(0)
                        .getPorts().get(0).getContainerPort();
                return new Observation(target.contains("8080") && Integer.valueOf(80).equals(containerPort),
                        "targetPort=" + target + ", containerPort=" + containerPort,
                        "Service and Deployment were read back from the Kubernetes API.");
            }
        }
        if ("pvc-pending".equals(scenarioId)) {
            var pvc = client.persistentVolumeClaims().inNamespace(namespace).withName("aiops-pvc-pending").get();
            String phase = pvc == null || pvc.getStatus() == null ? null : pvc.getStatus().getPhase();
            return new Observation("Pending".equalsIgnoreCase(phase), "PVC phase=" + text(phase),
                    "PVC phase was read from the Kubernetes API.");
        }
        List<Pod> pods = client.pods().inNamespace(namespace).withLabel(RUN_LABEL, label).list().getItems();
        List<Event> events = client.v1().events().inNamespace(namespace).list().getItems();
        for (Pod pod : pods) {
            String reason = podReason(pod);
            String phase = pod.getStatus() == null ? null : pod.getStatus().getPhase();
            if (expected(scenarioId, reason, phase)) {
                return new Observation(true, "Pod/" + pod.getMetadata().getName() + " · " + text(reason, phase),
                        "Container status was read from the Kubernetes API.");
            }
            Event warning = events.stream()
                    .filter(event -> "Warning".equalsIgnoreCase(event.getType()))
                    .filter(event -> event.getInvolvedObject() != null
                            && pod.getMetadata().getName().equals(event.getInvolvedObject().getName()))
                    .findFirst().orElse(null);
            if (warning != null && expected(scenarioId, warning.getReason(), phase)) {
                return new Observation(true, "Event/" + warning.getReason(), sanitize(warning.getMessage()));
            }
        }
        return new Observation(false, pods.isEmpty() ? "Pod not scheduled yet" : "Expected failure state not observed yet",
                "The fixture exists, but the bounded observation window ended before the expected signal appeared.");
    }

    private boolean expected(String scenarioId, String reason, String phase) {
        String value = (text(reason) + " " + text(phase)).toUpperCase(Locale.ROOT);
        return switch (scenarioId) {
            case "failed-mount" -> value.contains("FAILEDMOUNT");
            case "crash-loop" -> value.contains("CRASHLOOPBACKOFF") || value.contains("ERROR");
            case "image-pull" -> value.contains("IMAGEPULLBACKOFF") || value.contains("ERRIMAGEPULL");
            case "probe-failure" -> value.contains("UNHEALTHY") || value.contains("READINESS");
            case "oom-risk" -> value.contains("OOMKILLED") || value.contains("ERROR");
            case "rollback-guard" -> value.contains("IMAGEPULLBACKOFF") || value.contains("ERRIMAGEPULL");
            default -> false;
        };
    }

    private boolean allowed(KubernetesClient client, String namespace, String verb, String group, String resource) {
        SelfSubjectAccessReview review = new SelfSubjectAccessReviewBuilder()
                .withNewSpec().withNewResourceAttributes()
                .withNamespace(namespace).withVerb(verb).withGroup(group).withResource(resource)
                .endResourceAttributes().endSpec().build();
        SelfSubjectAccessReview result = client.authorization().v1().selfSubjectAccessReview().create(review);
        return result != null && result.getStatus() != null && Boolean.TRUE.equals(result.getStatus().getAllowed());
    }

    private String podReason(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) return null;
        return pod.getStatus().getContainerStatuses().stream()
                .map(status -> {
                    if (status.getState() != null && status.getState().getWaiting() != null) {
                        return status.getState().getWaiting().getReason();
                    }
                    if (status.getState() != null && status.getState().getTerminated() != null) {
                        return status.getState().getTerminated().getReason();
                    }
                    if (status.getLastState() != null && status.getLastState().getTerminated() != null) {
                        return status.getLastState().getTerminated().getReason();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    String manifest(String scenarioId, UUID runId) {
        String labels = """
                    aiops.platform/managed: "true"
                    aiops.platform/validation-run: "%s"
                """.formatted(runId);
        return switch (scenarioId) {
            case "failed-mount" -> pod("aiops-failed-mount", labels, """
                    image: busybox:1.36
                    command: ["sh", "-c", "sleep 600"]
                    volumeMounts:
                      - name: missing
                        mountPath: /missing
                    """, """
                    volumes:
                      - name: missing
                        configMap:
                          name: aiops-intentionally-missing
                    """);
            case "crash-loop" -> pod("aiops-crash-loop", labels, """
                    image: busybox:1.36
                    command: ["sh", "-c", "echo validation-crash; exit 1"]
                    """, "");
            case "image-pull" -> pod("aiops-image-pull", labels, """
                    image: invalid.registry.local/aiops/does-not-exist:validation
                    imagePullPolicy: Always
                    """, "");
            case "probe-failure" -> pod("aiops-probe-failure", labels, """
                    image: nginx:1.27
                    ports:
                      - containerPort: 80
                    readinessProbe:
                      httpGet:
                        path: /intentionally-missing
                        port: 80
                      initialDelaySeconds: 1
                      periodSeconds: 2
                      failureThreshold: 1
                    """, "");
            case "oom-risk" -> pod("aiops-oom-risk", labels, """
                    image: busybox:1.36
                    command: ["sh", "-c", "x=$(head -c 67108864 /dev/zero | tr '\\\\0' x); sleep 300"]
                    resources:
                      requests:
                        memory: 8Mi
                      limits:
                        memory: 24Mi
                    """, "");
            case "pvc-pending" -> """
                    apiVersion: v1
                    kind: PersistentVolumeClaim
                    metadata:
                      name: aiops-pvc-pending
                      labels:
                    %s
                    spec:
                      accessModes: ["ReadWriteOnce"]
                      storageClassName: aiops-intentionally-missing
                      resources:
                        requests:
                          storage: 1Mi
                    """.formatted(indent(labels, 4));
            case "port-mismatch" -> """
                    apiVersion: apps/v1
                    kind: Deployment
                    metadata:
                      name: aiops-port-mismatch
                      labels:
                    %s
                    spec:
                      replicas: 1
                      selector:
                        matchLabels:
                          app: aiops-port-mismatch
                      template:
                        metadata:
                          labels:
                            app: aiops-port-mismatch
                    %s
                        spec:
                          containers:
                            - name: nginx
                              image: nginx:1.27
                              ports:
                                - containerPort: 80
                    ---
                    apiVersion: v1
                    kind: Service
                    metadata:
                      name: aiops-port-mismatch
                      labels:
                    %s
                    spec:
                      selector:
                        app: aiops-port-mismatch
                      ports:
                        - port: 80
                          targetPort: 8080
                    """.formatted(indent(labels, 4), indent(labels, 8), indent(labels, 4));
            case "rollback-guard" -> """
                    apiVersion: apps/v1
                    kind: Deployment
                    metadata:
                      name: aiops-rollback-guard
                      labels:
                    %s
                    spec:
                      replicas: 1
                      progressDeadlineSeconds: 10
                      selector:
                        matchLabels:
                          app: aiops-rollback-guard
                      template:
                        metadata:
                          labels:
                            app: aiops-rollback-guard
                    %s
                        spec:
                          containers:
                            - name: app
                              image: invalid.registry.local/aiops/rollback-candidate:validation
                    """.formatted(indent(labels, 4), indent(labels, 8));
            default -> throw new IllegalArgumentException("Unsupported live validation scenario: " + scenarioId);
        };
    }

    private String pod(String name, String labels, String containerSpec, String podSpecExtra) {
        return """
                apiVersion: v1
                kind: Pod
                metadata:
                  name: %s
                  labels:
                %s
                spec:
                  restartPolicy: Always
                  containers:
                    - name: fixture
                %s
                %s
                """.formatted(name, indent(labels, 4), indent(containerSpec, 6), indent(podSpecExtra, 2));
    }

    private String indent(String value, int spaces) {
        if (value == null || value.isBlank()) return "";
        List<String> lines = value.lines().toList();
        int minimum = lines.stream().filter(line -> !line.isBlank())
                .mapToInt(this::leadingSpaces).min().orElse(0);
        return lines.stream().filter(line -> !line.isBlank())
                .map(line -> " ".repeat(spaces) + line.substring(Math.min(minimum, line.length())))
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') count++;
        return count;
    }

    private KubernetesClient createClient(KubernetesConnectionCredential credential) {
        if (credential.credentialType() == ClusterCredentialType.KUBECONFIG) {
            Config config = Config.fromKubeconfig(credential.payload());
            config.setConnectionTimeout(connectTimeoutMs);
            config.setRequestTimeout(requestTimeoutMs);
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

    private ServiceAccountPayload parseServiceAccountPayload(String payload) {
        try {
            return objectMapper.readValue(payload, ServiceAccountPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid ServiceAccount credential payload", exception);
        }
    }

    private String normalizeCertificateAuthority(String value) {
        if (value == null || value.isBlank()) return null;
        return value.contains("BEGIN CERTIFICATE")
                ? Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
                : value;
    }

    private String failureDetail(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return sanitize(current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage());
    }

    private String sanitize(String value) {
        if (value == null) return "";
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500) + "...";
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String text(String primary, String fallback) {
        return primary == null || primary.isBlank() ? text(fallback) : primary;
    }

    private record Observation(boolean detected, String signal, String detail) {
    }

    private record ResourceAccess(String apiGroup, String resource) {
    }

    private record ServiceAccountPayload(String apiServerUrl, String caCertificate, String token) {
    }
}
