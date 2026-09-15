package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.application.ManagedApplication;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ManagedApplicationRepositoryPort {

    ManagedApplication save(ManagedApplication application);

    default ManagedApplication saveAndFlush(ManagedApplication application) {
        return save(application);
    }

    Optional<ManagedApplication> findById(UUID applicationId);

    List<ManagedApplication> findRecent(int limit);

    default List<ManagedApplication> findRecentByClusterIds(Collection<UUID> clusterIds, int limit) {
        return findRecent(limit).stream().filter(item -> clusterIds.contains(item.clusterId())).toList();
    }
}
