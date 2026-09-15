package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.RoleBindingRepositoryPort;
import io.strato.aiops.application.port.out.UserAccountRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.WorkspaceRepositoryPort;
import io.strato.aiops.application.port.out.OidcGroupMappingRepositoryPort;
import io.strato.aiops.application.port.out.TenantMembershipRepositoryPort;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.identity.AccessPolicy;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.identity.ExternalIdentity;
import io.strato.aiops.domain.identity.AccessScope;
import io.strato.aiops.domain.identity.AccessTarget;
import io.strato.aiops.domain.identity.PlatformRole;
import io.strato.aiops.domain.identity.PrincipalType;
import io.strato.aiops.domain.identity.RoleBinding;
import io.strato.aiops.domain.identity.ScopeType;
import io.strato.aiops.domain.identity.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;

@Service
public class IdentityAccessService {

    private static final Duration LOGIN_ACTIVITY_WRITE_INTERVAL = Duration.ofMinutes(5);

    // Bootstrap 관리자 그룹만 Platform scope로 허용한다. 나머지 OIDC 그룹은 명시적인 Tenant Mapping이 필요하다.
    private static final Map<String, PlatformRole> STANDARD_GROUP_ROLES = Map.of(
            "aiops-platform-admins", PlatformRole.PLATFORM_ADMIN
    );

    private final UserAccountRepositoryPort users;
    private final RoleBindingRepositoryPort bindings;
    private final ClusterRepositoryPort clusters;
    private final WorkspaceRepositoryPort workspaces;
    private final OidcGroupMappingRepositoryPort groupMappings;
    private final TenantMembershipRepositoryPort memberships;
    private final Clock clock;
    private final AccessPolicy policy = new AccessPolicy();

    public IdentityAccessService(UserAccountRepositoryPort users, RoleBindingRepositoryPort bindings,
                                 ClusterRepositoryPort clusters, WorkspaceRepositoryPort workspaces,
                                 OidcGroupMappingRepositoryPort groupMappings,
                                 TenantMembershipRepositoryPort memberships, Clock clock) {
        this.users = users;
        this.bindings = bindings;
        this.clusters = clusters;
        this.workspaces = workspaces;
        this.groupMappings = groupMappings;
        this.memberships = memberships;
        this.clock = clock;
    }

    @Transactional
    public UserAccount provision(ExternalIdentity identity) {
        Instant now = clock.instant();
        UserAccount existing = users.findByIssuerAndSubject(identity.issuer(), identity.subject()).orElse(null);
        if (existing != null) {
            UserAccount refreshed = refreshIfNeeded(existing, identity, now);
            linkPendingMemberships(refreshed, identity, now);
            return refreshed;
        }

        users.lockProvisioning(identity.issuer(), identity.subject());
        UserAccount provisioned = users.findByIssuerAndSubject(identity.issuer(), identity.subject())
                .map(account -> refreshIfNeeded(account, identity, now))
                .orElseGet(() -> users.save(UserAccount.firstLogin(identity, now)));
        linkPendingMemberships(provisioned, identity, now);
        return provisioned;
    }

    private void linkPendingMemberships(UserAccount user, ExternalIdentity identity, Instant now) {
        Set<UUID> linked = new HashSet<>();
        List<io.strato.aiops.domain.identity.TenantMembership> pending = new ArrayList<>(
                memberships.findInvitedByIssuerAndSubject(identity.issuer(), identity.subject()));
        if (identity.emailVerified() && identity.email() != null && !identity.email().isBlank()) {
            pending.addAll(memberships.findInvitedByIssuerAndEmail(identity.issuer(), identity.email()));
        }
        pending.stream().filter(item -> linked.add(item.id())).forEach(item -> {
            memberships.save(item.activate(user.id(), now));
            saveBinding(RoleBinding.create(PrincipalType.USER, user.id().toString(), item.role(), item.scope(),
                    item.createdBy(), now));
        });
    }

    private UserAccount refreshIfNeeded(UserAccount existing, ExternalIdentity identity, Instant now) {
        return existing.needsRefresh(identity, now, LOGIN_ACTIVITY_WRITE_INTERVAL)
                ? users.save(existing.refresh(identity, now))
                : existing;
    }

