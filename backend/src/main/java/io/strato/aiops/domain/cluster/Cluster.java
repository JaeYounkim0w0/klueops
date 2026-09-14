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

    public Cluster(UUID id, String name, String description, ClusterEnvironment environment, ClusterProvider provider,
                   String region, ClusterStatus status, String createdBy, Instant createdAt, Instant updatedAt) {
        this(id, TenancyDefaults.TENANT_ID, TenancyDefaults.WORKSPACE_ID, name, description, environment, provider,
                region, status, createdBy, createdAt, updatedAt);
    }

    public static Cluster register(String name, String description, ClusterEnvironment environment, ClusterProvider provider,
                                   String region, String actor) {
        return register(TenancyDefaults.TENANT_ID, TenancyDefaults.WORKSPACE_ID, name, description, environment, provider, region, actor);
    }

    public static Cluster register(UUID tenantId, UUID workspaceId, String name, String description,
                                   ClusterEnvironment environment, ClusterProvider provider, String region, String actor) {
        Instant now = Instant.now();
        return new Cluster(UUID.randomUUID(), tenantId, workspaceId, name, description, environment, provider, region,
                ClusterStatus.REGISTERED, actor, now, now);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() { return tenantId; }

    public UUID workspaceId() { return workspaceId; }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ClusterEnvironment environment() {
        return environment;
    }

    public ClusterProvider provider() {
        return provider;
    }

    public String region() {
        return region;
    }

    public ClusterStatus status() {
        return status;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
