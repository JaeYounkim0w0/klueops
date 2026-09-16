package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CommandFavoriteJpaRepository extends JpaRepository<CommandFavoriteEntity, UUID> {
    /** CommandFavoriteJpaRepository의 findByClusterIdAndOwnerUserIdOrClusterIdAndSharedTrueOrderBySortOrderAscUpdatedAtDesc 처리 결과를 조회해 반환한다. */
    List<CommandFavoriteEntity> findByClusterIdAndOwnerUserIdOrClusterIdAndSharedTrueOrderBySortOrderAscUpdatedAtDesc(
            UUID ownerClusterId, String ownerUserId, UUID sharedClusterId);
}

