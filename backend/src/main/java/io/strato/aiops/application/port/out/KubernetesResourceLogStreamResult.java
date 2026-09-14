package io.strato.aiops.application.port.out;

public record KubernetesResourceLogStreamResult(String reason, long lineCount, long durationMs) {
}
