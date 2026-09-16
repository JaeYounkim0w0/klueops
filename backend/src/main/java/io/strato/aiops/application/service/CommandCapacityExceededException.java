package io.strato.aiops.application.service;

public class CommandCapacityExceededException extends RuntimeException {
    private final int retryAfterSeconds;

    /** CommandCapacityExceededException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public CommandCapacityExceededException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** CommandCapacityExceededException의 retryAfterSeconds 처리에 필요한 업무 로직을 수행한다. */
    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
