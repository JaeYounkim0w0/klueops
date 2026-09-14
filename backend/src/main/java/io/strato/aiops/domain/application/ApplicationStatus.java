package io.strato.aiops.domain.application;

public enum ApplicationStatus {
    DEPLOY_REQUESTED,
    RUNNING,
    DEGRADED,
    FAILED,
    UNKNOWN
}
