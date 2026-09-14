package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CommandFavoriteJpaRepository extends JpaRepository<CommandFavoriteEntity, UUID> {
    List<CommandFavoriteEntity> findByClusterIdAndOwnerUserIdOrClusterIdAndSharedTrueOrderBySortOrderAscUpdatedAtDesc(
            UUID ownerClusterId, String ownerUserId, UUID sharedClusterId);
}

