package io.strato.aiops.adapter.in.web;

import io.strato.aiops.adapter.in.web.security.CurrentAccessResolver;
import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import io.strato.aiops.application.service.TenantAccessAdministrationService;
import io.strato.aiops.domain.identity.AccessScope;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.identity.FeatureKey;
import io.strato.aiops.domain.identity.MembershipStatus;
import io.strato.aiops.domain.identity.OidcGroupMapping;
import io.strato.aiops.domain.identity.PlatformRole;
import io.strato.aiops.domain.identity.ScopeType;
import io.strato.aiops.domain.identity.TenantMembership;
import io.strato.aiops.domain.identity.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@Tag(name = "Tenant Access", description = "Tenant-scoped access, membership and OIDC group administration")
public class TenantAccessController {
    private final CurrentAccessResolver currentAccessResolver;
    private final IdentityAccessService identityAccessService;
    private final TenantAccessAdministrationService tenantAccessService;

    public TenantAccessController(CurrentAccessResolver currentAccessResolver,
                                  IdentityAccessService identityAccessService,
                                  TenantAccessAdministrationService tenantAccessService) {
        this.currentAccessResolver = currentAccessResolver;
        this.identityAccessService = identityAccessService;
        this.tenantAccessService = tenantAccessService;
    }

    @Operation(summary = "Resolve capabilities and navigation for the selected tenant scope")
    @GetMapping("/api/me/access")
    public AccessResponse access(@RequestParam UUID tenantId,
                                 @RequestParam(required = false) UUID workspaceId,
                                 Authentication authentication) {
        ResolvedAccess access = requireTenant(authentication, tenantId, Capability.TENANT_READ);
        if (workspaceId != null && !identityAccessService.allowsWorkspace(access, Capability.TENANT_READ,
                tenantId, workspaceId)) {
            throw new AccessDeniedException("tenant:read capability is not granted for the workspace");
        }
        return AccessResponse.from(tenantAccessService.access(access, tenantId, workspaceId), tenantId, workspaceId,
                identityAccessService.hasPlatformScope(access, Capability.PLATFORM_ADMIN));
    }

    @Operation(summary = "List tenant memberships")
    @GetMapping("/api/tenants/{tenantId}/members")
    public List<MemberResponse> members(@PathVariable UUID tenantId, Authentication authentication) {
        requireTenant(authentication, tenantId, Capability.TENANT_MEMBER_MANAGE);
        return tenantAccessService.members(tenantId).stream().map(MemberResponse::from).toList();
    }

    @Operation(summary = "Invite or pre-authorize a tenant member")
    @PostMapping("/api/tenants/{tenantId}/members")
    @ResponseStatus(CREATED)
    public MemberResponse invite(@PathVariable UUID tenantId, @Valid @RequestBody InviteMemberRequest request,
                                 Authentication authentication) {
        ResolvedAccess actor = requireTenant(authentication, tenantId, Capability.TENANT_MEMBER_MANAGE);
        AccessScope scope = request.scope(tenantId);
        return MemberResponse.from(tenantAccessService.invite(tenantId, request.issuer(), request.subject(),
                request.email(), request.role(), scope, actor.user().id().toString()));
    }

    @Operation(summary = "Suspend or reactivate a tenant membership")
    @PatchMapping("/api/tenants/{tenantId}/members/{membershipId}")
    public MemberResponse updateMember(@PathVariable UUID tenantId, @PathVariable UUID membershipId,
                                       @Valid @RequestBody UpdateMembershipRequest request,
                                       Authentication authentication) {
        requireTenant(authentication, tenantId, Capability.TENANT_MEMBER_MANAGE);
        if (request.status() == MembershipStatus.OFFBOARDED || request.status() == MembershipStatus.INVITED) {
            throw new IllegalArgumentException("Use the offboard workflow or create a new invitation");
        }
        return MemberResponse.from(tenantAccessService.changeStatus(tenantId, membershipId, request.status()));
    }

