package io.strato.aiops.adapter.out.kubernetes;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics.DiagnosticPodLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
final class Fabric8PodLogCollector {
    private final int maxLogChars;

    Fabric8PodLogCollector(@Value("${aiops.analysis.max-log-chars:4000}") int maxLogChars) {
        this.maxLogChars = Math.max(500, maxLogChars);
    }

    void collect(KubernetesClient client, String namespace, Pod pod, String selectedContainerName,
                 int tailLines, boolean previous, List<DiagnosticPodLog> target) {
        if (pod.getSpec() == null || pod.getSpec().getContainers() == null || pod.getMetadata() == null) return;
        String podName = text(pod.getMetadata().getName());
        if (podName.isBlank()) return;
        pod.getSpec().getContainers().stream()
                .filter(container -> selectedContainerName == null || selectedContainerName.isBlank()
                        || selectedContainerName.equals(container.getName()))
                .forEach(container -> collectContainer(client, namespace, pod, podName, container.getName(), tailLines, previous, target));
    }

    private void collectContainer(KubernetesClient client, String namespace, Pod pod, String podName,
                                  String containerName, int tailLines, boolean previous, List<DiagnosticPodLog> target) {
        ContainerStatus status = containerStatus(pod, containerName);
        String unavailable = unavailableReason(status);
        boolean readPrevious = previous || shouldTryPrevious(status);
        if (unavailable != null && !readPrevious) {
            target.add(new DiagnosticPodLog(namespace, podName, containerName, unavailable, false));
            return;
        }
        try {
            String log = client.pods().inNamespace(namespace).withName(podName).inContainer(containerName)
                    .tailingLines(Math.max(1, Math.min(5000, tailLines))).getLog(readPrevious);
            String sanitized = sanitize(log, maxLogChars);
            if (!sanitized.isBlank()) {
                String prefix = readPrevious ? "Previous terminated container log. Use this for CrashLoopBackOff startup/root-cause analysis.\n" : "";
                target.add(new DiagnosticPodLog(namespace, podName, containerName, prefix + sanitized,
                        log != null && log.length() > maxLogChars));
            } else if (unavailable != null) {
                target.add(new DiagnosticPodLog(namespace, podName, containerName,
                        unavailable + " Previous terminated container log was requested but returned no content.", false));
            }
        } catch (KubernetesClientException exception) {
            target.add(new DiagnosticPodLog(namespace, podName, containerName,
                    (unavailable == null ? "" : unavailable + " Previous log attempt: ") + friendlyFailure(exception), true));
        }
    }

    private ContainerStatus containerStatus(Pod pod, String containerName) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) return null;
        return pod.getStatus().getContainerStatuses().stream()
                .filter(status -> Objects.equals(status.getName(), containerName)).findFirst().orElse(null);
    }

    private boolean shouldTryPrevious(ContainerStatus status) {
        if (status == null) return false;
        if (status.getRestartCount() != null && status.getRestartCount() > 0) return true;
        if (status.getState() == null || status.getState().getWaiting() == null) return false;
        String reason = text(status.getState().getWaiting().getReason()).toLowerCase(Locale.ROOT);
        return reason.contains("crashloopbackoff") || reason.contains("error");
    }

    private String unavailableReason(ContainerStatus status) {
        if (status == null || status.getState() == null) return null;
        if (status.getState().getWaiting() != null) {
            String reason = text(status.getState().getWaiting().getReason());
            String message = sanitize(status.getState().getWaiting().getMessage(), 300);
            return "Logs are not available yet because the container is waiting" + (reason.isBlank() ? "" : " (" + reason + ")")
                    + ". Check the pod events and image/volume/config status." + (message.isBlank() ? "" : " Detail: " + message);
        }
        if (status.getState().getTerminated() != null && status.getState().getTerminated().getExitCode() == 0) {
            return "Container already terminated successfully. Current logs may be empty.";
        }
        return null;
    }

    private String friendlyFailure(KubernetesClientException exception) {
        String message = text(exception.getMessage());
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("waiting to start")) return "Logs are not available yet because the container is waiting. Check pod events, image pull, volume mount, config, and scheduling status.";
        if (lower.contains("previous terminated container")) return "Previous container logs are not available for this container.";
        if (lower.contains("not found")) return "Logs are not available because the pod or container was not found.";
        return "Logs are not available: " + sanitize(message.replaceAll("https?://[^\\s]+", "<kubernetes-api>"), 300);
    }

    private String sanitize(String value, int limit) {
        if (value == null) return "";
        String sanitized = value.replaceAll("(?i)(token|password|secret|authorization)(\\s*[:=]\\s*)[^\\s,;]+", "$1$2***");
        return sanitized.length() <= limit ? sanitized : sanitized.substring(0, limit);
    }

    private String text(String value) { return value == null ? "" : value.trim(); }
}
