package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.application.ManagedApplication;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ManagedApplicationRepositoryPort {

    /** ManagedApplicationRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    ManagedApplication save(ManagedApplication application);

    /** ManagedApplicationRepositoryPort의 saveAndFlush 처리에 필요한 데이터를 생성하거나 저장한다. */
    default ManagedApplication saveAndFlush(ManagedApplication application) {
        return save(application);
    }

    /** ManagedApplicationRepositoryPort의 findById 처리 결과를 조회해 반환한다. */
    Optional<ManagedApplication> findById(UUID applicationId);

    /** ManagedApplicationRepositoryPort의 findByIdForUpdate 처리 결과를 조회해 반환한다. */
    default Optional<ManagedApplication> findByIdForUpdate(UUID applicationId) {
        return findById(applicationId);
    }

    /** ManagedApplicationRepositoryPort의 findRecent 처리 결과를 조회해 반환한다. */
    List<ManagedApplication> findRecent(int limit);

    /** ManagedApplicationRepositoryPort의 findRecentByClusterIds 처리 결과를 조회해 반환한다. */
    default List<ManagedApplication> findRecentByClusterIds(Collection<UUID> clusterIds, int limit) {
        return findRecent(limit).stream().filter(item -> clusterIds.contains(item.clusterId())).toList();
    }
}
