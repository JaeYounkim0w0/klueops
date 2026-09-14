package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.WorkspaceRepositoryPort;
import io.strato.aiops.domain.tenancy.Workspace;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class WorkspacePersistenceAdapter implements WorkspaceRepositoryPort {
    private final WorkspaceJpaRepository repository;

    WorkspacePersistenceAdapter(WorkspaceJpaRepository repository) {
        this.repository = repository;
    }

    @Override public Workspace save(Workspace workspace) { return repository.save(WorkspaceEntity.fromDomain(workspace)).toDomain(); }
    @Override public Optional<Workspace> findById(UUID id) { return repository.findById(id).map(WorkspaceEntity::toDomain); }
    @Override public Optional<Workspace> findByTenantIdAndCode(UUID tenantId, String code) { return repository.findByTenantIdAndCode(tenantId, code).map(WorkspaceEntity::toDomain); }
    @Override public List<Workspace> findByTenantId(UUID tenantId) { return repository.findByTenantIdOrderByNameAsc(tenantId).stream().map(WorkspaceEntity::toDomain).toList(); }
    @Override public List<Workspace> findAll() { return repository.findAllByOrderByNameAsc().stream().map(WorkspaceEntity::toDomain).toList(); }
}