    @Transactional(readOnly = true)
    public ResolvedAccess resolveAccess(UserAccount user, Set<String> groups) {
        if (!user.active()) {
            throw new AccountDisabledException();
        }
        Set<String> principals = new HashSet<>(groups == null ? Set.of() : groups);
        principals.add(user.id().toString());
        List<RoleBinding> assigned = new ArrayList<>(bindings.findByPrincipals(principals));
        Set<String> oidcGroups = groups == null ? Set.of() : groups;
        groupMappings.findActive(user.issuer(), oidcGroups).forEach(mapping -> assigned.add(new RoleBinding(
                mapping.id(), PrincipalType.GROUP, mapping.groupValue(), mapping.role(), mapping.scope(),
                mapping.createdBy(), mapping.createdAt())));
        oidcGroups.forEach(group -> {
            String normalized = group.startsWith("/") ? group.substring(1) : group;
            PlatformRole role = STANDARD_GROUP_ROLES.get(normalized);
            if (role != null && assigned.stream().noneMatch(binding ->
                    binding.principalType() == PrincipalType.GROUP && binding.principalKey().equals(group)
                            && binding.role() == role)) {
                UUID id = UUID.nameUUIDFromBytes(("oidc-group:" + group + ":" + role).getBytes(StandardCharsets.UTF_8));
                assigned.add(new RoleBinding(id, PrincipalType.GROUP, group, role, AccessScope.platform(),
                        "oidc-group-mapping", user.lastLoginAt()));
            }
        });
        Set<Capability> capabilities = new LinkedHashSet<>();
        assigned.forEach(binding -> capabilities.addAll(policy.capabilities(binding.role())));
        return new ResolvedAccess(user, Set.copyOf(capabilities), List.copyOf(assigned));
    }

