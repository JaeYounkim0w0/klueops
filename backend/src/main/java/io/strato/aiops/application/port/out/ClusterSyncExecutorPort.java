package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface ClusterSyncExecutorPort {

    void submitClusterSync(UUID asyncJobId);
}
