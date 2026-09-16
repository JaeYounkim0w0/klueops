package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.OidcGroupMappingRepositoryPort;
import io.strato.aiops.application.port.out.RoleBindingRepositoryPort;
import io.strato.aiops.application.port.out.TenantFeaturePolicyRepositoryPort;
import io.strato.aiops.application.port.out.TenantMembershipRepositoryPort;
import io.strato.aiops.application.port.out.UserAccountRepositoryPort;
import io.strato.aiops.application.port.out.WorkspaceRepositoryPort;
import io.strato.aiops.domain.identity.AccessScope;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.identity.FeatureKey;
import io.strato.aiops.domain.identity.MembershipStatus;
import io.strato.aiops.domain.identity.OidcGroupMapping;
import io.strato.aiops.domain.identity.PlatformRole;
import io.strato.aiops.domain.identity.PrincipalType;
import io.strato.aiops.domain.identity.RoleBinding;
import io.strato.aiops.domain.identity.ScopeType;
import io.strato.aiops.domain.identity.TenantFeaturePolicy;
import io.strato.aiops.domain.identity.TenantMembership;
import io.strato.aiops.domain.identity.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class TenantAccessAdministrationService {
    private final TenantMembershipRepositoryPort memberships;
    private final TenantFeaturePolicyRepositoryPort features;
    private final OidcGroupMappingRepositoryPort groupMappings;
    private final RoleBindingRepositoryPort bindings;
    private final UserAccountRepositoryPort users;
    private final WorkspaceRepositoryPort workspaces;
    private final ClusterRepositoryPort clusters;
    private final IdentityAccessService identityAccess;
    private final Clock clock;

    /** TenantAccessAdministrationService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public TenantAccessAdministrationService(TenantMembershipRepositoryPort memberships,
                                             TenantFeaturePolicyRepositoryPort features,
                                             OidcGroupMappingRepositoryPort groupMappings,
                                             RoleBindingRepositoryPort bindings,
                                             UserAccountRepositoryPort users,
                                             WorkspaceRepositoryPort workspaces,
                                             ClusterRepositoryPort clusters,
                                             IdentityAccessService identityAccess,
                                             Clock clock) {
        this.memberships = memberships;
        this.features = features;
        this.groupMappings = groupMappings;
        this.bindings = bindings;
        this.users = users;
        this.workspaces = workspaces;
        this.clusters = clusters;
        this.identityAccess = identityAccess;
        this.clock = clock;
    }

    /** TenantAccessAdministrationService의 members 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public List<MemberView> members(UUID tenantId) {
        return memberships.findByTenantId(tenantId).stream().map(this::toView).toList();
    }

    /** TenantAccessAdministrationService의 invite 처리에 필요한 업무 로직을 수행한다. */
    @Transactional
    public MemberView invite(UUID tenantId, String issuer, String subject, String email, PlatformRole role,
                             AccessScope scope, String actor) {
        validateMappingTarget(tenantId, role, scope);
        TenantMembership membership = TenantMembership.invite(tenantId, issuer, subject, email, role, scope,
                actor, clock.instant());
        return toView(memberships.save(membership));
    }

    /** TenantAccessAdministrationService의 changeStatus 처리 대상의 상태를 갱신한다. */
    @Transactional
    public MemberView changeStatus(UUID tenantId, UUID membershipId, MembershipStatus status) {
        TenantMembership membership = membership(tenantId, membershipId);
        Instant now = clock.instant();
        TenantMembership changed = switch (status) {
            case ACTIVE -> membership.reactivate(now);
            case SUSPENDED -> membership.suspend(now);
            case OFFBOARDED -> offboardMembership(membership, now);
            case INVITED -> throw new IllegalArgumentException("An existing membership cannot return to invited");
        };
        return toView(memberships.save(changed));
    }

    /** TenantAccessAdministrationService의 offboardPlan 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public OffboardPlan offboardPlan(UUID tenantId, UUID membershipId) {
        TenantMembership membership = membership(tenantId, membershipId);
        List<RoleBinding> affected = membership.userId() == null ? List.of() : tenantBindings(tenantId, membership.userId());
        UserAccount user = membership.userId() == null ? null : users.findById(membership.userId()).orElse(null);
        return new OffboardPlan(membership.id(), user == null ? membership.pendingEmail() : user.username(),
                membership.status(), affected.size(), true, "OFFBOARD " + displayIdentity(membership, user));
    }

    /** TenantAccessAdministrationService의 featurePolicy 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public Map<FeatureKey, Boolean> featurePolicy(UUID tenantId) {
        Map<FeatureKey, Boolean> result = new EnumMap<>(FeatureKey.class);
        Arrays.stream(FeatureKey.values()).forEach(key -> result.put(key, true));
        features.findByTenantId(tenantId).forEach(policy -> result.put(policy.featureKey(), policy.enabled()));
        return Map.copyOf(result);
    }

    /** TenantAccessAdministrationService의 setFeature 처리 대상의 상태를 갱신한다. */
    @Transactional
    public Map<FeatureKey, Boolean> setFeature(UUID tenantId, FeatureKey key, boolean enabled, String actor) {
        TenantFeaturePolicy existing = features.findByTenantIdAndFeatureKey(tenantId, key).orElse(null);
        TenantFeaturePolicy policy = existing == null
                ? TenantFeaturePolicy.set(tenantId, key, enabled, actor, clock.instant())
                : new TenantFeaturePolicy(existing.id(), tenantId, key, enabled, actor, clock.instant());
        features.save(policy);
        return featurePolicy(tenantId);
    }

    /** TenantAccessAdministrationService의 mappings 처리 데이터를 필요한 표현으로 변환한다. */
    @Transactional(readOnly = true)
    public List<OidcGroupMapping> mappings(UUID tenantId) {
        return groupMappings.findByTenantId(tenantId);
    }

    /** TenantAccessAdministrationService의 createMapping 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Transactional
    public OidcGroupMapping createMapping(String issuer, String groupValue, UUID tenantId, PlatformRole role,
                                          AccessScope scope, String actor) {
        validateMappingTarget(tenantId, role, scope);
        return groupMappings.save(OidcGroupMapping.create(issuer, groupValue, tenantId, role, scope,
                actor, clock.instant()));
    }

    /** TenantAccessAdministrationService의 setMappingActive 처리 대상의 상태를 갱신한다. */
    @Transactional
    public OidcGroupMapping setMappingActive(UUID tenantId, UUID mappingId, boolean active, String actor) {
        OidcGroupMapping mapping = groupMappings.findByIdAndTenantId(mappingId, tenantId).orElseThrow();
        return groupMappings.save(mapping.withActive(active, actor, clock.instant()));
    }

    /** TenantAccessAdministrationService의 deleteMapping 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Transactional
    public void deleteMapping(UUID tenantId, UUID mappingId) {
        groupMappings.delete(groupMappings.findByIdAndTenantId(mappingId, tenantId).orElseThrow());
    }

    /** TenantAccessAdministrationService의 access 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public AccessSnapshot access(ResolvedAccess access, UUID tenantId, UUID workspaceId) {
        Set<Capability> capabilities = identityAccess.effectiveCapabilities(access, tenantId, workspaceId);
        Map<FeatureKey, Boolean> enabled = featurePolicy(tenantId);
        Map<String, Boolean> navigation = new LinkedHashMap<>();
        navigation.put("overview", visible(enabled, FeatureKey.CORE_OVERVIEW, capabilities, Capability.CLUSTER_READ));
        navigation.put("clusters", visible(enabled, FeatureKey.CLUSTER_OPERATIONS, capabilities, Capability.CLUSTER_READ));
        navigation.put("console", visible(enabled, FeatureKey.KUBERNETES_CONSOLE, capabilities, Capability.CLUSTER_READ));
        navigation.put("ai", visible(enabled, FeatureKey.AI_OPERATIONS, capabilities, Capability.ANALYSIS_READ));
        navigation.put("applications", visible(enabled, FeatureKey.APPLICATION_DELIVERY, capabilities, Capability.APPLICATION_READ));
        navigation.put("access", visible(enabled, FeatureKey.ACCESS_CONTROL, capabilities, Capability.TENANT_MEMBER_MANAGE));
        navigation.put("aiProviders", visible(enabled, FeatureKey.AI_PROVIDER_ROUTING, capabilities, Capability.AI_ROUTING_MANAGE));
        return new AccessSnapshot(capabilities, enabled.entrySet().stream().filter(Map.Entry::getValue)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet()), Map.copyOf(navigation));
    }

    /** TenantAccessAdministrationService의 visible 처리에 필요한 업무 로직을 수행한다. */
    private boolean visible(Map<FeatureKey, Boolean> features, FeatureKey key, Set<Capability> capabilities,
                            Capability required) {
        return features.getOrDefault(key, true) && capabilities.contains(required);
    }

    /** TenantAccessAdministrationService의 offboardMembership 처리에 필요한 업무 로직을 수행한다. */
    private TenantMembership offboardMembership(TenantMembership membership, Instant now) {
        if (membership.userId() != null) {
            tenantBindings(membership.tenantId(), membership.userId()).forEach(binding -> bindings.deleteById(binding.id()));
        }
        return membership.offboard(now);
    }

    /** TenantAccessAdministrationService의 tenantBindings 처리에 필요한 업무 로직을 수행한다. */
    private List<RoleBinding> tenantBindings(UUID tenantId, UUID userId) {
        return bindings.findByPrincipal(userId.toString()).stream()
                .filter(binding -> binding.principalType() == PrincipalType.USER)
                .filter(binding -> binding.principalKey().equals(userId.toString()))
                .filter(binding -> belongsToTenant(binding, tenantId))
                .toList();
    }

    /** TenantAccessAdministrationService의 belongsToTenant 처리에 필요한 업무 로직을 수행한다. */
    private boolean belongsToTenant(RoleBinding binding, UUID tenantId) {
        return switch (binding.scope().type()) {
            case PLATFORM -> false;
            case TENANT, WORKSPACE -> tenantId.equals(binding.scope().tenantId());
            case CLUSTER, NAMESPACE -> clusters.findById(binding.scope().clusterId())
                    .map(cluster -> tenantId.equals(cluster.tenantId())).orElse(false);
        };
    }

    /** TenantAccessAdministrationService의 validateMappingTarget 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void validateMappingTarget(UUID tenantId, PlatformRole role, AccessScope scope) {
        if (role == PlatformRole.PLATFORM_ADMIN || scope.type() == ScopeType.PLATFORM) {
            throw new IllegalArgumentException("Tenant mapping cannot grant Platform Manager");
        }
        if (scope.type() == ScopeType.TENANT && !tenantId.equals(scope.tenantId())) {
            throw new IllegalArgumentException("Mapping scope must belong to the tenant");
        }
        if (scope.type() == ScopeType.WORKSPACE) {
            boolean valid = workspaces.findById(scope.workspaceId())
                    .map(workspace -> tenantId.equals(workspace.tenantId())).orElse(false);
            if (!valid) throw new IllegalArgumentException("Workspace does not belong to the tenant");
        }
        if (scope.type() == ScopeType.CLUSTER || scope.type() == ScopeType.NAMESPACE) {
            boolean valid = clusters.findById(scope.clusterId())
                    .map(cluster -> tenantId.equals(cluster.tenantId())).orElse(false);
            if (!valid) throw new IllegalArgumentException("Cluster does not belong to the tenant");
        }
    }

    /** TenantAccessAdministrationService의 membership 처리에 필요한 업무 로직을 수행한다. */
    private TenantMembership membership(UUID tenantId, UUID membershipId) {
        return memberships.findByIdAndTenantId(membershipId, tenantId).orElseThrow();
    }

    /** TenantAccessAdministrationService의 toView 처리 데이터를 필요한 표현으로 변환한다. */
    private MemberView toView(TenantMembership membership) {
        UserAccount user = membership.userId() == null ? null : users.findById(membership.userId()).orElse(null);
        return new MemberView(membership, user);
    }

    /** TenantAccessAdministrationService의 displayIdentity 처리에 필요한 업무 로직을 수행한다. */
    private String displayIdentity(TenantMembership membership, UserAccount user) {
        if (user != null) return user.username();
        return Optional.ofNullable(membership.pendingEmail()).orElse(membership.pendingSubject());
    }

    public record MemberView(TenantMembership membership, UserAccount user) {
    }

    public record OffboardPlan(UUID membershipId, String username, MembershipStatus status,
                               int roleBindingsToRemove, boolean sessionsRevoked, String confirmationText) {
    }

    public record AccessSnapshot(Set<Capability> capabilities, Set<FeatureKey> enabledFeatures,
                                 Map<String, Boolean> navigation) {
    }
}
