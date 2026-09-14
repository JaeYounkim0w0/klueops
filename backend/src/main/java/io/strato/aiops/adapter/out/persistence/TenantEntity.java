package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.tenancy.Tenant;
import io.strato.aiops.domain.tenancy.TenantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
class TenantEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 63) private String code;
    @Column(nullable = false) private String name;
    @Column(length = 1000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TenantStatus status;
    @Column(nullable = false) private String createdBy;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected TenantEntity() {
    }

    private TenantEntity(Tenant tenant) {
        id = tenant.id();
        code = tenant.code();
        name = tenant.name();
        description = tenant.description();
        status = tenant.status();
        createdBy = tenant.createdBy();
        createdAt = tenant.createdAt();
        updatedAt = tenant.updatedAt();
    }

    static TenantEntity fromDomain(Tenant tenant) {
        return new TenantEntity(tenant);
    }

    Tenant toDomain() {
        return new Tenant(id, code, name, description, status, createdBy, createdAt, updatedAt);
    }
}
