package io.strato.aiops.application.service;

public class ApplicationOperationConflictException extends RuntimeException {

    /** ApplicationOperationConflictException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ApplicationOperationConflictException(String message) {
        super(message);
    }

    /** ApplicationOperationConflictException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ApplicationOperationConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
