package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesResourceLogPort;
import io.strato.aiops.application.port.out.KubernetesResourceLogSnapshot;
import io.strato.aiops.application.port.out.KubernetesResourceLogStreamResult;
import io.strato.aiops.application.port.out.KubernetesResourceLogTargets;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Component
public class Fabric8KubernetesResourceLogAdapter implements KubernetesResourceLogPort {

    private static final List<String> SUPPORTED_TYPES = List.of(
            "pod", "deployment", "statefulset", "daemonset", "replicaset", "job", "cronjob", "service");

    private final ObjectMapper objectMapper;
    private final TaskScheduler scheduler;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final long maxStreamDurationMs;
    private final int maxTargetPods;

    /** Fabric8KubernetesResourceLogAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public Fabric8KubernetesResourceLogAdapter(
            ObjectMapper objectMapper,
            @Qualifier("resourceLogScheduler") TaskScheduler scheduler,
            @Value("${aiops.kubernetes.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${aiops.kubernetes.request-timeout-ms:10000}") int requestTimeoutMs,
            @Value("${aiops.cluster-logs.max-stream-duration-ms:1800000}") long maxStreamDurationMs,
            @Value("${aiops.cluster-logs.max-target-pods:50}") int maxTargetPods
    ) {
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
        this.connectTimeoutMs = connectTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.maxStreamDurationMs = Math.max(1_000, maxStreamDurationMs);
        this.maxTargetPods = Math.max(1, maxTargetPods);
    }

    /** Fabric8KubernetesResourceLogAdapter의 findTargets 처리 결과를 조회해 반환한다. */
    @Override
    public KubernetesResourceLogTargets findTargets(KubernetesConnectionCredential credential, String namespace,
                                                     String resourceType, String resourceName) {
        String normalizedType = normalizeType(resourceType);
        if (!SUPPORTED_TYPES.contains(normalizedType)) {
            return new KubernetesResourceLogTargets(namespace, resourceType, resourceName, false,
                    "This Kubernetes resource type does not expose container logs.", List.of());
        }
        try (KubernetesClient client = createClient(credential, false)) {
            List<KubernetesResourceLogTargets.PodTarget> pods = podsForResource(
                    client, namespace, normalizedType, resourceName).stream()
                    .sorted(Comparator.comparing(this::podStartedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(maxTargetPods)
                    .map(this::toTarget)
                    .toList();
            String unavailableReason = pods.isEmpty()
                    ? "No related Pod was found. Check selectors, owner references, and workload rollout status."
                    : null;
            return new KubernetesResourceLogTargets(namespace, resourceType, resourceName, true,
                    unavailableReason, pods);
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Kubernetes log target lookup failed: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesResourceLogAdapter의 getRecentLogs 처리 결과를 조회해 반환한다. */
    @Override
    public KubernetesResourceLogSnapshot getRecentLogs(KubernetesConnectionCredential credential, String namespace,
                                                       String resourceType, String resourceName, String podName,
                                                       String containerName, int tailLines, boolean previous) {
        try (KubernetesClient client = createClient(credential, false)) {
            requireTarget(client, namespace, normalizeType(resourceType), resourceName, podName, containerName);
            String log = client.pods().inNamespace(namespace).withName(podName).inContainer(containerName)
                    .usingTimestamps().tailingLines(tailLines).getLog(previous);
            return new KubernetesResourceLogSnapshot(namespace, resourceType, resourceName, podName, containerName,
                    tailLines, previous, log == null ? "" : log, false, Instant.now());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (KubernetesClientException exception) {
            throw new KubernetesApiException("Kubernetes recent log request failed: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesResourceLogAdapter의 streamLogs 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public KubernetesResourceLogStreamResult streamLogs(KubernetesConnectionCredential credential, String namespace,
                                                        String resourceType, String resourceName, String podName,
                                                        String containerName, int tailLines, Consumer<String> onLine,
                                                        BooleanSupplier cancelled) {
        long startedAt = System.nanoTime();
        long lineCount = 0;
        AtomicBoolean timeLimitReached = new AtomicBoolean();
        AtomicBoolean clientDisconnected = new AtomicBoolean();
        try (KubernetesClient client = createClient(credential, true)) {
            requireTarget(client, namespace, normalizeType(resourceType), resourceName, podName, containerName);
            try (LogWatch watch = client.pods().inNamespace(namespace).withName(podName).inContainer(containerName)
                    .usingTimestamps().tailingLines(tailLines).watchLog()) {
                ScheduledFuture<?> timeout = scheduler.schedule(() -> {
                    timeLimitReached.set(true);
                    watch.close();
                }, Instant.now().plusMillis(maxStreamDurationMs));
                ScheduledFuture<?> cancellationMonitor = scheduler.scheduleAtFixedRate(() -> {
                    if (cancelled.getAsBoolean()) {
                        clientDisconnected.set(true);
                        watch.close();
                    }
                }, Duration.ofSeconds(1));
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        watch.getOutput(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        onLine.accept(line);
                        lineCount++;
                    }
                } finally {
                    if (timeout != null) {
                        timeout.cancel(false);
                    }
                    if (cancellationMonitor != null) {
                        cancellationMonitor.cancel(false);
                    }
                }
            }
            return new KubernetesResourceLogStreamResult(
                    clientDisconnected.get() ? "CLIENT_DISCONNECTED"
                            : timeLimitReached.get() ? "TIME_LIMIT" : "COMPLETED",
                    lineCount, elapsedMs(startedAt));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new KubernetesApiException("Kubernetes log stream read failed: " + value(exception.getMessage()), exception);
        } catch (RuntimeException exception) {
            throw new KubernetesApiException("Kubernetes log stream failed: " + failureDetail(exception), exception);
        }
    }

    /** Fabric8KubernetesResourceLogAdapter의 requireTarget 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireTarget(KubernetesClient client, String namespace, String resourceType, String resourceName,
                               String podName, String containerName) {
        if (!SUPPORTED_TYPES.contains(resourceType)) {
            throw new IllegalArgumentException("Resource type does not expose container logs: " + resourceType);
        }
        Pod pod = podsForResource(client, namespace, resourceType, resourceName).stream()
                .filter(candidate -> candidate.getMetadata() != null
                        && podName.equals(candidate.getMetadata().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Selected Pod does not belong to " + resourceType + "/" + resourceName));
        boolean containerExists = allContainers(pod).stream().anyMatch(container -> containerName.equals(container.getName()));
        if (!containerExists) {
            throw new IllegalArgumentException("Container does not belong to Pod/" + podName + ": " + containerName);
        }
    }

    /** Fabric8KubernetesResourceLogAdapter의 toTarget 처리 데이터를 필요한 표현으로 변환한다. */
    private KubernetesResourceLogTargets.PodTarget toTarget(Pod pod) {
        String podName = pod.getMetadata() == null ? "" : value(pod.getMetadata().getName());
        String phase = pod.getStatus() == null ? "Unknown" : value(pod.getStatus().getPhase());
        List<KubernetesResourceLogTargets.ContainerTarget> containers = new ArrayList<>();
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
            pod.getSpec().getContainers().forEach(container -> containers.add(
                    toContainerTarget(container, statusFor(pod.getStatus() == null ? null
                            : pod.getStatus().getContainerStatuses(), container.getName()), false)));
        }
        if (pod.getSpec() != null && pod.getSpec().getInitContainers() != null) {
            pod.getSpec().getInitContainers().forEach(container -> containers.add(
                    toContainerTarget(container, statusFor(pod.getStatus() == null ? null
                            : pod.getStatus().getInitContainerStatuses(), container.getName()), true)));
        }
        return new KubernetesResourceLogTargets.PodTarget(podName, phase, podStartedAt(pod), containers);
    }

    /** Fabric8KubernetesResourceLogAdapter의 toContainerTarget 처리 데이터를 필요한 표현으로 변환한다. */
    private KubernetesResourceLogTargets.ContainerTarget toContainerTarget(Container container,
                                                                            ContainerStatus status,
                                                                            boolean initContainer) {
        return new KubernetesResourceLogTargets.ContainerTarget(container.getName(),
                status != null && Boolean.TRUE.equals(status.getReady()),
                status == null || status.getRestartCount() == null ? 0 : status.getRestartCount(),
                containerState(status), initContainer);
    }

    /** Fabric8KubernetesResourceLogAdapter의 allContainers 처리에 필요한 업무 로직을 수행한다. */
    private List<Container> allContainers(Pod pod) {
        if (pod.getSpec() == null) {
            return List.of();
        }
        List<Container> containers = new ArrayList<>();
        if (pod.getSpec().getContainers() != null) {
            containers.addAll(pod.getSpec().getContainers());
        }
        if (pod.getSpec().getInitContainers() != null) {
            containers.addAll(pod.getSpec().getInitContainers());
        }
        return containers;
    }

    /** Fabric8KubernetesResourceLogAdapter의 statusFor 처리에 필요한 업무 로직을 수행한다. */
    private ContainerStatus statusFor(List<ContainerStatus> statuses, String name) {
        if (statuses == null) {
            return null;
        }
        return statuses.stream().filter(status -> name.equals(status.getName())).findFirst().orElse(null);
    }

    /** Fabric8KubernetesResourceLogAdapter의 containerState 처리에 필요한 업무 로직을 수행한다. */
    private String containerState(ContainerStatus status) {
        if (status == null || status.getState() == null) {
            return "UNKNOWN";
        }
        if (status.getState().getRunning() != null) {
            return "RUNNING";
        }
        if (status.getState().getWaiting() != null) {
            return "WAITING:" + value(status.getState().getWaiting().getReason());
        }
        if (status.getState().getTerminated() != null) {
            return "TERMINATED:" + value(status.getState().getTerminated().getReason());
        }
        return "UNKNOWN";
    }

    /** Fabric8KubernetesResourceLogAdapter의 podsForResource 처리에 필요한 업무 로직을 수행한다. */
    private List<Pod> podsForResource(KubernetesClient client, String namespace, String resourceType,
                                      String resourceName) {
        return switch (resourceType) {
            case "pod" -> podByName(client, namespace, resourceName);
            case "deployment" -> {
                var resource = client.apps().deployments().inNamespace(namespace).withName(resourceName).get();
                yield resource == null || resource.getSpec() == null
                        ? List.of() : podsBySelector(client, namespace, resource.getSpec().getSelector());
            }
            case "statefulset" -> {
                var resource = client.apps().statefulSets().inNamespace(namespace).withName(resourceName).get();
                yield resource == null || resource.getSpec() == null
                        ? List.of() : podsBySelector(client, namespace, resource.getSpec().getSelector());
            }
            case "daemonset" -> {
                var resource = client.apps().daemonSets().inNamespace(namespace).withName(resourceName).get();
                yield resource == null || resource.getSpec() == null
                        ? List.of() : podsBySelector(client, namespace, resource.getSpec().getSelector());
            }
            case "replicaset" -> {
                var resource = client.apps().replicaSets().inNamespace(namespace).withName(resourceName).get();
                yield resource == null || resource.getSpec() == null
                        ? List.of() : podsBySelector(client, namespace, resource.getSpec().getSelector());
            }
            case "job" -> {
                var resource = client.batch().v1().jobs().inNamespace(namespace).withName(resourceName).get();
                yield resource == null || resource.getSpec() == null
                        ? List.of() : podsBySelector(client, namespace, resource.getSpec().getSelector());
            }
            case "cronjob" -> podsForCronJob(client, namespace, resourceName);
            case "service" -> {
                var resource = client.services().inNamespace(namespace).withName(resourceName).get();
                yield resource == null || resource.getSpec() == null
                        ? List.of() : podsByLabels(client, namespace, resource.getSpec().getSelector());
            }
            default -> List.of();
        };
    }

    /** Fabric8KubernetesResourceLogAdapter의 podByName 처리에 필요한 업무 로직을 수행한다. */
    private List<Pod> podByName(KubernetesClient client, String namespace, String name) {
        Pod pod = client.pods().inNamespace(namespace).withName(name).get();
        return pod == null ? List.of() : List.of(pod);
    }

    /** Fabric8KubernetesResourceLogAdapter의 podsForCronJob 처리에 필요한 업무 로직을 수행한다. */
    private List<Pod> podsForCronJob(KubernetesClient client, String namespace, String cronJobName) {
        return client.batch().v1().jobs().inNamespace(namespace).list().getItems().stream()
                .filter(job -> ownedBy(job.getMetadata() == null ? null : job.getMetadata().getOwnerReferences(),
                        "CronJob", cronJobName))
                .sorted(Comparator.comparing(job -> job.getMetadata() == null ? null
                        : parseInstant(job.getMetadata().getCreationTimestamp()),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .flatMap(job -> podsBySelector(client, namespace,
                        job.getSpec() == null ? null : job.getSpec().getSelector()).stream())
                .toList();
    }

    /** Fabric8KubernetesResourceLogAdapter의 ownedBy 처리에 필요한 업무 로직을 수행한다. */
    private boolean ownedBy(List<OwnerReference> owners, String kind, String name) {
        return owners != null && owners.stream().anyMatch(owner -> kind.equalsIgnoreCase(value(owner.getKind()))
                && name.equals(owner.getName()));
    }

    /** Fabric8KubernetesResourceLogAdapter의 podsBySelector 처리에 필요한 업무 로직을 수행한다. */
    private List<Pod> podsBySelector(KubernetesClient client, String namespace, LabelSelector selector) {
        if (selector == null) {
            return List.of();
        }
        return client.pods().inNamespace(namespace).withLabelSelector(selector).list().getItems();
    }

    /** Fabric8KubernetesResourceLogAdapter의 podsByLabels 처리에 필요한 업무 로직을 수행한다. */
    private List<Pod> podsByLabels(KubernetesClient client, String namespace, Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        return client.pods().inNamespace(namespace).withLabels(labels).list().getItems();
    }

    /** Fabric8KubernetesResourceLogAdapter의 podStartedAt 처리에 필요한 업무 로직을 수행한다. */
    private Instant podStartedAt(Pod pod) {
        return pod.getStatus() == null ? null : parseInstant(pod.getStatus().getStartTime());
    }

    /** Fabric8KubernetesResourceLogAdapter의 parseInstant 처리 데이터를 필요한 표현으로 변환한다. */
    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Fabric8KubernetesResourceLogAdapter의 createClient 처리에 필요한 데이터를 생성하거나 저장한다. */
    private KubernetesClient createClient(KubernetesConnectionCredential credential, boolean streaming) {
        if (credential.credentialType() == ClusterCredentialType.KUBECONFIG) {
            Config config = Config.fromKubeconfig(credential.payload());
            config.setConnectionTimeout(connectTimeoutMs);
            config.setRequestTimeout(streaming ? 0 : requestTimeoutMs);
            return new KubernetesClientBuilder().withConfig(config).build();
        }
        ServiceAccountPayload payload = parseServiceAccountPayload(credential.payload());
        Config config = new ConfigBuilder()
                .withMasterUrl(payload.apiServerUrl())
                .withOauthToken(payload.token())
                .withCaCertData(normalizeCertificateAuthority(payload.caCertificate()))
                .withConnectionTimeout(connectTimeoutMs)
                .withRequestTimeout(streaming ? 0 : requestTimeoutMs)
                .build();
        return new KubernetesClientBuilder().withConfig(config).build();
    }

    /** Fabric8KubernetesResourceLogAdapter의 parseServiceAccountPayload 처리 데이터를 필요한 표현으로 변환한다. */
    private ServiceAccountPayload parseServiceAccountPayload(String payload) {
        try {
            return objectMapper.readValue(payload, ServiceAccountPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid ServiceAccount credential payload", exception);
        }
    }

    /** Fabric8KubernetesResourceLogAdapter의 normalizeCertificateAuthority 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizeCertificateAuthority(String caCertificate) {
        if (caCertificate == null || caCertificate.isBlank()) {
            return null;
        }
        return caCertificate.contains("BEGIN CERTIFICATE")
                ? Base64.getEncoder().encodeToString(caCertificate.getBytes(StandardCharsets.UTF_8))
                : caCertificate;
    }

    /** Fabric8KubernetesResourceLogAdapter의 normalizeType 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizeType(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    /** Fabric8KubernetesResourceLogAdapter의 value 처리에 필요한 업무 로직을 수행한다. */
    private String value(String value) {
        return value == null ? "" : value;
    }

    /** Fabric8KubernetesResourceLogAdapter의 elapsedMs 처리에 필요한 업무 로직을 수행한다. */
    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    /** Fabric8KubernetesResourceLogAdapter의 failureDetail 처리에 필요한 업무 로직을 수행한다. */
    private String failureDetail(RuntimeException exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String detail = current.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = current.getClass().getSimpleName();
        }
        detail = detail.replaceAll("\\s+", " ");
        return detail.length() > 300 ? detail.substring(0, 300) + "..." : detail;
    }

    private record ServiceAccountPayload(String apiServerUrl, String caCertificate, String token) {
    }
}
