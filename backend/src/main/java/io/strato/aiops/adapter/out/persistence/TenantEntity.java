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

    /** TenantEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected TenantEntity() {
    }

    /** TenantEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** TenantEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static TenantEntity fromDomain(Tenant tenant) {
        return new TenantEntity(tenant);
    }

    /** TenantEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    Tenant toDomain() {
        return new Tenant(id, code, name, description, status, createdBy, createdAt, updatedAt);
    }
}
