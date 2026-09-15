package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.RoleBindingRepositoryPort;
import io.strato.aiops.application.port.out.UserAccountRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.WorkspaceRepositoryPort;
import io.strato.aiops.application.port.out.OidcGroupMappingRepositoryPort;
import io.strato.aiops.application.port.out.TenantMembershipRepositoryPort;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;
import io.strato.aiops.domain.identity.AccessScope;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.identity.ExternalIdentity;
import io.strato.aiops.domain.identity.PlatformRole;
import io.strato.aiops.domain.identity.PrincipalType;
import io.strato.aiops.domain.identity.RoleBinding;
import io.strato.aiops.domain.identity.UserAccount;
import io.strato.aiops.domain.identity.OidcGroupMapping;
import io.strato.aiops.domain.identity.TenantMembership;
import io.strato.aiops.domain.identity.MembershipStatus;
import io.strato.aiops.domain.tenancy.Workspace;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityAccessServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryBindings bindings = new InMemoryBindings();
    private final InMemoryClusters clusters = new InMemoryClusters();
    private final InMemoryWorkspaces workspaces = new InMemoryWorkspaces();
    private final InMemoryGroupMappings groupMappings = new InMemoryGroupMappings();
    private final InMemoryMemberships memberships = new InMemoryMemberships();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);
    private final IdentityAccessService service = new IdentityAccessService(users, bindings, clusters, workspaces,
            groupMappings, memberships, clock);

    @Test
    void provisionsOnceAndRefreshesIdentityOnLaterLogin() {
        ExternalIdentity first = new ExternalIdentity("https://idp", "subject-1", "operator", "First Name",
                "operator@example.com", Set.of("aiops-operators"));
        ExternalIdentity changed = new ExternalIdentity("https://idp", "subject-1", "operator", "Changed Name",
                "operator@example.com", Set.of("aiops-operators"));

        UserAccount created = service.provision(first);
        UserAccount refreshed = service.provision(changed);

        assertThat(refreshed.id()).isEqualTo(created.id());
        assertThat(refreshed.displayName()).isEqualTo("Changed Name");
        assertThat(users.items).hasSize(1);
    }

    @Test
    void doesNotWriteUnchangedIdentityOnEveryApiRequest() {
        ExternalIdentity identity = new ExternalIdentity("https://idp", "subject-stable", "operator", "Operator",
                "operator@example.com", Set.of());

        service.provision(identity);
        service.provision(identity);

        assertThat(users.saveCount).isEqualTo(1);
    }

    @Test
    void combinesUserAndGroupBindingsIntoCapabilities() {
        UserAccount user = service.provision(new ExternalIdentity("https://idp", "subject-2", "viewer", "Viewer",
                "viewer@example.com", Set.of("team-operators")));
        bindings.save(RoleBinding.create(PrincipalType.USER, user.id().toString(), PlatformRole.VIEWER,
                AccessScope.platform(), "admin", clock.instant()));
        bindings.save(RoleBinding.create(PrincipalType.GROUP, "team-operators", PlatformRole.OPERATOR,
                AccessScope.cluster(UUID.fromString("11111111-1111-1111-1111-111111111111")), "admin", clock.instant()));

        var access = service.resolveAccess(user, Set.of("team-operators"));

        assertThat(access.capabilities()).contains(Capability.ANALYSIS_READ, Capability.ANALYSIS_RUN);
        assertThat(access.bindings()).hasSize(2);
    }

    @Test
    void rejectsDisabledAccount() {
        UserAccount user = service.provision(new ExternalIdentity("https://idp", "subject-3", "blocked", "Blocked",
                "blocked@example.com", Set.of()));
        users.save(user.withActive(false));

        assertThatThrownBy(() -> service.resolveAccess(users.findById(user.id()).orElseThrow(), Set.of()))
                .isInstanceOf(AccountDisabledException.class);
    }

    @Test
    void mapsConventionalOidcGroupsWithoutDatabaseBootstrap() {
        UserAccount user = service.provision(new ExternalIdentity("https://idp", "subject-4", "admin", "Admin",
                "admin@example.com", Set.of("aiops-platform-admins")));

        var access = service.resolveAccess(user, Set.of("aiops-platform-admins"));

        assertThat(access.capabilities()).contains(Capability.IDENTITY_MANAGE, Capability.PLATFORM_ADMIN);
        assertThat(access.bindings()).singleElement().satisfies(binding -> {
            assertThat(binding.role()).isEqualTo(PlatformRole.PLATFORM_ADMIN);
            assertThat(binding.scope()).isEqualTo(AccessScope.platform());
        });
    }

    @Test
    void limitsGlobalAndClusterVisibilityToAssignedScope() {
        UUID assignedCluster = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherCluster = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UserAccount user = service.provision(new ExternalIdentity("https://idp", "subject-5", "scoped", "Scoped",
                "scoped@example.com", Set.of()));
        bindings.save(RoleBinding.create(PrincipalType.USER, user.id().toString(), PlatformRole.OPERATOR,
                AccessScope.cluster(assignedCluster), "admin", clock.instant()));

        var access = service.resolveAccess(user, Set.of());

        assertThat(service.hasAccessAtAnyScope(access, Capability.CLUSTER_READ)).isTrue();
        assertThat(service.allows(access, Capability.CLUSTER_READ, null, null)).isFalse();
        assertThat(service.visibleClusterIds(access)).containsExactly(assignedCluster);
        assertThat(service.allows(access, Capability.CLUSTER_READ, assignedCluster, null)).isTrue();
        assertThat(service.allows(access, Capability.CLUSTER_READ, otherCluster, null)).isFalse();
    }

    @Test
    void platformScopeCanSeeEveryCluster() {
        UserAccount user = service.provision(new ExternalIdentity("https://idp", "subject-6", "platform", "Platform",
                "platform@example.com", Set.of()));
        bindings.save(RoleBinding.create(PrincipalType.USER, user.id().toString(), PlatformRole.VIEWER,
                AccessScope.platform(), "admin", clock.instant()));

        var access = service.resolveAccess(user, Set.of());

        assertThat(service.hasPlatformScope(access, Capability.CLUSTER_READ)).isTrue();
        assertThat(service.visibleClusterIds(access)).isEmpty();
    }

    @Test
    void doesNotInferTenantRoleFromConventionalGroupName() {
        UserAccount user = service.provision(new ExternalIdentity("https://idp", "subject-no-implicit", "operator",
                "Operator", "operator@example.com", Set.of("aiops-operators")));

        var access = service.resolveAccess(user, Set.of("aiops-operators"));

        assertThat(access.bindings()).isEmpty();
        assertThat(access.capabilities()).isEmpty();
    }

    @Test
    void resolvesOnlyExplicitIssuerAndGroupMapping() {
        UUID tenantId = UUID.randomUUID();
        UserAccount user = service.provision(new ExternalIdentity("https://idp", "mapped", "mapped", "Mapped",
                null, Set.of("/companies/aa/operators")));
        groupMappings.save(OidcGroupMapping.create("https://idp", "/companies/aa/operators", tenantId,
                PlatformRole.OPERATOR, AccessScope.tenant(tenantId), "admin", clock.instant()));

        var access = service.resolveAccess(user, Set.of("/companies/aa/operators"));

        assertThat(service.effectiveCapabilities(access, tenantId, null))
                .contains(Capability.APPLICATION_DEPLOY, Capability.ANALYSIS_RUN)
                .doesNotContain(Capability.APPLICATION_DELETE);
    }

    @Test
    void tenantAndWorkspaceBindingsInheritOnlyToOwnedClusters() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID workspaceA = UUID.randomUUID();
        UUID workspaceB = UUID.randomUUID();
        Cluster clusterA = clusters.save(Cluster.register(tenantA, workspaceA, "cluster-a", null,
                ClusterEnvironment.DEV, ClusterProvider.KIND, null, "admin"));
        Cluster clusterB = clusters.save(Cluster.register(tenantB, workspaceB, "cluster-b", null,
                ClusterEnvironment.DEV, ClusterProvider.KIND, null, "admin"));
        UserAccount user = service.provision(new ExternalIdentity("https://idp", "tenant-user", "tenant-user",
                "Tenant User", null, Set.of()));
        bindings.save(RoleBinding.create(PrincipalType.USER, user.id().toString(), PlatformRole.OPERATOR,
                AccessScope.tenant(tenantA), "admin", clock.instant()));

        var access = service.resolveAccess(user, Set.of());

        assertThat(service.allows(access, Capability.ANALYSIS_RUN, clusterA.id(), "default")).isTrue();
        assertThat(service.allows(access, Capability.ANALYSIS_RUN, clusterB.id(), "default")).isFalse();
        assertThat(service.visibleClusterIds(access)).containsExactly(clusterA.id());
        assertThat(service.visibleTenantIds(access)).containsExactly(tenantA);
        assertThat(service.visibleWorkspaceIds(access)).containsExactly(workspaceA);
    }

    @Test
    void tenantBindingAllowsOnlyDirectWorkspacesOwnedByThatTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        Workspace workspaceA = workspaces.save(Workspace.create(tenantA, "team-a", "Team A", null, "admin"));
        Workspace workspaceB = workspaces.save(Workspace.create(tenantB, "team-b", "Team B", null, "admin"));
        UserAccount user = service.provision(new ExternalIdentity("https://idp", "tenant-direct-user",
                "tenant-direct-user", "Tenant Direct User", null, Set.of()));
        bindings.save(RoleBinding.create(PrincipalType.USER, user.id().toString(), PlatformRole.VIEWER,
                AccessScope.tenant(tenantA), "admin", clock.instant()));

        var access = service.resolveAccess(user, Set.of());

        assertThat(service.allowsWorkspace(access, Capability.CLUSTER_READ, workspaceA.id())).isTrue();
        assertThat(service.allowsWorkspace(access, Capability.CLUSTER_READ, workspaceB.id())).isFalse();
    }

    @Test
    void protectsLastActivePlatformManagerFromDisableAndBindingDeletion() {
        UserAccount actor = service.provision(new ExternalIdentity("https://idp", "manager-actor", "actor",
                "Actor", null, Set.of()));
        UserAccount lastManager = service.provision(new ExternalIdentity("https://idp", "manager-last", "manager",
                "Manager", null, Set.of()));
        RoleBinding managerBinding = bindings.save(RoleBinding.create(PrincipalType.USER,
                lastManager.id().toString(), PlatformRole.PLATFORM_ADMIN, AccessScope.platform(), "bootstrap",
                clock.instant()));

        assertThatThrownBy(() -> service.setActive(lastManager.id(), false, actor.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Platform Manager");
        assertThatThrownBy(() -> service.deleteBinding(managerBinding.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Platform Manager");
    }

    @Test
    void allowsPlatformManagerRemovalWhenAnotherActiveManagerRemains() {
        UserAccount actor = service.provision(new ExternalIdentity("https://idp", "manager-one", "manager-one",
                "Manager One", null, Set.of()));
        UserAccount replacement = service.provision(new ExternalIdentity("https://idp", "manager-two", "manager-two",
                "Manager Two", null, Set.of()));
        RoleBinding actorBinding = bindings.save(RoleBinding.create(PrincipalType.USER, actor.id().toString(),
                PlatformRole.PLATFORM_ADMIN, AccessScope.platform(), "bootstrap", clock.instant()));
        bindings.save(RoleBinding.create(PrincipalType.USER, replacement.id().toString(),
                PlatformRole.PLATFORM_ADMIN, AccessScope.platform(), "bootstrap", clock.instant()));

        service.deleteBinding(actorBinding.id());

        assertThat(bindings.items).noneMatch(binding -> binding.id().equals(actorBinding.id()));
    }

    private static final class InMemoryUsers implements UserAccountRepositoryPort {
        private final Map<UUID, UserAccount> items = new LinkedHashMap<>();
        private int saveCount;

        @Override
        public Optional<UserAccount> findByIssuerAndSubject(String issuer, String subject) {
            return items.values().stream().filter(user -> user.issuer().equals(issuer) && user.subject().equals(subject)).findFirst();
        }

        @Override
        public void lockProvisioning(String issuer, String subject) {
        }

        @Override
        public Optional<UserAccount> findById(UUID id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public List<UserAccount> findAll() {
            return List.copyOf(items.values());
        }

        @Override
        public UserAccount save(UserAccount user) {
            saveCount++;
            items.put(user.id(), user);
            return user;
        }
    }

    private static final class InMemoryBindings implements RoleBindingRepositoryPort {
        private final List<RoleBinding> items = new ArrayList<>();

        @Override
        public List<RoleBinding> findByPrincipals(Collection<String> principals) {
            return items.stream().filter(binding -> principals.contains(binding.principalKey())).toList();
        }

        @Override
        public List<RoleBinding> findAll() {
            return List.copyOf(items);
        }

        @Override public Optional<RoleBinding> findById(UUID id) {
            return items.stream().filter(item -> item.id().equals(id)).findFirst();
        }

        @Override
        public RoleBinding save(RoleBinding binding) {
            items.removeIf(item -> item.id().equals(binding.id()));
            items.add(binding);
            return binding;
        }

        @Override
        public void deleteById(UUID id) {
            items.removeIf(item -> item.id().equals(id));
        }
    }

    private static final class InMemoryClusters implements ClusterRepositoryPort {
        private final Map<UUID, Cluster> items = new LinkedHashMap<>();

        @Override public Cluster save(Cluster cluster) { items.put(cluster.id(), cluster); return cluster; }
        @Override public Optional<Cluster> findById(UUID clusterId) { return Optional.ofNullable(items.get(clusterId)); }
        @Override public List<Cluster> findAll() { return List.copyOf(items.values()); }
    }

    private static final class InMemoryWorkspaces implements WorkspaceRepositoryPort {
        private final Map<UUID, Workspace> items = new LinkedHashMap<>();

        @Override public Workspace save(Workspace workspace) { items.put(workspace.id(), workspace); return workspace; }
        @Override public Optional<Workspace> findById(UUID id) { return Optional.ofNullable(items.get(id)); }
        @Override public Optional<Workspace> findByTenantIdAndCode(UUID tenantId, String code) {
            return items.values().stream().filter(item -> item.tenantId().equals(tenantId) && item.code().equals(code)).findFirst();
        }
        @Override public List<Workspace> findByTenantId(UUID tenantId) {
            return items.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList();
        }
        @Override public List<Workspace> findAll() { return List.copyOf(items.values()); }
    }

    private static final class InMemoryGroupMappings implements OidcGroupMappingRepositoryPort {
        private final List<OidcGroupMapping> items = new ArrayList<>();

        @Override public List<OidcGroupMapping> findActive(String issuer, Collection<String> groups) {
            return items.stream().filter(OidcGroupMapping::active)
                    .filter(item -> item.issuer().equals(issuer) && groups.contains(item.groupValue())).toList();
        }
        @Override public List<OidcGroupMapping> findByTenantId(UUID tenantId) {
            return items.stream().filter(item -> item.tenantId().equals(tenantId)).toList();
        }
        @Override public Optional<OidcGroupMapping> findByIdAndTenantId(UUID id, UUID tenantId) {
            return items.stream().filter(item -> item.id().equals(id) && item.tenantId().equals(tenantId)).findFirst();
        }
        @Override public OidcGroupMapping save(OidcGroupMapping mapping) {
            items.removeIf(item -> item.id().equals(mapping.id()));
            items.add(mapping);
            return mapping;
        }
        @Override public void delete(OidcGroupMapping mapping) { items.removeIf(item -> item.id().equals(mapping.id())); }
    }

    private static final class InMemoryMemberships implements TenantMembershipRepositoryPort {
        private final List<TenantMembership> items = new ArrayList<>();

        @Override public List<TenantMembership> findByTenantId(UUID tenantId) {
            return items.stream().filter(item -> item.tenantId().equals(tenantId)).toList();
        }
        @Override public Optional<TenantMembership> findByIdAndTenantId(UUID id, UUID tenantId) {
            return items.stream().filter(item -> item.id().equals(id) && item.tenantId().equals(tenantId)).findFirst();
        }
        @Override public List<TenantMembership> findInvitedByIssuerAndSubject(String issuer, String subject) {
            return items.stream().filter(item -> item.status() == MembershipStatus.INVITED)
                    .filter(item -> issuer.equals(item.pendingIssuer()) && subject.equals(item.pendingSubject())).toList();
        }
        @Override public List<TenantMembership> findInvitedByIssuerAndEmail(String issuer, String email) {
            return items.stream().filter(item -> item.status() == MembershipStatus.INVITED)
                    .filter(item -> issuer.equals(item.pendingIssuer()) && email.equalsIgnoreCase(item.pendingEmail())).toList();
        }
        @Override public TenantMembership save(TenantMembership membership) {
            items.removeIf(item -> item.id().equals(membership.id()));
            items.add(membership);
            return membership;
        }
    }
}
