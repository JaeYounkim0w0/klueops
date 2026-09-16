package io.strato.aiops.domain.identity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class AccessPolicy {

    private static final Map<PlatformRole, Set<Capability>> GRANTS = grants();

    /** AccessPolicy의 allows 처리에 필요한 업무 로직을 수행한다. */
    public boolean allows(PlatformRole role, Capability capability) {
        return GRANTS.getOrDefault(role, Set.of()).contains(capability);
    }

    /** AccessPolicy의 capabilities 처리에 필요한 업무 로직을 수행한다. */
    public Set<Capability> capabilities(PlatformRole role) {
        return Set.copyOf(GRANTS.getOrDefault(role, Set.of()));
    }

    /** AccessPolicy의 grants 처리에 필요한 업무 로직을 수행한다. */
    private static Map<PlatformRole, Set<Capability>> grants() {
        Map<PlatformRole, Set<Capability>> grants = new EnumMap<>(PlatformRole.class);
        grants.put(PlatformRole.PLATFORM_ADMIN, EnumSet.allOf(Capability.class));
        // Tenant 관리자는 현재 Tenant의 사용자·공유 자산·배포를 관리하지만 Platform Provider와 Model은 관리하지 않는다.
        grants.put(PlatformRole.TENANT_ADMIN, EnumSet.of(
                Capability.TENANT_READ,
                Capability.TENANT_MANAGE,
                Capability.TENANT_MEMBER_MANAGE,
                Capability.CLUSTER_READ,
                Capability.CLUSTER_MANAGE,
                Capability.ANALYSIS_READ,
                Capability.ANALYSIS_RUN,
                Capability.OPERATION_EXECUTE,
                Capability.POLICY_MANAGE,
                Capability.AUDIT_READ,
                Capability.CHART_READ,
                Capability.CHART_IMPORT,
                Capability.CHART_MANAGE,
                Capability.VALUES_EDIT,
                Capability.APPLICATION_READ,
                Capability.APPLICATION_DEPLOY,
                Capability.APPLICATION_ROLLBACK,
                Capability.APPLICATION_DELETE,
                Capability.APPLICATION_EXPOSURE,
                Capability.NAMESPACE_CREATE,
                Capability.AI_ROUTING_MANAGE
        ));
        grants.put(PlatformRole.CLUSTER_ADMIN, EnumSet.of(
                Capability.TENANT_READ,
                Capability.CLUSTER_READ,
                Capability.CLUSTER_MANAGE,
                Capability.ANALYSIS_READ,
                Capability.ANALYSIS_RUN,
                Capability.OPERATION_EXECUTE,
                Capability.POLICY_MANAGE,
                Capability.AUDIT_READ,
                Capability.CHART_READ,
                Capability.VALUES_EDIT,
                Capability.APPLICATION_READ,
                Capability.APPLICATION_DEPLOY,
                Capability.APPLICATION_ROLLBACK,
                Capability.APPLICATION_DELETE,
                Capability.APPLICATION_EXPOSURE,
                Capability.NAMESPACE_CREATE
        ));
        grants.put(PlatformRole.OPERATOR, EnumSet.of(
                Capability.TENANT_READ,
                Capability.CLUSTER_READ,
                Capability.ANALYSIS_READ,
                Capability.ANALYSIS_RUN,
                Capability.OPERATION_EXECUTE,
                Capability.CHART_READ,
                Capability.VALUES_EDIT,
                Capability.APPLICATION_READ,
                Capability.APPLICATION_DEPLOY,
                Capability.APPLICATION_ROLLBACK
        ));
        grants.put(PlatformRole.VIEWER, EnumSet.of(
                Capability.TENANT_READ,
                Capability.CLUSTER_READ,
                Capability.ANALYSIS_READ,
                Capability.CHART_READ,
                Capability.APPLICATION_READ,
                Capability.AUDIT_READ
        ));
        return Map.copyOf(grants);
    }
}
