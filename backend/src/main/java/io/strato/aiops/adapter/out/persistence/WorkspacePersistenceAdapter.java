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

    /** WorkspacePersistenceAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    WorkspacePersistenceAdapter(WorkspaceJpaRepository repository) {
        this.repository = repository;
    }

    /** WorkspacePersistenceAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override public Workspace save(Workspace workspace) { return repository.save(WorkspaceEntity.fromDomain(workspace)).toDomain(); }
    /** WorkspacePersistenceAdapter의 findById 처리 결과를 조회해 반환한다. */
    @Override public Optional<Workspace> findById(UUID id) { return repository.findById(id).map(WorkspaceEntity::toDomain); }
    /** WorkspacePersistenceAdapter의 findByTenantIdAndCode 처리 결과를 조회해 반환한다. */
    @Override public Optional<Workspace> findByTenantIdAndCode(UUID tenantId, String code) { return repository.findByTenantIdAndCode(tenantId, code).map(WorkspaceEntity::toDomain); }
    /** WorkspacePersistenceAdapter의 findByTenantId 처리 결과를 조회해 반환한다. */
    @Override public List<Workspace> findByTenantId(UUID tenantId) { return repository.findByTenantIdOrderByNameAsc(tenantId).stream().map(WorkspaceEntity::toDomain).toList(); }
    /** WorkspacePersistenceAdapter의 findAll 처리 결과를 조회해 반환한다. */
    @Override public List<Workspace> findAll() { return repository.findAllByOrderByNameAsc().stream().map(WorkspaceEntity::toDomain).toList(); }
}
