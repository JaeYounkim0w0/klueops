package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ClusterCredentialJpaRepository extends JpaRepository<ClusterCredentialEntity, UUID> {

    Optional<ClusterCredentialEntity> findFirstByClusterIdOrderByCreatedAtDesc(UUID clusterId);
}
