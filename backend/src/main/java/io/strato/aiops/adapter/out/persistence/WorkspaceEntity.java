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

    /** WorkspaceEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected WorkspaceEntity() {
    }

    /** WorkspaceEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** WorkspaceEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static WorkspaceEntity fromDomain(Workspace workspace) {
        return new WorkspaceEntity(workspace);
    }

    /** WorkspaceEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    Workspace toDomain() {
        return new Workspace(id, tenantId, code, name, description, status, createdBy, createdAt, updatedAt);
    }
}
