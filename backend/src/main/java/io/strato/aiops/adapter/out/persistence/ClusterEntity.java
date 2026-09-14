package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;
import io.strato.aiops.domain.cluster.ClusterStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clusters")
class ClusterEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClusterEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClusterProvider provider;

    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClusterStatus status;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ClusterEntity() {
    }

    private ClusterEntity(UUID id, UUID tenantId, UUID workspaceId, String name, String description, ClusterEnvironment environment, ClusterProvider provider,
                          String region, ClusterStatus status, String createdBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.name = name;
        this.description = description;
        this.environment = environment;
        this.provider = provider;
        this.region = region;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static ClusterEntity fromDomain(Cluster cluster) {
        return new ClusterEntity(
                cluster.id(),
                cluster.tenantId(),
                cluster.workspaceId(),
                cluster.name(),
                cluster.description(),
                cluster.environment(),
                cluster.provider(),
                cluster.region(),
                cluster.status(),
                cluster.createdBy(),
                cluster.createdAt(),
                cluster.updatedAt()
        );
    }

    Cluster toDomain() {
        return new Cluster(id, tenantId, workspaceId, name, description, environment, provider, region, status, createdBy, createdAt, updatedAt);
    }
}
