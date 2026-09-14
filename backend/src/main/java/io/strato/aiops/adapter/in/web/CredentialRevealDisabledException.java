package io.strato.aiops.adapter.in.web;

public class CredentialRevealDisabledException extends RuntimeException {

    public CredentialRevealDisabledException() {
        super("Stored cluster credential reveal is disabled by runtime policy");
    }
}
