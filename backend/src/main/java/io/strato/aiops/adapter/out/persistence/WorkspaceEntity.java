package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.tenancy.Workspace;
import io.strato.aiops.domain.tenancy.WorkspaceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
class WorkspaceEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 63) private String code;
    @Column(nullable = false) private String name;
    @Column(length = 1000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private WorkspaceStatus status;
    @Column(nullable = false) private String createdBy;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected WorkspaceEntity() {
    }

    private WorkspaceEntity(Workspace workspace) {
        id = workspace.id();
        tenantId = workspace.tenantId();
        code = workspace.code();
        name = workspace.name();
        description = workspace.description();
        status = workspace.status();
        createdBy = workspace.createdBy();
        createdAt = workspace.createdAt();
        updatedAt = workspace.updatedAt();
    }

    static WorkspaceEntity fromDomain(Workspace workspace) {
        return new WorkspaceEntity(workspace);
    }

    Workspace toDomain() {
        return new Workspace(id, tenantId, code, name, description, status, createdBy, createdAt, updatedAt);
    }
}
