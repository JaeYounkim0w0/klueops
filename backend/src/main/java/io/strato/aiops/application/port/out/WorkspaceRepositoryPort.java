package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.tenancy.Workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepositoryPort {
    /** WorkspaceRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    Workspace save(Workspace workspace);
    /** WorkspaceRepositoryPort의 findById 처리 결과를 조회해 반환한다. */
    Optional<Workspace> findById(UUID id);
    /** WorkspaceRepositoryPort의 findByTenantIdAndCode 처리 결과를 조회해 반환한다. */
    Optional<Workspace> findByTenantIdAndCode(UUID tenantId, String code);
    /** WorkspaceRepositoryPort의 findByTenantId 처리 결과를 조회해 반환한다. */
    List<Workspace> findByTenantId(UUID tenantId);
    /** WorkspaceRepositoryPort의 findAll 처리 결과를 조회해 반환한다. */
    List<Workspace> findAll();
}
