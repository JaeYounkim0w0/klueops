package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.ManagedApplicationRepositoryPort;
import io.strato.aiops.domain.application.ManagedApplication;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaManagedApplicationRepositoryAdapter implements ManagedApplicationRepositoryPort {

    private final ManagedApplicationJpaRepository repository;

    /** JpaManagedApplicationRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaManagedApplicationRepositoryAdapter(ManagedApplicationJpaRepository repository) {
        this.repository = repository;
    }

    /** JpaManagedApplicationRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public ManagedApplication save(ManagedApplication application) {
        return repository.save(ManagedApplicationEntity.fromDomain(application)).toDomain();
    }

    /** JpaManagedApplicationRepositoryAdapter의 saveAndFlush 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public ManagedApplication saveAndFlush(ManagedApplication application) {
        return repository.saveAndFlush(ManagedApplicationEntity.fromDomain(application)).toDomain();
    }

    /** JpaManagedApplicationRepositoryAdapter의 findById 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<ManagedApplication> findById(UUID applicationId) {
        return repository.findById(applicationId).map(ManagedApplicationEntity::toDomain);
    }

    /** JpaManagedApplicationRepositoryAdapter의 findByIdForUpdate 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<ManagedApplication> findByIdForUpdate(UUID applicationId) {
        return repository.findByIdForUpdate(applicationId).map(ManagedApplicationEntity::toDomain);
    }

    /** JpaManagedApplicationRepositoryAdapter의 findRecent 처리 결과를 조회해 반환한다. */
    @Override
    public List<ManagedApplication> findRecent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(ManagedApplicationEntity::toDomain)
                .toList();
    }

    /** JpaManagedApplicationRepositoryAdapter의 findRecentByClusterIds 처리 결과를 조회해 반환한다. */
    @Override
    public List<ManagedApplication> findRecentByClusterIds(Collection<UUID> clusterIds, int limit) {
        if (clusterIds.isEmpty()) return List.of();
        return repository.findByClusterIdInOrderByCreatedAtDesc(clusterIds, PageRequest.of(0, limit)).stream()
                .map(ManagedApplicationEntity::toDomain)
                .toList();
    }
}
