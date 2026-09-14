package io.strato.aiops.domain.command;

public enum CommandVerificationStatus {
    NOT_REQUIRED,
    PENDING,
    VERIFIED_CHANGED,
    VERIFIED_STABLE,
    VERIFICATION_FAILED
}
