package io.strato.aiops.application.port.out;

import java.time.Instant;
import java.util.List;

public record KubernetesNamespaceDiagnostics(
        List<DiagnosticResource> resources,
        List<DiagnosticEvent> events,
        List<DiagnosticPodLog> podLogs,
        Instant collectedAt,
        List<CollectionStage> collectionStages
) {
    public KubernetesNamespaceDiagnostics(List<DiagnosticResource> resources, List<DiagnosticEvent> events,
                                          List<DiagnosticPodLog> podLogs, Instant collectedAt) {
        this(resources, events, podLogs, collectedAt, List.of());
    }

    public KubernetesNamespaceDiagnostics {
        resources = resources == null ? List.of() : List.copyOf(resources);
        events = events == null ? List.of() : List.copyOf(events);
        podLogs = podLogs == null ? List.of() : List.copyOf(podLogs);
        collectionStages = collectionStages == null ? List.of() : List.copyOf(collectionStages);
    }

    public boolean partial() {
        return collectionStages.stream().anyMatch(stage -> !"SUCCEEDED".equals(stage.status()));
    }

    public record CollectionStage(
            String source,
            String status,
            int itemCount,
            long latencyMs,
            String detail
    ) {
    }

    public record DiagnosticResource(
            String namespace,
            String resourceType,
            String resourceName,
            String status,
            String summaryJson
    ) {
    }

    public record DiagnosticEvent(
            String namespace,
            String involvedKind,
            String involvedName,
            String reason,
            String type,
            String message,
            Instant eventTime,
            Integer count
    ) {
    }

    public record DiagnosticPodLog(
            String namespace,
            String podName,
            String containerName,
            String log,
            boolean truncated
    ) {
    }
}
