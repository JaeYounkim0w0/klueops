package io.strato.aiops.domain.identity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPolicyTest {

    private final AccessPolicy policy = new AccessPolicy();
    private final UUID tenantA = UUID.randomUUID();
    private final UUID workspaceA = UUID.randomUUID();
    private final UUID clusterA = UUID.randomUUID();
    private final UUID clusterB = UUID.randomUUID();

    @Test
    void grantsCapabilitiesByRoleWithoutImplicitWriteAccess() {
        assertThat(policy.allows(PlatformRole.PLATFORM_ADMIN, Capability.IDENTITY_MANAGE)).isTrue();
        assertThat(policy.allows(PlatformRole.TENANT_ADMIN, Capability.TENANT_MEMBER_MANAGE)).isTrue();
        assertThat(policy.allows(PlatformRole.TENANT_ADMIN, Capability.AI_PROVIDER_MANAGE)).isFalse();
        assertThat(policy.allows(PlatformRole.CLUSTER_ADMIN, Capability.CLUSTER_MANAGE)).isTrue();
        assertThat(policy.allows(PlatformRole.CLUSTER_ADMIN, Capability.APPLICATION_DELETE)).isTrue();
        assertThat(policy.allows(PlatformRole.OPERATOR, Capability.ANALYSIS_RUN)).isTrue();
        assertThat(policy.allows(PlatformRole.OPERATOR, Capability.APPLICATION_DELETE)).isFalse();
        assertThat(policy.allows(PlatformRole.VIEWER, Capability.ANALYSIS_READ)).isTrue();

        assertThat(policy.allows(PlatformRole.CLUSTER_ADMIN, Capability.IDENTITY_MANAGE)).isFalse();
        assertThat(policy.allows(PlatformRole.OPERATOR, Capability.CLUSTER_MANAGE)).isFalse();
        assertThat(policy.allows(PlatformRole.VIEWER, Capability.ANALYSIS_RUN)).isFalse();
    }

    @Test
    void platformScopeIncludesEveryClusterAndNamespace() {
        AccessScope scope = AccessScope.platform();

        assertThat(scope.includes(clusterA, "default")).isTrue();
        assertThat(scope.includes(clusterB, "production")).isTrue();
    }

    @Test
    void clusterScopeDoesNotCrossClusterBoundary() {
        AccessScope scope = AccessScope.cluster(clusterA);

        assertThat(scope.includes(clusterA, null)).isTrue();
        assertThat(scope.includes(clusterA, "default")).isTrue();
        assertThat(scope.includes(clusterB, "default")).isFalse();
    }

    @Test
    void namespaceScopeRequiresExactClusterAndNamespace() {
        AccessScope scope = AccessScope.namespace(clusterA, "payments");

        assertThat(scope.includes(clusterA, "payments")).isTrue();
        assertThat(scope.includes(clusterA, "default")).isFalse();
        assertThat(scope.includes(clusterB, "payments")).isFalse();
        assertThat(scope.includes(clusterA, null)).isFalse();
    }

    @Test
    void tenantAndWorkspaceScopesIncludeOnlyTheirOwnedClusterPlacement() {
        AccessTarget target = new AccessTarget(tenantA, workspaceA, clusterA, "payments");

        assertThat(AccessScope.tenant(tenantA).includes(target)).isTrue();
        assertThat(AccessScope.tenant(UUID.randomUUID()).includes(target)).isFalse();
        assertThat(AccessScope.workspace(tenantA, workspaceA).includes(target)).isTrue();
        assertThat(AccessScope.workspace(tenantA, UUID.randomUUID()).includes(target)).isFalse();
        assertThat(AccessScope.cluster(clusterA).includes(target)).isTrue();
        assertThat(AccessScope.namespace(clusterA, "payments").includes(target)).isTrue();
    }
}
