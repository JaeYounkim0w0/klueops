package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.tenancy.Workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepositoryPort {
    Workspace save(Workspace workspace);
    Optional<Workspace> findById(UUID id);
    Optional<Workspace> findByTenantIdAndCode(UUID tenantId, String code);
    List<Workspace> findByTenantId(UUID tenantId);
    List<Workspace> findAll();
}