    public Set<Capability> effectiveCapabilities(ResolvedAccess access, UUID tenantId, UUID workspaceId) {
        AccessTarget target = new AccessTarget(tenantId, workspaceId, null, null);
        Set<Capability> result = new LinkedHashSet<>();
        access.bindings().stream()
                .filter(binding -> binding.scope().type() == ScopeType.PLATFORM || binding.scope().includes(target))
                .forEach(binding -> result.addAll(policy.capabilities(binding.role())));
        return Set.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<UserAccount> listUsers() {
        return users.findAll();
    }

    @Transactional(readOnly = true)
    public List<RoleBinding> listBindings() {
        return bindings.findAll();
    }

    @Transactional
    public UserAccount setActive(UUID userId, boolean active, UUID actorUserId) {
        if (!active && userId.equals(actorUserId)) {
            throw new IllegalArgumentException("You cannot disable your own active session account");
        }
        UserAccount user = users.findById(userId).orElseThrow();
        return users.save(user.withActive(active, clock.instant()));
    }

    @Transactional
    public RoleBinding saveBinding(RoleBinding binding) {
        if (binding.principalType() == PrincipalType.USER) {
            UUID userId;
            try {
                userId = UUID.fromString(binding.principalKey());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("User role binding principal must be a valid user id");
            }
            if (users.findById(userId).isEmpty()) {
                throw new IllegalArgumentException("Role binding user does not exist");
            }
        }
        RoleBinding duplicate = bindings.findAll().stream()
                .filter(existing -> existing.principalType() == binding.principalType())
                .filter(existing -> existing.principalKey().equals(binding.principalKey()))
                .filter(existing -> existing.role() == binding.role())
                .filter(existing -> existing.scope().equals(binding.scope()))
                .findFirst()
                .orElse(null);
        if (duplicate != null) {
            return duplicate;
        }
        return bindings.save(binding);
    }

    @Transactional
    public void deleteBinding(UUID id) {
        bindings.deleteById(id);
    }

    public boolean hasCapability(Collection<Capability> capabilities, Capability required) {
        return capabilities.contains(required);
    }

    public boolean allows(ResolvedAccess access, Capability required, UUID clusterId, String namespace) {
        if (clusterId == null) {
            return hasPlatformScope(access, required);
        }
        Cluster cluster = clusters.findById(clusterId).orElse(null);
        AccessTarget target = cluster == null
                ? new AccessTarget(null, null, clusterId, namespace)
                : new AccessTarget(cluster.tenantId(), cluster.workspaceId(), cluster.id(), namespace);
        return access.bindings().stream().anyMatch(binding ->
                policy.allows(binding.role(), required)
                        && binding.scope().includes(target));
    }

    public boolean hasAccessAtAnyScope(ResolvedAccess access, Capability required) {
        return access.bindings().stream().anyMatch(binding -> policy.allows(binding.role(), required));
    }

    public boolean hasPlatformScope(ResolvedAccess access, Capability required) {
        return access.bindings().stream().anyMatch(binding ->
                binding.scope().type() == ScopeType.PLATFORM && policy.allows(binding.role(), required));
    }

    public Set<UUID> visibleClusterIds(ResolvedAccess access) {
        if (hasPlatformScope(access, Capability.CLUSTER_READ)) return Set.of();
        Set<UUID> result = new LinkedHashSet<>();
        access.bindings().stream()
                .filter(binding -> policy.allows(binding.role(), Capability.CLUSTER_READ))
                .map(RoleBinding::scope)
                .filter(scope -> scope.type() == ScopeType.CLUSTER || scope.type() == ScopeType.NAMESPACE)
                .map(AccessScope::clusterId)
                .forEach(result::add);
        clusters.findAll().stream()
                .filter(cluster -> allows(access, Capability.CLUSTER_READ, cluster.id(), null))
                .map(Cluster::id)
                .forEach(result::add);
        return Set.copyOf(result);
    }

    public Set<UUID> visibleTenantIds(ResolvedAccess access) {
        if (hasPlatformScope(access, Capability.CLUSTER_READ)) return Set.of();
        Set<UUID> result = new LinkedHashSet<>();
        access.bindings().stream()
                .filter(binding -> policy.allows(binding.role(), Capability.CLUSTER_READ))
                .forEach(binding -> {
                    AccessScope scope = binding.scope();
                    if (scope.tenantId() != null) result.add(scope.tenantId());
                    if (scope.clusterId() != null) clusters.findById(scope.clusterId()).map(Cluster::tenantId).ifPresent(result::add);
                });
        return Set.copyOf(result);
    }

    public Set<UUID> visibleWorkspaceIds(ResolvedAccess access) {
        if (hasPlatformScope(access, Capability.CLUSTER_READ)) return Set.of();
        Set<UUID> result = new LinkedHashSet<>();
        access.bindings().stream()
                .filter(binding -> policy.allows(binding.role(), Capability.CLUSTER_READ))
                .forEach(binding -> {
                    AccessScope scope = binding.scope();
                    if (scope.workspaceId() != null) result.add(scope.workspaceId());
                });
        clusters.findAll().stream()
                .filter(cluster -> allows(access, Capability.CLUSTER_READ, cluster.id(), null))
                .map(Cluster::workspaceId)
                .forEach(result::add);
        return Set.copyOf(result);
    }

    public boolean allowsTenant(ResolvedAccess access, Capability required, UUID tenantId) {
        return access.bindings().stream()
                .filter(binding -> policy.allows(binding.role(), required))
                .anyMatch(binding -> switch (binding.scope().type()) {
                    case PLATFORM -> true;
                    case TENANT, WORKSPACE -> tenantId.equals(binding.scope().tenantId());
                    case CLUSTER, NAMESPACE -> clusters.findById(binding.scope().clusterId())
                            .map(cluster -> tenantId.equals(cluster.tenantId())).orElse(false);
                });
    }

    public boolean allowsWorkspace(ResolvedAccess access, Capability required, UUID tenantId, UUID workspaceId) {
        return access.bindings().stream()
                .filter(binding -> policy.allows(binding.role(), required))
                .anyMatch(binding -> switch (binding.scope().type()) {
                    case PLATFORM -> true;
                    case TENANT -> tenantId.equals(binding.scope().tenantId());
                    case WORKSPACE -> tenantId.equals(binding.scope().tenantId()) && workspaceId.equals(binding.scope().workspaceId());
                    case CLUSTER, NAMESPACE -> clusters.findById(binding.scope().clusterId())
                            .map(cluster -> tenantId.equals(cluster.tenantId()) && workspaceId.equals(cluster.workspaceId())).orElse(false);
                });
    }

    public boolean allowsWorkspace(ResolvedAccess access, Capability required, UUID workspaceId) {
        return workspaces.findById(workspaceId)
                .map(workspace -> allowsWorkspace(access, required, workspace.tenantId(), workspace.id()))
                .orElse(false);
    }
}
