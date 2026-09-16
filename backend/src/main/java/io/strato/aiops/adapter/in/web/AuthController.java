package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.identity.ExternalIdentity;
import io.strato.aiops.domain.identity.RoleBinding;
import io.strato.aiops.domain.identity.UserAccount;
import io.strato.aiops.adapter.in.web.security.OidcIdentityMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Browser session and current access context")
public class AuthController {

    private final IdentityAccessService identityAccessService;
    private final Environment environment;
    private final OidcIdentityMapper identityMapper;
    private final Duration absoluteSessionTimeout;

    /** AuthController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AuthController(IdentityAccessService identityAccessService, Environment environment,
                          OidcIdentityMapper identityMapper,
                          @Value("${aiops.security.session.absolute-timeout:8h}") Duration absoluteSessionTimeout) {
        this.identityAccessService = identityAccessService;
        this.environment = environment;
        this.identityMapper = identityMapper;
        this.absoluteSessionTimeout = absoluteSessionTimeout;
    }

    /** AuthController의 me 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get current browser session and platform capabilities")
    @GetMapping("/me")
    public AuthSessionResponse me(HttpServletRequest request, CsrfToken csrfToken) {
        csrfToken.getToken();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            ExternalIdentity externalIdentity = identityMapper.map(oidcUser);
            UserAccount user = identityAccessService.provision(externalIdentity);
            ResolvedAccess access = identityAccessService.resolveAccess(user, externalIdentity.groups());
            return AuthSessionResponse.authenticatedSession(user, access, sessionStatus(request));
        }
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            return AuthSessionResponse.localSession();
        }
        return AuthSessionResponse.anonymous();
    }

    /** AuthController의 extend 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Extend the active browser session after verified operator activity")
    @PostMapping("/session/extend")
    public BrowserSessionResponse extend(HttpServletRequest request) {
        return sessionStatus(request);
    }

    /** AuthController의 sessionStatus 처리에 필요한 업무 로직을 수행한다. */
    private BrowserSessionResponse sessionStatus(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Instant now = Instant.now();
        Instant absoluteExpiresAt = Instant.ofEpochMilli(session.getCreationTime()).plus(absoluteSessionTimeout);
        int idleTimeoutSeconds = session.getMaxInactiveInterval();
        Instant idleExpiresAt = now.plusSeconds(Math.max(0, idleTimeoutSeconds));
        // 절대 만료 한도를 넘는 연장 시각을 반환하지 않아 UI가 실제보다 길게 표시하지 않도록 한다.
        Instant expiresAt = idleExpiresAt.isBefore(absoluteExpiresAt) ? idleExpiresAt : absoluteExpiresAt;
        return new BrowserSessionResponse(expiresAt, absoluteExpiresAt, idleTimeoutSeconds,
                idleTimeoutSeconds > 0 && idleExpiresAt.isBefore(absoluteExpiresAt));
    }

    public record AuthSessionResponse(
            boolean authenticated,
            boolean localDevelopment,
            UserResponse user,
            List<String> capabilities,
            List<ScopeResponse> scopes,
            String loginUrl,
            String logoutUrl,
            BrowserSessionResponse session
    ) {
        /** AuthSessionResponse의 anonymous 처리에 필요한 업무 로직을 수행한다. */
        static AuthSessionResponse anonymous() {
            return new AuthSessionResponse(false, false, null, List.of(), List.of(),
                    "/oauth2/authorization/aiops", "/logout", null);
        }

        /** AuthSessionResponse의 localSession 처리에 필요한 업무 로직을 수행한다. */
        static AuthSessionResponse localSession() {
            List<String> capabilities = java.util.Arrays.stream(Capability.values()).map(Capability::value).sorted().toList();
            return new AuthSessionResponse(true, true,
                    new UserResponse("local-development", "local", "Local Operator", null, true),
                    capabilities, List.of(new ScopeResponse("PLATFORM", null, null, null, null, "PLATFORM_ADMIN")),
                    "/oauth2/authorization/aiops", "/logout", null);
        }

        /** AuthSessionResponse의 authenticatedSession 처리에 필요한 업무 로직을 수행한다. */
        static AuthSessionResponse authenticatedSession(UserAccount user, ResolvedAccess access,
                                                         BrowserSessionResponse session) {
            return new AuthSessionResponse(true, false, UserResponse.from(user),
                    access.capabilities().stream().map(Capability::value).sorted().toList(),
                    access.bindings().stream().map(ScopeResponse::from).toList(),
                    "/oauth2/authorization/aiops", "/logout", session);
        }
    }

    public record BrowserSessionResponse(
            Instant expiresAt,
            Instant absoluteExpiresAt,
            int idleTimeoutSeconds,
            boolean canExtend
    ) {
    }

    public record UserResponse(String id, String username, String displayName, String email, boolean active) {
        /** UserResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static UserResponse from(UserAccount user) {
            return new UserResponse(user.id().toString(), user.username(), user.displayName(), user.email(), user.active());
        }
    }

    public record ScopeResponse(String type, String tenantId, String workspaceId, String clusterId, String namespace, String role) {
        /** ScopeResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static ScopeResponse from(RoleBinding binding) {
            return new ScopeResponse(binding.scope().type().name(),
                    binding.scope().tenantId() == null ? null : binding.scope().tenantId().toString(),
                    binding.scope().workspaceId() == null ? null : binding.scope().workspaceId().toString(),
                    binding.scope().clusterId() == null ? null : binding.scope().clusterId().toString(),
                    binding.scope().namespace(), binding.role().name());
        }
    }
}
