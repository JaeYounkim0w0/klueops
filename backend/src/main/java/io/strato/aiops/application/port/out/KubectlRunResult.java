package io.strato.aiops.application.port.out;

public record KubectlRunResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        boolean canceled,
        boolean truncated,
        long durationMs
) {
}

