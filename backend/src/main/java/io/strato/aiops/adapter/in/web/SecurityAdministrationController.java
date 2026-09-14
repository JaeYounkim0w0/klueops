package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import io.strato.aiops.domain.identity.AccessScope;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.identity.ExternalIdentity;
import io.strato.aiops.domain.identity.PlatformRole;
import io.strato.aiops.domain.identity.PrincipalType;
import io.strato.aiops.domain.identity.RoleBinding;
import io.strato.aiops.domain.identity.ScopeType;
import io.strato.aiops.domain.identity.UserAccount;
import io.strato.aiops.adapter.in.web.security.OidcIdentityMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/security")
@Tag(name = "Identity And Access", description = "User lifecycle and scoped role binding administration")
public class SecurityAdministrationController {

    private final IdentityAccessService identityAccessService;
    private final Clock clock;
    private final OidcIdentityMapper identityMapper;

    public SecurityAdministrationController(IdentityAccessService identityAccessService, Clock clock,
                                            OidcIdentityMapper identityMapper) {
        this.identityAccessService = identityAccessService;
        this.clock = clock;
        this.identityMapper = identityMapper;
    }

    @Operation(summary = "List platform user accounts")
    @GetMapping("/users")
    public List<UserAdminResponse> listUsers(Authentication authentication) {
        requireIdentityManage(authentication);
        return identityAccessService.listUsers().stream().map(UserAdminResponse::from).toList();
    }

    @Operation(summary = "Enable or disable a platform user account")
    @PatchMapping("/users/{userId}")
    public UserAdminResponse updateUser(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request,
                                        Authentication authentication) {
        ResolvedAccess actor = requireIdentityManage(authentication);
        return UserAdminResponse.from(identityAccessService.setActive(userId, request.active(), actor.user().id()));
    }

    @Operation(summary = "List role bindings")
    @GetMapping("/role-bindings")
    public List<RoleBindingResponse> listBindings(Authentication authentication) {
        requireIdentityManage(authentication);
        return identityAccessService.listBindings().stream().map(RoleBindingResponse::from).toList();
    }

    @Operation(summary = "Create a scoped role binding")
    @PostMapping("/role-bindings")
    @ResponseStatus(CREATED)
    public RoleBindingResponse createBinding(@Valid @RequestBody CreateRoleBindingRequest request,
                                             Authentication authentication) {
        ResolvedAccess actor = requireIdentityManage(authentication);
        RoleBinding binding = RoleBinding.create(request.principalType(), request.principalKey(), request.role(),
                request.toScope(), actor.user().id().toString(), clock.instant());
        return RoleBindingResponse.from(identityAccessService.saveBinding(binding));
    }

    @Operation(summary = "Delete a role binding")
    @DeleteMapping("/role-bindings/{bindingId}")
    @ResponseStatus(NO_CONTENT)
    public void deleteBinding(@PathVariable UUID bindingId, Authentication authentication) {
        requireIdentityManage(authentication);
        identityAccessService.deleteBinding(bindingId);
    }

    private ResolvedAccess requireIdentityManage(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new AccessDeniedException("Identity administration requires an OIDC platform administrator");
        }
        ExternalIdentity identity = identityMapper.map(oidcUser);
        UserAccount user = identityAccessService.provision(identity);
        ResolvedAccess access = identityAccessService.resolveAccess(user, identity.groups());
        if (!identityAccessService.hasCapability(access.capabilities(), Capability.IDENTITY_MANAGE)) {
            throw new AccessDeniedException("identity:manage capability is required");
        }
        return access;
    }

    public record UpdateUserRequest(boolean active) {
    }

    public record CreateRoleBindingRequest(
            @NotNull PrincipalType principalType,
            @NotBlank String principalKey,
            @NotNull PlatformRole role,
            @NotNull ScopeType scopeType,
            UUID tenantId,
            UUID workspaceId,
            UUID clusterId,
            String namespace
    ) {
        AccessScope toScope() {
            return new AccessScope(scopeType, tenantId, workspaceId, clusterId, namespace);
        }
    }

    public record UserAdminResponse(String id, String username, String displayName, String email, boolean active,
                                    String firstSeenAt, String lastLoginAt) {
        static UserAdminResponse from(UserAccount user) {
            return new UserAdminResponse(user.id().toString(), user.username(), user.displayName(), user.email(),
                    user.active(), user.firstSeenAt().toString(), user.lastLoginAt().toString());
        }
    }

    public record RoleBindingResponse(String id, String principalType, String principalKey, String role,
                                      String scopeType, String tenantId, String workspaceId, String clusterId,
                                      String namespace, String createdBy,
                                      String createdAt) {
        static RoleBindingResponse from(RoleBinding binding) {
            return new RoleBindingResponse(binding.id().toString(), binding.principalType().name(),
                    binding.principalKey(), binding.role().name(), binding.scope().type().name(),
                    binding.scope().tenantId() == null ? null : binding.scope().tenantId().toString(),
                    binding.scope().workspaceId() == null ? null : binding.scope().workspaceId().toString(),
                    binding.scope().clusterId() == null ? null : binding.scope().clusterId().toString(),
                    binding.scope().namespace(), binding.createdBy(), binding.createdAt().toString());
        }
    }
}
