package io.strato.aiops.application.service;

public class InteractiveCommandRequiredException extends RuntimeException {
    public InteractiveCommandRequiredException() {
        super("Interactive kubectl commands require the terminal session endpoint");
    }
}
