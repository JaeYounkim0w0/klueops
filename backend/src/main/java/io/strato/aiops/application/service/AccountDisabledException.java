package io.strato.aiops.application.service;

public class AccountDisabledException extends RuntimeException {
    /** AccountDisabledException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AccountDisabledException() {
        super("The platform account is disabled");
    }
}
