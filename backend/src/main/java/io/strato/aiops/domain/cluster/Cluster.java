package io.strato.aiops.domain.cluster;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.strato.aiops.domain.tenancy.TenancyDefaults;

public final class Cluster {

    private final UUID id;
    private final UUID tenantId;
    private final UUID workspaceId;
    private final String name;
    private final String description;
    private final ClusterEnvironment environment;
    private final ClusterProvider provider;
    private final String region;
    private final ClusterStatus status;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;

    /** Cluster 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public Cluster(UUID id, UUID tenantId, UUID workspaceId, String name, String description, ClusterEnvironment environment, ClusterProvider provider,
                   String region, ClusterStatus status, String createdBy, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.region = region;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /** Cluster 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public Cluster(UUID id, String name, String description, ClusterEnvironment environment, ClusterProvider provider,
                   String region, ClusterStatus status, String createdBy, Instant createdAt, Instant updatedAt) {
        this(id, TenancyDefaults.TENANT_ID, TenancyDefaults.WORKSPACE_ID, name, description, environment, provider,
                region, status, createdBy, createdAt, updatedAt);
    }

    /** Cluster의 register 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static Cluster register(String name, String description, ClusterEnvironment environment, ClusterProvider provider,
                                   String region, String actor) {
        return register(TenancyDefaults.TENANT_ID, TenancyDefaults.WORKSPACE_ID, name, description, environment, provider, region, actor);
    }

    /** Cluster의 register 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static Cluster register(UUID tenantId, UUID workspaceId, String name, String description,
                                   ClusterEnvironment environment, ClusterProvider provider, String region, String actor) {
        Instant now = Instant.now();
        return new Cluster(UUID.randomUUID(), tenantId, workspaceId, name, description, environment, provider, region,
                ClusterStatus.REGISTERED, actor, now, now);
    }

    /** Cluster의 id 처리에 필요한 업무 로직을 수행한다. */
    public UUID id() {
        return id;
    }

    /** Cluster의 tenantId 처리에 필요한 업무 로직을 수행한다. */
    public UUID tenantId() { return tenantId; }

    /** Cluster의 workspaceId 처리에 필요한 업무 로직을 수행한다. */
    public UUID workspaceId() { return workspaceId; }

    /** Cluster의 name 처리에 필요한 업무 로직을 수행한다. */
    public String name() {
        return name;
    }

    /** Cluster의 description 처리에 필요한 업무 로직을 수행한다. */
    public String description() {
        return description;
    }

    /** Cluster의 environment 처리에 필요한 업무 로직을 수행한다. */
    public ClusterEnvironment environment() {
        return environment;
    }

    /** Cluster의 provider 처리에 필요한 업무 로직을 수행한다. */
    public ClusterProvider provider() {
        return provider;
    }

    /** Cluster의 region 처리에 필요한 업무 로직을 수행한다. */
    public String region() {
        return region;
    }

    /** Cluster의 status 처리에 필요한 업무 로직을 수행한다. */
    public ClusterStatus status() {
        return status;
    }

    /** Cluster의 createdBy 처리에 필요한 데이터를 생성하거나 저장한다. */
    public String createdBy() {
        return createdBy;
    }

    /** Cluster의 createdAt 처리에 필요한 데이터를 생성하거나 저장한다. */
    public Instant createdAt() {
        return createdAt;
    }

    /** Cluster의 updatedAt 처리 대상의 상태를 갱신한다. */
    public Instant updatedAt() {
        return updatedAt;
    }
}
