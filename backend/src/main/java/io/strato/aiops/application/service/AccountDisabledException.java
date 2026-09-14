package io.strato.aiops.application.service;

public class AccountDisabledException extends RuntimeException {
    public AccountDisabledException() {
        super("The platform account is disabled");
    }
}
