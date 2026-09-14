package io.strato.aiops.application.port.in;

public record ClusterResourceLogStreamResult(
        String reason,
        long lineCount,
        long durationMs
) {
}
