package io.strato.aiops.application.service;

public class CommandCapacityExceededException extends RuntimeException {
    private final int retryAfterSeconds;

    public CommandCapacityExceededException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
