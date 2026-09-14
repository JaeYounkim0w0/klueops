package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.cluster.Cluster;

public interface RegisterClusterUseCase {

    Cluster registerCluster(RegisterClusterCommand command, String actor, String requestId);
}
