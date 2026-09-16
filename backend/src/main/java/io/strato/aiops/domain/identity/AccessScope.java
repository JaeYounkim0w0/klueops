package io.strato.aiops.domain.identity;

import java.util.Objects;
import java.util.UUID;

public record AccessScope(ScopeType type, UUID tenantId, UUID workspaceId, UUID clusterId, String namespace) {

    /** AccessScope 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AccessScope(ScopeType type, UUID clusterId, String namespace) {
        this(type, null, null, clusterId, namespace);
    }

    /** AccessScope 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AccessScope {
        Objects.requireNonNull(type, "type is required");
        if (type == ScopeType.PLATFORM && (tenantId != null || workspaceId != null || clusterId != null || namespace != null)) {
            throw new IllegalArgumentException("Platform scope cannot target a resource");
        }
        if (type == ScopeType.TENANT && (tenantId == null || workspaceId != null || clusterId != null || namespace != null)) {
            throw new IllegalArgumentException("Tenant scope requires only a tenant id");
        }
        if (type == ScopeType.WORKSPACE && (tenantId == null || workspaceId == null || clusterId != null || namespace != null)) {
            throw new IllegalArgumentException("Workspace scope requires tenant and workspace ids");
        }
        if (type == ScopeType.CLUSTER && (tenantId != null || workspaceId != null || clusterId == null || namespace != null)) {
            throw new IllegalArgumentException("Cluster scope requires only a cluster id");
        }
        if (type == ScopeType.NAMESPACE && (tenantId != null || workspaceId != null || clusterId == null || namespace == null || namespace.isBlank())) {
            throw new IllegalArgumentException("Namespace scope requires a cluster id and namespace");
        }
    }

    /** AccessScope의 platform 처리에 필요한 업무 로직을 수행한다. */
    public static AccessScope platform() {
        return new AccessScope(ScopeType.PLATFORM, null, null, null, null);
    }

    /** AccessScope의 tenant 처리에 필요한 업무 로직을 수행한다. */
    public static AccessScope tenant(UUID tenantId) {
        return new AccessScope(ScopeType.TENANT, tenantId, null, null, null);
    }

    /** AccessScope의 workspace 처리에 필요한 업무 로직을 수행한다. */
    public static AccessScope workspace(UUID tenantId, UUID workspaceId) {
        return new AccessScope(ScopeType.WORKSPACE, tenantId, workspaceId, null, null);
    }

    /** AccessScope의 cluster 처리에 필요한 업무 로직을 수행한다. */
    public static AccessScope cluster(UUID clusterId) {
        return new AccessScope(ScopeType.CLUSTER, null, null, clusterId, null);
    }

    /** AccessScope의 namespace 처리에 필요한 업무 로직을 수행한다. */
    public static AccessScope namespace(UUID clusterId, String namespace) {
        return new AccessScope(ScopeType.NAMESPACE, null, null, clusterId, namespace);
    }

    /** AccessScope의 includes 처리에 필요한 업무 로직을 수행한다. */
    public boolean includes(UUID targetClusterId, String targetNamespace) {
        return switch (type) {
            case PLATFORM -> true;
            case TENANT, WORKSPACE -> false;
            case CLUSTER -> clusterId.equals(targetClusterId);
            case NAMESPACE -> clusterId.equals(targetClusterId) && namespace.equals(targetNamespace);
        };
    }

    /** AccessScope의 includes 처리에 필요한 업무 로직을 수행한다. */
    public boolean includes(AccessTarget target) {
        Objects.requireNonNull(target, "target is required");
        return switch (type) {
            case PLATFORM -> true;
            case TENANT -> tenantId.equals(target.tenantId());
            case WORKSPACE -> tenantId.equals(target.tenantId()) && workspaceId.equals(target.workspaceId());
            case CLUSTER -> clusterId.equals(target.clusterId());
            case NAMESPACE -> clusterId.equals(target.clusterId()) && namespace.equals(target.namespace());
        };
    }
}
