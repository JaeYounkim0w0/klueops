package io.strato.aiops.domain.application;

public enum ApplicationStatus {
    DEPLOY_REQUESTED,
    DEPLOYING,
    UPGRADING,
    ROLLING_BACK,
    UNINSTALLING,
    RUNNING,
    RUNNING_ENDPOINT_DEGRADED,
    UNINSTALLED,
    DEGRADED,
    FAILED,
    UNKNOWN
}
