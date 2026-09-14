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

    TenantPersistenceAdapter(TenantJpaRepository repository) {
        this.repository = repository;
    }

    @Override public Tenant save(Tenant tenant) { return repository.save(TenantEntity.fromDomain(tenant)).toDomain(); }
    @Override public Optional<Tenant> findById(UUID id) { return repository.findById(id).map(TenantEntity::toDomain); }
    @Override public Optional<Tenant> findByCode(String code) { return repository.findByCode(code).map(TenantEntity::toDomain); }
    @Override public List<Tenant> findAll() { return repository.findAllByOrderByNameAsc().stream().map(TenantEntity::toDomain).toList(); }
}
