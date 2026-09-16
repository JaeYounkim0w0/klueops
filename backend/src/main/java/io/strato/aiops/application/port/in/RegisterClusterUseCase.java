package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.cluster.Cluster;

public interface RegisterClusterUseCase {

    /** RegisterClusterUseCase의 registerCluster 처리에 필요한 데이터를 생성하거나 저장한다. */
    Cluster registerCluster(RegisterClusterCommand command, String actor, String requestId);
}
