package io.strato.aiops.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ProductionReadinessService {

    private static final String DEFAULT_LOCAL_MASTER_KEY = "local-development-master-key";

    private final Environment environment;
    private final String datasourceUrl;
    private final String masterKey;
    private final boolean credentialRevealEnabled;
    private final boolean validationLabLiveEnabled;
    private final boolean validationLabProductionAllowed;
    private final String oidcIssuerUri;
    private final String oidcClientId;
    private final boolean sessionCookieSecure;
    private final boolean explicitOidcEndpointsEnabled;
    private final String oidcAuthorizationUri;
    private final String oidcTokenUri;
    private final String oidcJwkSetUri;
    private final String oidcUserInfoUri;
    private final String publicBaseUrl;
    private final String aiBaseUrl;
    private final String commandRunnerMode;

    public ProductionReadinessService(
            Environment environment,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${aiops.crypto.local-master-key:}") String masterKey,
            @Value("${aiops.security.credential-reveal-enabled:false}") boolean credentialRevealEnabled,
            @Value("${aiops.validation-lab.live-enabled:false}") boolean validationLabLiveEnabled,
            @Value("${aiops.validation-lab.allow-production:false}") boolean validationLabProductionAllowed,
            @Value("${spring.security.oauth2.client.provider.aiops.issuer-uri:}") String oidcIssuerUri,
            @Value("${spring.security.oauth2.client.registration.aiops.client-id:}") String oidcClientId,
            @Value("${server.servlet.session.cookie.secure:false}") boolean sessionCookieSecure,
            @Value("${aiops.security.oidc-provider.explicit-endpoints-enabled:false}") boolean explicitOidcEndpointsEnabled,
            @Value("${aiops.security.oidc-provider.authorization-uri:}") String oidcAuthorizationUri,
            @Value("${aiops.security.oidc-provider.token-uri:}") String oidcTokenUri,
            @Value("${aiops.security.oidc-provider.jwk-set-uri:}") String oidcJwkSetUri,
            @Value("${aiops.security.oidc-provider.user-info-uri:}") String oidcUserInfoUri,
            @Value("${aiops.security.public-base-url:}") String publicBaseUrl,
            @Value("${aiops.ai.base-url:}") String aiBaseUrl,
            @Value("${aiops.command-console.runner.mode:local}") String commandRunnerMode
    ) {
        this.environment = environment;
        this.datasourceUrl = datasourceUrl;
        this.masterKey = masterKey;
        this.credentialRevealEnabled = credentialRevealEnabled;
        this.validationLabLiveEnabled = validationLabLiveEnabled;
        this.validationLabProductionAllowed = validationLabProductionAllowed;
        this.oidcIssuerUri = oidcIssuerUri;
        this.oidcClientId = oidcClientId;
        this.sessionCookieSecure = sessionCookieSecure;
        this.explicitOidcEndpointsEnabled = explicitOidcEndpointsEnabled;
        this.oidcAuthorizationUri = oidcAuthorizationUri;
        this.oidcTokenUri = oidcTokenUri;
        this.oidcJwkSetUri = oidcJwkSetUri;
        this.oidcUserInfoUri = oidcUserInfoUri;
        this.publicBaseUrl = publicBaseUrl;
        this.aiBaseUrl = aiBaseUrl;
        this.commandRunnerMode = commandRunnerMode;
    }

    public RuntimeReadiness assess() {
        boolean localProfile = Arrays.asList(environment.getActiveProfiles()).contains("local")
                || environment.getActiveProfiles().length == 0;
        List<ReadinessCheck> checks = new ArrayList<>();
        checks.add(authenticationCheck(localProfile));
        checks.add(identityProviderEndpointsCheck(localProfile));
        checks.add(sessionCookieCheck(localProfile));
        checks.add(publicPortalOriginCheck(localProfile));
        checks.add(aiProviderTransportCheck(localProfile));
        checks.add(commandRunnerIsolationCheck(localProfile));
        checks.add(databaseCheck());
        checks.add(masterKeyCheck(localProfile));
        checks.add(credentialRevealCheck(localProfile));
        checks.add(validationLabCheck());

        ReadinessStatus status = checks.stream()
                .map(ReadinessCheck::status)
                .max(ReadinessStatus::compareSeverity)
                .orElse(ReadinessStatus.READY);
        String mode = switch (status) {
            case READY -> "production";
            case PILOT -> "single-operator-pilot";
            case BLOCKED -> "unsafe-configuration";
        };
        return new RuntimeReadiness(status, mode, Instant.now(), List.copyOf(checks));
    }

    private ReadinessCheck authenticationCheck(boolean localProfile) {
        if (localProfile) {
            return check("AUTHENTICATION_MODE", ReadinessStatus.PILOT, "Local authentication mode",
                    "The local profile permits unauthenticated access for supervised development.",
                    "Use the production security profile and an external identity provider before multi-user operation.",
                    "local/permit-all");
        }
        boolean oidcProfile = Arrays.asList(environment.getActiveProfiles()).contains("security-oidc");
        if (!oidcProfile || oidcIssuerUri.isBlank() || oidcClientId.isBlank()) {
            return check("AUTHENTICATION_MODE", ReadinessStatus.BLOCKED, "OIDC BFF configuration incomplete",
                    "A non-local profile is active, but the OIDC BFF profile, issuer, or client ID is missing.",
                    "Activate security-oidc and configure a trusted issuer and confidential client.",
                    String.join(",", environment.getActiveProfiles()));
        }
        if (sessionCookieSecure && !isHttps(oidcIssuerUri)) {
            return check("AUTHENTICATION_MODE", ReadinessStatus.BLOCKED, "Public OIDC issuer is not HTTPS",
                    "Secure browser sessions require a public HTTPS issuer.",
                    "Configure AIOPS_OIDC_ISSUER_URI with the externally trusted HTTPS Realm URL.",
                    endpointLabel(oidcIssuerUri));
        }
        return check("AUTHENTICATION_MODE", ReadinessStatus.READY, "Production security profile",
                "The OIDC BFF profile, issuer, and client ID are configured.",
                "Verify MFA, group claims, JWKS rotation, and scoped two-user acceptance.", oidcIssuerUri);
    }

    private ReadinessCheck identityProviderEndpointsCheck(boolean localProfile) {
        if (localProfile) {
            return check("IDENTITY_PROVIDER_ENDPOINTS", ReadinessStatus.PILOT,
                    "Local identity transport", "Identity endpoint transport is not a production gate in local mode.",
                    "Use HTTPS public URLs and cluster-internal transport endpoints in Kubernetes.", "local");
        }
        if (!explicitOidcEndpointsEnabled) {
            return check("IDENTITY_PROVIDER_ENDPOINTS", ReadinessStatus.READY,
                    "OIDC discovery enabled", "Provider endpoints are resolved from the trusted public issuer.",
                    "Monitor discovery and JWKS availability from the Backend network path.", "issuer-discovery");
        }
        if (oidcAuthorizationUri.isBlank() || oidcTokenUri.isBlank()
                || oidcJwkSetUri.isBlank() || oidcUserInfoUri.isBlank()) {
            return check("IDENTITY_PROVIDER_ENDPOINTS", ReadinessStatus.BLOCKED,
                    "Explicit OIDC endpoints incomplete", "One or more required provider endpoints are missing.",
                    "Configure authorization, token, JWKS, and user-info endpoints together.", "incomplete");
        }
        if (sessionCookieSecure && !isHttps(oidcAuthorizationUri)) {
            return check("IDENTITY_PROVIDER_ENDPOINTS", ReadinessStatus.BLOCKED,
                    "Authorization endpoint is not HTTPS", "The browser authorization endpoint is not secure.",
                    "Use the public HTTPS Keycloak authorization endpoint.", endpointLabel(oidcAuthorizationUri));
        }
        if (!isSecureOrClusterInternal(oidcTokenUri)
                || !isSecureOrClusterInternal(oidcJwkSetUri)
                || !isSecureOrClusterInternal(oidcUserInfoUri)) {
            return check("IDENTITY_PROVIDER_ENDPOINTS", ReadinessStatus.BLOCKED,
                    "Unsafe internal OIDC transport", "Backend provider traffic uses an untrusted plain HTTP host.",
                    "Use HTTPS or a Kubernetes .svc service name for internal OIDC traffic.",
                    String.join(",", endpointLabel(oidcTokenUri), endpointLabel(oidcJwkSetUri), endpointLabel(oidcUserInfoUri)));
        }
        return check("IDENTITY_PROVIDER_ENDPOINTS", ReadinessStatus.READY,
                "Split OIDC endpoints configured",
                "Browser authorization remains public while Backend provider traffic uses a trusted internal path.",
                "Keep public issuer and internal service certificates, DNS, and NetworkPolicy under change control.",
                String.join(",", endpointLabel(oidcAuthorizationUri), endpointLabel(oidcTokenUri)));
    }

    private boolean isHttps(String value) {
        try {
            return "https".equalsIgnoreCase(URI.create(value).getScheme());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isSecureOrClusterInternal(String value) {
        try {
            URI uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return true;
            }
            String host = uri.getHost();
            return "http".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.endsWith(".svc") || host.endsWith(".svc.cluster.local"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String endpointLabel(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (IllegalArgumentException exception) {
            return "invalid";
        }
    }

    private ReadinessCheck sessionCookieCheck(boolean localProfile) {
        if (sessionCookieSecure) {
            return check("SESSION_COOKIE", ReadinessStatus.READY, "Secure session cookie",
                    "The browser session cookie is restricted to HTTPS transport.",
                    "Keep TLS termination and forwarded headers aligned with the public origin.", "secure");
        }
        return check("SESSION_COOKIE", localProfile ? ReadinessStatus.PILOT : ReadinessStatus.BLOCKED,
                "Insecure session cookie", "The session cookie may be sent over plain HTTP.",
                "Set AIOPS_SESSION_COOKIE_SECURE=true behind HTTPS outside local development.", "not-secure");
    }

    private ReadinessCheck publicPortalOriginCheck(boolean localProfile) {
        if (isHttps(publicBaseUrl)) {
            return check("PUBLIC_PORTAL_ORIGIN", ReadinessStatus.READY, "HTTPS public Portal origin",
                    "Browser callbacks and generated links use an HTTPS Portal origin.",
                    "Keep the certificate, DNS record, proxy headers, and Keycloak redirect allow-list aligned.",
                    endpointLabel(publicBaseUrl));
        }
        ReadinessStatus status = localProfile ? ReadinessStatus.PILOT : ReadinessStatus.BLOCKED;
        return check("PUBLIC_PORTAL_ORIGIN", status, "Public Portal origin is not HTTPS",
                "The browser-facing Portal URL is missing or uses plain HTTP.",
                "Set AIOPS_PUBLIC_BASE_URL to the externally reachable HTTPS Portal origin.", endpointLabel(publicBaseUrl));
    }

    private ReadinessCheck aiProviderTransportCheck(boolean localProfile) {
        if (isSecureOrClusterInternal(aiBaseUrl)) {
            return check("AI_PROVIDER_TRANSPORT", ReadinessStatus.READY, "Trusted AI provider transport",
                    "Ollama traffic uses HTTPS or a Kubernetes cluster-internal service address.",
                    "Keep NetworkPolicy, timeout, model capacity, and endpoint health under observation.",
                    endpointLabel(aiBaseUrl));
        }
        ReadinessStatus status = localProfile ? ReadinessStatus.PILOT : ReadinessStatus.BLOCKED;
        return check("AI_PROVIDER_TRANSPORT", status, "Untrusted AI provider transport",
                "The configured AI endpoint is not HTTPS and is not a Kubernetes service address.",
                "Expose Ollama through an internal .svc address or trusted HTTPS endpoint.", endpointLabel(aiBaseUrl));
    }

    private ReadinessCheck commandRunnerIsolationCheck(boolean localProfile) {
        if ("remote".equalsIgnoreCase(commandRunnerMode)) {
            return check("COMMAND_RUNNER_ISOLATION", ReadinessStatus.READY, "Isolated command runner",
                    "Non-interactive kubectl processes run outside the Portal Backend filesystem and process boundary.",
                    "Monitor runner availability, rejection rate, resource pressure, and replay conflicts.", "remote");
        }
        return check("COMMAND_RUNNER_ISOLATION", localProfile ? ReadinessStatus.PILOT : ReadinessStatus.BLOCKED,
                "Backend-local command runner", "kubectl processes share the Portal Backend container boundary.",
                "Set AIOPS_COMMAND_RUNNER_MODE=remote and deploy the isolated command-runner component.", "local");
    }

    private ReadinessCheck databaseCheck() {
        boolean fileH2 = datasourceUrl.toLowerCase().startsWith("jdbc:h2:file:");
        if (fileH2) {
            return check("DATABASE_DURABILITY", ReadinessStatus.PILOT, "Single-node file database",
                    "File-based H2 is suitable for a supervised pilot but has no application-level high availability.",
                    "Move to PostgreSQL with backups before multi-instance or production operation.", "file-h2");
        }
        return check("DATABASE_DURABILITY", ReadinessStatus.READY, "External database configured",
                "The application is configured with an external database.",
                "Verify backup, restore, failover, and connection-pool alerts.", datasourceFamily());
    }

    private ReadinessCheck masterKeyCheck(boolean localProfile) {
        boolean weak = masterKey.isBlank() || DEFAULT_LOCAL_MASTER_KEY.equals(masterKey);
        if (!weak) {
            return check("MASTER_KEY_STRENGTH", ReadinessStatus.READY, "Custom credential encryption key",
                    "A non-default credential encryption key is configured.",
                    "Store and rotate the key through the deployment secret manager.", "custom");
        }
        ReadinessStatus status = localProfile ? ReadinessStatus.PILOT : ReadinessStatus.BLOCKED;
        return check("MASTER_KEY_STRENGTH", status, "Default credential encryption key",
                "The bundled development key cannot protect kubeconfig credentials in a shared environment.",
                "Set AIOPS_LOCAL_MASTER_KEY to a strong secret before registering production clusters.", "default-or-empty");
    }

    private ReadinessCheck credentialRevealCheck(boolean localProfile) {
        if (!credentialRevealEnabled) {
            return check("CREDENTIAL_REVEAL", ReadinessStatus.READY, "Credential reveal disabled",
                    "Stored kubeconfig plaintext cannot be returned through the API.",
                    "Keep this disabled; use a controlled break-glass workflow if retrieval is ever required.", "disabled");
        }
        ReadinessStatus status = localProfile ? ReadinessStatus.PILOT : ReadinessStatus.BLOCKED;
        return check("CREDENTIAL_REVEAL", status, "Credential reveal enabled",
                "The API can return stored kubeconfig plaintext to callers.",
                "Set AIOPS_CREDENTIAL_REVEAL_ENABLED=false outside isolated development.", "enabled");
    }

    private ReadinessCheck validationLabCheck() {
        if (validationLabProductionAllowed) {
            return check("VALIDATION_LAB_SAFETY", ReadinessStatus.BLOCKED, "Production validation allowed",
                    "Fault-injection validation is allowed against production-labelled targets.",
                    "Set AIOPS_VALIDATION_LAB_ALLOW_PRODUCTION=false and isolate validation namespaces.", "production-allowed");
        }
        if (validationLabLiveEnabled) {
            return check("VALIDATION_LAB_SAFETY", ReadinessStatus.PILOT, "Live validation enabled",
                    "The validation lab may create temporary Kubernetes resources in approved namespaces.",
                    "Restrict the service account and continuously verify TTL cleanup.", "live-non-production");
        }
        return check("VALIDATION_LAB_SAFETY", ReadinessStatus.READY, "Live validation disabled",
                "Fault-injection validation cannot create live resources.",
                "Enable only in an isolated test cluster when required.", "disabled");
    }

    private String datasourceFamily() {
        int separator = datasourceUrl.indexOf(':', "jdbc:".length());
        return separator > 0 ? datasourceUrl.substring("jdbc:".length(), separator) : "external";
    }

    private ReadinessCheck check(String code, ReadinessStatus status, String title, String detail,
                                 String action, String observedValue) {
        return new ReadinessCheck(code, status, title, detail, action, observedValue);
    }

    public record RuntimeReadiness(ReadinessStatus status, String mode, Instant checkedAt,
                                   List<ReadinessCheck> checks) {
    }

    public record ReadinessCheck(String code, ReadinessStatus status, String title, String detail,
                                 String action, String observedValue) {
    }

    public enum ReadinessStatus {
        READY(0), PILOT(1), BLOCKED(2);

        private final int severity;

        ReadinessStatus(int severity) {
            this.severity = severity;
        }

        private static int compareSeverity(ReadinessStatus left, ReadinessStatus right) {
            return Integer.compare(left.severity, right.severity);
        }
    }
}
