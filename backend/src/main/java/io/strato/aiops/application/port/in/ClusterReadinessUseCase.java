package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface ClusterReadinessUseCase {

    ClusterReadinessReport getReadiness(UUID clusterId, String namespace, String targetVersion, boolean refresh);
}
