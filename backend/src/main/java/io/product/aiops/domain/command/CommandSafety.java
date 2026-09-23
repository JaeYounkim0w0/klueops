package io.product.aiops.domain.command;

public enum CommandSafety {
    READ_ONLY,
    DIAGNOSE,
    CHANGE,
    DESTRUCTIVE,
    PRIVILEGED_INTERACTIVE
}