    @Operation(summary = "Preview tenant member offboarding")
    @PostMapping("/api/tenants/{tenantId}/members/{membershipId}/offboard-plan")
    public TenantAccessAdministrationService.OffboardPlan offboardPlan(@PathVariable UUID tenantId,
                                                                        @PathVariable UUID membershipId,
                                                                        Authentication authentication) {
        requireTenant(authentication, tenantId, Capability.TENANT_MEMBER_MANAGE);
        return tenantAccessService.offboardPlan(tenantId, membershipId);
    }

    @Operation(summary = "Execute an approved tenant member offboarding plan")
    @PostMapping("/api/tenants/{tenantId}/members/{membershipId}/offboard")
    public MemberResponse offboard(@PathVariable UUID tenantId, @PathVariable UUID membershipId,
                                   @Valid @RequestBody OffboardRequest request, Authentication authentication) {
        requireTenant(authentication, tenantId, Capability.TENANT_MEMBER_MANAGE);
        var plan = tenantAccessService.offboardPlan(tenantId, membershipId);
        if (!plan.confirmationText().equals(request.confirmationText())) {
            throw new IllegalArgumentException("Offboard confirmation text does not match the current plan");
        }
        return MemberResponse.from(tenantAccessService.changeStatus(tenantId, membershipId,
                MembershipStatus.OFFBOARDED));
    }

    @GetMapping("/api/tenants/{tenantId}/features")
    public Map<FeatureKey, Boolean> features(@PathVariable UUID tenantId, Authentication authentication) {
        requireTenant(authentication, tenantId, Capability.TENANT_READ);
        return tenantAccessService.featurePolicy(tenantId);
    }

    @PatchMapping("/api/tenants/{tenantId}/features")
    public Map<FeatureKey, Boolean> updateFeature(@PathVariable UUID tenantId,
                                                  @Valid @RequestBody UpdateFeatureRequest request,
                                                  Authentication authentication) {
        ResolvedAccess actor = requireTenant(authentication, tenantId, Capability.TENANT_MANAGE);
        return tenantAccessService.setFeature(tenantId, request.featureKey(), request.enabled(),
                actor.user().id().toString());
    }

    @GetMapping("/api/tenants/{tenantId}/oidc-group-mappings")
    public List<GroupMappingResponse> mappings(@PathVariable UUID tenantId, Authentication authentication) {
        requireTenant(authentication, tenantId, Capability.TENANT_MEMBER_MANAGE);
        return tenantAccessService.mappings(tenantId).stream().map(GroupMappingResponse::from).toList();
    }

    @PostMapping("/api/tenants/{tenantId}/oidc-group-mappings")
    @ResponseStatus(CREATED)
    public GroupMappingResponse createMapping(@PathVariable UUID tenantId,
                                               @Valid @RequestBody CreateGroupMappingRequest request,
                                               Authentication authentication) {
        ResolvedAccess actor = requireTenant(authentication, tenantId, Capability.TENANT_MEMBER_MANAGE);
        return GroupMappingResponse.from(tenantAccessService.createMapping(request.issuer(), request.groupValue(),
                tenantId, request.role(), request.scope(tenantId), actor.user().id().toString()));
    }

    @PatchMapping("/api/tenants/{tenantId}/oidc-group-mappings/{mappingId}")
    public GroupMappingResponse updateMapping(@PathVariable UUID tenantId, @PathVariable UUID mappingId,
                                               @Valid @RequestBody UpdateGroupMappingRequest request,
                                               Authentication authentication) {
        ResolvedAccess actor = requireTenant(authentication, tenantId, Capability.TENANT_MEMBER_MANAGE);
        return GroupMappingResponse.from(tenantAccessService.setMappingActive(tenantId, mappingId, request.active(),
                actor.user().id().toString()));
    }

    @DeleteMapping("/api/tenants/{tenantId}/oidc-group-mappings/{mappingId}")
    @ResponseStatus(NO_CONTENT)
    public void deleteMapping(@PathVariable UUID tenantId, @PathVariable UUID mappingId,
                              Authentication authentication) {
        requireTenant(authentication, tenantId, Capability.TENANT_MEMBER_MANAGE);
        tenantAccessService.deleteMapping(tenantId, mappingId);
    }

