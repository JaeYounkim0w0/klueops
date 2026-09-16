package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ClusterCredentialJpaRepository extends JpaRepository<ClusterCredentialEntity, UUID> {

    /** ClusterCredentialJpaRepository의 findFirstByClusterIdOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    Optional<ClusterCredentialEntity> findFirstByClusterIdOrderByCreatedAtDesc(UUID clusterId);
}
