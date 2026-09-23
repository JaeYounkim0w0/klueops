package io.product.aiops.domain.command;

public enum CommandStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELED
}

