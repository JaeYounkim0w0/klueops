package io.strato.aiops.domain.job;

public enum AsyncJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED,
    TIMEOUT;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED || this == TIMEOUT;
    }
}
