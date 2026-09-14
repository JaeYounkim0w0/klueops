package io.strato.aiops.domain.identity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class AccessPolicy {

    private static final Map<PlatformRole, Set<Capability>> GRANTS = grants();

    public boolean allows(PlatformRole role, Capability capability) {
        return GRANTS.getOrDefault(role, Set.of()).contains(capability);
    }

    public Set<Capability> capabilities(PlatformRole role) {
        return Set.copyOf(GRANTS.getOrDefault(role, Set.of()));
    }

    private static Map<PlatformRole, Set<Capability>> grants() {
        Map<PlatformRole, Set<Capability>> grants = new EnumMap<>(PlatformRole.class);
        grants.put(PlatformRole.PLATFORM_ADMIN, EnumSet.allOf(Capability.class));
        grants.put(PlatformRole.CLUSTER_ADMIN, EnumSet.of(
                Capability.CLUSTER_READ,
                Capability.CLUSTER_MANAGE,
                Capability.ANALYSIS_READ,
                Capability.ANALYSIS_RUN,
                Capability.OPERATION_EXECUTE,
                Capability.POLICY_MANAGE,
                Capability.AUDIT_READ
        ));
        grants.put(PlatformRole.OPERATOR, EnumSet.of(
                Capability.CLUSTER_READ,
                Capability.ANALYSIS_READ,
                Capability.ANALYSIS_RUN,
                Capability.OPERATION_EXECUTE
        ));
        grants.put(PlatformRole.VIEWER, EnumSet.of(
                Capability.CLUSTER_READ,
                Capability.ANALYSIS_READ
        ));
        return Map.copyOf(grants);
    }
}
