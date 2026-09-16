package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.TenantRepositoryPort;
import io.strato.aiops.domain.tenancy.Tenant;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class TenantPersistenceAdapter implements TenantRepositoryPort {
    private final TenantJpaRepository repository;

    /** TenantPersistenceAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    TenantPersistenceAdapter(TenantJpaRepository repository) {
        this.repository = repository;
    }

    /** TenantPersistenceAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override public Tenant save(Tenant tenant) { return repository.save(TenantEntity.fromDomain(tenant)).toDomain(); }
    /** TenantPersistenceAdapter의 findById 처리 결과를 조회해 반환한다. */
    @Override public Optional<Tenant> findById(UUID id) { return repository.findById(id).map(TenantEntity::toDomain); }
    /** TenantPersistenceAdapter의 findByCode 처리 결과를 조회해 반환한다. */
    @Override public Optional<Tenant> findByCode(String code) { return repository.findByCode(code).map(TenantEntity::toDomain); }
    /** TenantPersistenceAdapter의 findAll 처리 결과를 조회해 반환한다. */
    @Override public List<Tenant> findAll() { return repository.findAllByOrderByNameAsc().stream().map(TenantEntity::toDomain).toList(); }
}
