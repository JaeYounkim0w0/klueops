package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.cluster.Cluster;

import java.util.List;
import java.util.UUID;

public interface GetClusterUseCase {

    List<Cluster> listClusters();

    List<Cluster> listClusters(UUID tenantId, UUID workspaceId);

    Cluster getCluster(UUID clusterId);
}
