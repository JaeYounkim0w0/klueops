package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.ClusterResourceLogResult;

import java.time.Instant;
import java.util.UUID;

public record ClusterResourceLogResponse(
        UUID clusterId,
        String namespace,
        String resourceType,
        String resourceName,
        String podName,
        String containerName,
        int tailLines,
        boolean previous,
        String log,
        boolean truncated,
        Instant collectedAt
) {
    /** ClusterResourceLogResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static ClusterResourceLogResponse from(ClusterResourceLogResult result) {
        return new ClusterResourceLogResponse(result.clusterId(), result.namespace(), result.resourceType(),
                result.resourceName(), result.podName(), result.containerName(), result.tailLines(), result.previous(),
                result.log(), result.truncated(), result.collectedAt());
    }
}
