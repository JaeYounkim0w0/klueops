package io.strato.aiops.adapter.in.web.security;

import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import io.strato.aiops.domain.identity.Capability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApiAuthorizationInterceptor implements HandlerInterceptor {
    public static final String RESOLVED_ACCESS_ATTRIBUTE = ApiAuthorizationInterceptor.class.getName() + ".resolvedAccess";
    private static final Pattern CLUSTER_PATH = Pattern.compile("/(?:clusters|analysis/clusters)/([0-9a-fA-F-]{36})(?:/|$)");
    private static final Pattern ANALYSIS_NAMESPACE_PATH = Pattern.compile("/api/analysis/namespaces/([^/]+)(?:/|$)");
    private static final Pattern TENANT_PATH = Pattern.compile("/api/tenants/([0-9a-fA-F-]{36})(?:/|$)");
    private static final Pattern WORKSPACE_PATH = Pattern.compile("/api/workspaces/([0-9a-fA-F-]{36})(?:/|$)");

    private final CurrentAccessResolver currentAccessResolver;
    private final IdentityAccessService identityAccessService;

    public ApiAuthorizationInterceptor(CurrentAccessResolver currentAccessResolver,
                                       IdentityAccessService identityAccessService) {
        this.currentAccessResolver = currentAccessResolver;
        this.identityAccessService = identityAccessService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.startsWith("/api/auth/") || path.startsWith("/api/security/")
                || path.startsWith("/api/me/")) {
            return true;
        }
        Capability required = requiredCapability(path, request.getMethod());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ResolvedAccess access = currentAccessResolver.resolve(authentication);
        request.setAttribute(RESOLVED_ACCESS_ATTRIBUTE, access);
        UUID clusterId = clusterId(request, path);
        UUID tenantId = tenantId(path);
        UUID workspaceId = workspaceId(path);
        String namespace = namespace(request, path);
        boolean allowed = (isClusterCollectionRead(path, request.getMethod()) || path.startsWith("/api/ai-chat")
                || path.startsWith("/api/search") || path.startsWith("/api/incidents") || path.startsWith("/api/runbooks"))
                ? identityAccessService.hasAccessAtAnyScope(access, required)
                : clusterId != null
                    ? identityAccessService.allows(access, required, clusterId, namespace)
                    : tenantId != null
                        ? identityAccessService.allowsTenant(access, required, tenantId)
                        : workspaceId != null
                            ? identityAccessService.allowsWorkspace(access, required, workspaceId)
                        : identityAccessService.allows(access, required, null, namespace);
        if (!allowed) {
            throw new AccessDeniedException(required.value() + " capability is not granted for this scope");
        }
        return true;
    }

    String namespace(HttpServletRequest request, String path) {
        String parameter = request.getParameter("namespace");
        if (parameter != null && !parameter.isBlank()) {
            return parameter;
        }
        Matcher matcher = ANALYSIS_NAMESPACE_PATH.matcher(path);
        return matcher.find() ? java.net.URLDecoder.decode(matcher.group(1), java.nio.charset.StandardCharsets.UTF_8) : null;
    }

    private boolean isClusterCollectionRead(String path, String method) {
        return (method.equals("GET") || method.equals("HEAD"))
                && (path.equals("/api/clusters") || path.equals("/api/clusters/")
                    || path.equals("/api/tenants") || path.equals("/api/tenants/"));
    }

    Capability requiredCapability(String path, String method) {
        boolean read = method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS");
        if (path.startsWith("/api/clusters")) {
            if (isCommandConsolePath(path)) {
                return read && path.contains("/command-capabilities")
                        ? Capability.CLUSTER_READ
                        : Capability.OPERATION_EXECUTE;
            }
            return read ? Capability.CLUSTER_READ : Capability.CLUSTER_MANAGE;
        }
        if (path.startsWith("/api/tenants") || path.startsWith("/api/workspaces")) {
            return read ? Capability.CLUSTER_READ : Capability.CLUSTER_MANAGE;
        }
        if (path.startsWith("/api/analysis")) {
            if (path.contains("/commands") && !read) {
                return Capability.OPERATION_EXECUTE;
            }
            return read ? Capability.ANALYSIS_READ : Capability.ANALYSIS_RUN;
        }
        if (path.startsWith("/api/ai-chat")) {
            return read ? Capability.ANALYSIS_READ : Capability.ANALYSIS_RUN;
        }
        if (path.startsWith("/api/search")) {
            return Capability.CLUSTER_READ;
        }
        if (path.startsWith("/api/incidents") || path.startsWith("/api/runbooks")) {
            return read ? Capability.ANALYSIS_READ : Capability.OPERATION_EXECUTE;
        }
        if (path.startsWith("/api/policies")) {
            return read ? Capability.CLUSTER_READ : Capability.POLICY_MANAGE;
        }
        if (path.startsWith("/api/audit")) {
            return Capability.AUDIT_READ;
        }
        if (path.startsWith("/api/applications") || path.startsWith("/api/operations") || path.startsWith("/api/jobs")) {
            return read ? Capability.CLUSTER_READ : Capability.OPERATION_EXECUTE;
        }
        return Capability.PLATFORM_ADMIN;
    }

    private boolean isCommandConsolePath(String path) {
        return path.contains("/command-capabilities")
                || path.contains("/commands/validate")
                || path.contains("/command-executions")
                || path.contains("/command-sessions")
                || path.contains("/command-favorites");
    }

    private UUID clusterId(HttpServletRequest request, String path) {
        String parameter = request.getParameter("clusterId");
        if (parameter != null && !parameter.isBlank()) {
            return parseUuid(parameter);
        }
        Matcher matcher = CLUSTER_PATH.matcher(path);
        return matcher.find() ? parseUuid(matcher.group(1)) : null;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid cluster scope");
        }
    }

    private UUID tenantId(String path) {
        Matcher matcher = TENANT_PATH.matcher(path);
        return matcher.find() ? parseUuid(matcher.group(1)) : null;
    }

    UUID workspaceId(String path) {
        Matcher matcher = WORKSPACE_PATH.matcher(path);
        return matcher.find() ? parseUuid(matcher.group(1)) : null;
    }
}
