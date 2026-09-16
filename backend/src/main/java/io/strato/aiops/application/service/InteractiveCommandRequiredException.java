package io.strato.aiops.application.service;

public class InteractiveCommandRequiredException extends RuntimeException {
    /** InteractiveCommandRequiredException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public InteractiveCommandRequiredException() {
        super("Interactive kubectl commands require the terminal session endpoint");
    }
}
