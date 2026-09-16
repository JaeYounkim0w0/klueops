package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface ClusterSyncExecutorPort {

    /** ClusterSyncExecutorPort의 submitClusterSync 처리 계약을 정의한다. */
    void submitClusterSync(UUID asyncJobId);
}
