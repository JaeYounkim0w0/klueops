package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface DeleteClusterUseCase {

    void deleteCluster(UUID clusterId, String actor, String requestId);
}
