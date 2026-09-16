package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.cluster.Cluster;

import java.util.List;
import java.util.UUID;

public interface GetClusterUseCase {

    /** GetClusterUseCase의 listClusters 처리 결과를 조회해 반환한다. */
    List<Cluster> listClusters();

    /** GetClusterUseCase의 listClusters 처리 결과를 조회해 반환한다. */
    List<Cluster> listClusters(UUID tenantId, UUID workspaceId);

    /** GetClusterUseCase의 getCluster 처리 결과를 조회해 반환한다. */
    Cluster getCluster(UUID clusterId);
}
