package io.strato.aiops.application.service;

public class CommandConfirmationRequiredException extends RuntimeException {
    private final String safety;
    private final String target;

    public CommandConfirmationRequiredException(String safety, String target) {
        super("Command confirmation is required");
        this.safety = safety;
        this.target = target;
    }

    public String safety() { return safety; }
    public String target() { return target; }
}

