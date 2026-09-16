package io.strato.aiops.adapter.in.web;

public class CredentialRevealDisabledException extends RuntimeException {

    /** CredentialRevealDisabledException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public CredentialRevealDisabledException() {
        super("Stored cluster credential reveal is disabled by runtime policy");
    }
}
