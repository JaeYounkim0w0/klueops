package io.strato.aiops.domain.job;

public enum AsyncJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED,
    TIMEOUT;

    /** AsyncJobStatus의 isTerminal 처리 조건의 충족 여부를 판단한다. */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED || this == TIMEOUT;
    }
}