    private ResolvedAccess requireTenant(Authentication authentication, UUID tenantId, Capability capability) {
        ResolvedAccess access = currentAccessResolver.resolve(authentication);
        if (!identityAccessService.allowsTenant(access, capability, tenantId)) {
            throw new AccessDeniedException(capability.value() + " capability is not granted for this tenant");
        }
        return access;
    }

    public record InviteMemberRequest(@NotBlank String issuer, String subject, String email,
                                      @NotNull PlatformRole role, @NotNull ScopeType scopeType,
                                      UUID workspaceId, UUID clusterId, String namespace) {
        AccessScope scope(UUID tenantId) {
            return scoped(tenantId, scopeType, workspaceId, clusterId, namespace);
        }
    }

    public record UpdateMembershipRequest(@NotNull MembershipStatus status) {
    }

    public record OffboardRequest(@NotBlank String confirmationText) {
    }

    public record UpdateFeatureRequest(@NotNull FeatureKey featureKey, boolean enabled) {
    }

    public record CreateGroupMappingRequest(@NotBlank String issuer, @NotBlank String groupValue,
                                            @NotNull PlatformRole role, @NotNull ScopeType scopeType,
                                            UUID workspaceId, UUID clusterId, String namespace) {
        AccessScope scope(UUID tenantId) {
            return scoped(tenantId, scopeType, workspaceId, clusterId, namespace);
        }
    }

    public record UpdateGroupMappingRequest(boolean active) {
    }

    public record MemberResponse(String id, String tenantId, String userId, String username, String displayName,
                                 String email, String role, String scopeType, String workspaceId, String clusterId,
                                 String namespace, String status, String createdAt, String updatedAt) {
        static MemberResponse from(TenantAccessAdministrationService.MemberView view) {
            TenantMembership member = view.membership();
            UserAccount user = view.user();
            return new MemberResponse(member.id().toString(), member.tenantId().toString(), value(member.userId()),
                    user == null ? null : user.username(), user == null ? null : user.displayName(),
                    user == null ? member.pendingEmail() : user.email(), member.role().name(),
                    member.scope().type().name(), value(member.scope().workspaceId()),
                    value(member.scope().clusterId()), member.scope().namespace(), member.status().name(),
                    member.createdAt().toString(), member.updatedAt().toString());
        }
    }

    public record GroupMappingResponse(String id, String issuer, String groupValue, String tenantId, String role,
                                       String scopeType, String workspaceId, String clusterId, String namespace,
                                       boolean active, String updatedAt) {
        static GroupMappingResponse from(OidcGroupMapping mapping) {
            return new GroupMappingResponse(mapping.id().toString(), mapping.issuer(), mapping.groupValue(),
                    mapping.tenantId().toString(), mapping.role().name(), mapping.scope().type().name(),
                    value(mapping.scope().workspaceId()), value(mapping.scope().clusterId()),
                    mapping.scope().namespace(), mapping.active(), mapping.updatedAt().toString());
        }
    }

    public record AccessResponse(String platformRole, String tenantId, String workspaceId,
                                 List<String> effectiveCapabilities, List<String> enabledFeatures,
                                 Map<String, Boolean> navigation) {
        static AccessResponse from(TenantAccessAdministrationService.AccessSnapshot snapshot, UUID tenantId,
                                   UUID workspaceId, boolean platformManager) {
            return new AccessResponse(platformManager ? "PLATFORM_MANAGER" : null, tenantId.toString(),
                    value(workspaceId), snapshot.capabilities().stream().map(Capability::value).sorted().toList(),
                    snapshot.enabledFeatures().stream().map(Enum::name).sorted().toList(), snapshot.navigation());
        }
    }

    private static AccessScope scoped(UUID tenantId, ScopeType type, UUID workspaceId, UUID clusterId,
                                      String namespace) {
        return switch (type) {
            case TENANT -> AccessScope.tenant(tenantId);
            case WORKSPACE -> AccessScope.workspace(tenantId, workspaceId);
            case CLUSTER -> AccessScope.cluster(clusterId);
            case NAMESPACE -> AccessScope.namespace(clusterId, namespace);
            case PLATFORM -> throw new IllegalArgumentException("Tenant operation cannot grant platform scope");
        };
    }

    private static String value(UUID value) {
        return value == null ? null : value.toString();
    }
}
