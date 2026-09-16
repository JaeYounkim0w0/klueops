package io.strato.aiops.application.service;

import io.strato.aiops.application.port.in.ClusterResourceLogLine;
import io.strato.aiops.application.port.in.ClusterResourceLogResult;
import io.strato.aiops.application.port.in.ClusterResourceLogStreamResult;
import io.strato.aiops.application.port.in.ClusterResourceLogTargetsResult;
import io.strato.aiops.application.port.in.GetClusterResourceLogsUseCase;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesResourceLogPort;
import io.strato.aiops.application.port.out.KubernetesResourceLogSnapshot;
import io.strato.aiops.application.port.out.KubernetesResourceLogTargets;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Service
public class ClusterResourceLogService implements GetClusterResourceLogsUseCase {

    private final ClusterRepositoryPort clusterRepositoryPort;
    private final ClusterCredentialRepositoryPort credentialRepositoryPort;
    private final SecretCryptoPort secretCryptoPort;
    private final KubernetesResourceLogPort kubernetesResourceLogPort;

    /** ClusterResourceLogService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ClusterResourceLogService(ClusterRepositoryPort clusterRepositoryPort,
                                     ClusterCredentialRepositoryPort credentialRepositoryPort,
                                     SecretCryptoPort secretCryptoPort,
                                     KubernetesResourceLogPort kubernetesResourceLogPort) {
        this.clusterRepositoryPort = clusterRepositoryPort;
        this.credentialRepositoryPort = credentialRepositoryPort;
        this.secretCryptoPort = secretCryptoPort;
        this.kubernetesResourceLogPort = kubernetesResourceLogPort;
    }

    /** ClusterResourceLogService의 getTargets 처리 결과를 조회해 반환한다. */
    @Override
    public ClusterResourceLogTargetsResult getTargets(UUID clusterId, String namespace, String resourceType,
                                                      String resourceName) {
        KubernetesResourceLogTargets targets = kubernetesResourceLogPort.findTargets(
                connectionCredential(clusterId), required(namespace, "namespace"), required(resourceType, "resourceType"),
                required(resourceName, "resourceName"));
        return new ClusterResourceLogTargetsResult(clusterId, targets.namespace(), targets.resourceType(),
                targets.resourceName(), targets.supported(), targets.unavailableReason(), targets.pods().stream()
                .map(pod -> new ClusterResourceLogTargetsResult.PodTarget(pod.podName(), pod.phase(), pod.startedAt(),
                        pod.containers().stream().map(container -> new ClusterResourceLogTargetsResult.ContainerTarget(
                                container.containerName(), container.ready(), container.restartCount(), container.state(),
                                container.initContainer())).toList()))
                .toList());
    }

    /** ClusterResourceLogService의 getRecentLogs 처리 결과를 조회해 반환한다. */
    @Override
    public ClusterResourceLogResult getRecentLogs(UUID clusterId, String namespace, String resourceType,
                                                  String resourceName, String podName, String containerName,
                                                  int tailLines, boolean previous) {
        KubernetesResourceLogSnapshot result = kubernetesResourceLogPort.getRecentLogs(connectionCredential(clusterId),
                required(namespace, "namespace"), required(resourceType, "resourceType"),
                required(resourceName, "resourceName"), required(podName, "podName"),
                required(containerName, "containerName"), clampTailLines(tailLines), previous);
        return new ClusterResourceLogResult(clusterId, result.namespace(), result.resourceType(), result.resourceName(),
                result.podName(), result.containerName(), result.tailLines(), result.previous(), result.log(),
                result.truncated(), result.collectedAt());
    }

    /** ClusterResourceLogService의 streamLogs 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public ClusterResourceLogStreamResult streamLogs(UUID clusterId, String namespace, String resourceType,
                                                     String resourceName, String podName, String containerName,
                                                     int tailLines, Consumer<ClusterResourceLogLine> onLine,
                                                     BooleanSupplier cancelled) {
        String selectedPod = required(podName, "podName");
        String selectedContainer = required(containerName, "containerName");
        var result = kubernetesResourceLogPort.streamLogs(connectionCredential(clusterId),
                required(namespace, "namespace"), required(resourceType, "resourceType"),
                required(resourceName, "resourceName"), selectedPod, selectedContainer, clampTailLines(tailLines),
                line -> onLine.accept(new ClusterResourceLogLine(selectedPod, selectedContainer, line, Instant.now())),
                cancelled);
        return new ClusterResourceLogStreamResult(result.reason(), result.lineCount(), result.durationMs());
    }

    /** ClusterResourceLogService의 connectionCredential 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesConnectionCredential connectionCredential(UUID clusterId) {
        clusterRepositoryPort.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
        EncryptedClusterCredential credential = credentialRepositoryPort.findByClusterId(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found: " + clusterId));
        String plaintext = secretCryptoPort.decrypt(new EncryptedSecret(credential.encryptedPayload(), credential.keyId(),
                credential.algorithm(), credential.nonce()));
        return new KubernetesConnectionCredential(credential.credentialType(), plaintext);
    }

    /** ClusterResourceLogService의 clampTailLines 처리에 필요한 업무 로직을 수행한다. */
    private int clampTailLines(int tailLines) {
        return Math.max(10, Math.min(tailLines, 1000));
    }

    /** ClusterResourceLogService의 required 처리 입력과 현재 상태의 유효성을 검증한다. */
    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
