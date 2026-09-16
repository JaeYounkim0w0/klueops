package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

interface ManagedApplicationJpaRepository extends JpaRepository<ManagedApplicationEntity, UUID> {

    /** ManagedApplicationJpaRepository의 findByIdForUpdate 처리 결과를 조회해 반환한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from ManagedApplicationEntity application where application.id = :applicationId")
    java.util.Optional<ManagedApplicationEntity> findByIdForUpdate(@Param("applicationId") UUID applicationId);

    /** ManagedApplicationJpaRepository의 findAllByOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    List<ManagedApplicationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** ManagedApplicationJpaRepository의 findByClusterIdInOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    List<ManagedApplicationEntity> findByClusterIdInOrderByCreatedAtDesc(Collection<UUID> clusterIds, Pageable pageable);
}
