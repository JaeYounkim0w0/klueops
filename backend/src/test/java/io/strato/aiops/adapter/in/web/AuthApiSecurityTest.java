package io.strato.aiops.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.aiops.client-id=test-client",
        "spring.security.oauth2.client.registration.aiops.client-secret=test-secret",
        "spring.security.oauth2.client.registration.aiops.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.aiops.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.registration.aiops.scope=openid,profile,email",
        "spring.security.oauth2.client.provider.aiops.authorization-uri=https://idp.example/authorize",
        "spring.security.oauth2.client.provider.aiops.token-uri=https://idp.example/token",
        "spring.security.oauth2.client.provider.aiops.jwk-set-uri=https://idp.example/jwks",
        "spring.security.oauth2.client.provider.aiops.user-info-uri=https://idp.example/userinfo",
        "spring.security.oauth2.client.provider.aiops.user-name-attribute=sub",
        "aiops.security.oidc-provider.explicit-endpoints-enabled=true",
        "aiops.security.oidc-provider.issuer-uri=https://auth.aiops.test/realms/aiops",
        "aiops.security.oidc-provider.authorization-uri=https://auth.aiops.test/realms/aiops/protocol/openid-connect/auth",
        "aiops.security.oidc-provider.token-uri=http://aiops-keycloak.default.svc:8080/realms/aiops/protocol/openid-connect/token",
        "aiops.security.oidc-provider.jwk-set-uri=http://aiops-keycloak.default.svc:8080/realms/aiops/protocol/openid-connect/certs",
        "aiops.security.oidc-provider.user-info-uri=http://aiops-keycloak.default.svc:8080/realms/aiops/protocol/openid-connect/userinfo",
        "aiops.security.oidc-provider.user-name-attribute=sub"
})
@AutoConfigureMockMvc
@ActiveProfiles("security-oidc-test")
class AuthApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    /** AuthApiSecurityTest의 keepsPublicIssuerAndAuthorizationWithInternalTransportEndpoints 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void keepsPublicIssuerAndAuthorizationWithInternalTransportEndpoints() {
        var registration = clientRegistrationRepository.findByRegistrationId("aiops");

        org.assertj.core.api.Assertions.assertThat(registration.getProviderDetails().getIssuerUri())
                .isEqualTo("https://auth.aiops.test/realms/aiops");
        org.assertj.core.api.Assertions.assertThat(registration.getProviderDetails().getAuthorizationUri())
                .startsWith("https://auth.aiops.test/");
        org.assertj.core.api.Assertions.assertThat(registration.getProviderDetails().getTokenUri())
                .startsWith("http://aiops-keycloak.default.svc:8080/");
        org.assertj.core.api.Assertions.assertThat(registration.getProviderDetails().getJwkSetUri())
                .startsWith("http://aiops-keycloak.default.svc:8080/");
        org.assertj.core.api.Assertions.assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUri())
                .startsWith("http://aiops-keycloak.default.svc:8080/");
    }

    /** AuthApiSecurityTest의 returnsJson401ForProtectedApi 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void returnsJson401ForProtectedApi() throws Exception {
        mockMvc.perform(get("/api/clusters"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    /** AuthApiSecurityTest의 exposesAnonymousSessionStateWithoutRedirect 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void exposesAnonymousSessionStateWithoutRedirect() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    /** AuthApiSecurityTest의 exposesKubernetesHealthProbeGroupsWithoutAuthentication 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void exposesKubernetesHealthProbeGroupsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    /** AuthApiSecurityTest의 letsTheOneTimeTicketLayerAuthenticateTerminalWebSocketUpgrades 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void letsTheOneTimeTicketLayerAuthenticateTerminalWebSocketUpgrades() throws Exception {
        mockMvc.perform(get("/ws/command-sessions/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isBadRequest());
    }

    /** AuthApiSecurityTest의 returnsNotFoundForAnUnmappedAuthenticatedRoute 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void returnsNotFoundForAnUnmappedAuthenticatedRoute() throws Exception {
        mockMvc.perform(get("/unmapped-spa-route").with(oidcLogin()))
                .andExpect(status().isNotFound());
    }

    /** AuthApiSecurityTest의 provisionsAuthenticatedOidcUserAndReturnsIdentity 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void provisionsAuthenticatedOidcUserAndReturnsIdentity() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(oidcLogin().idToken(token -> token
                        .issuer("https://idp.example")
                        .subject("operator-1")
                        .claim("preferred_username", "operator")
                        .claim("name", "Example Operator")
                        .claim("email", "operator@example.com")
                        .claim("groups", java.util.List.of("team-operators")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.username").value("operator"))
                .andExpect(jsonPath("$.session.idleTimeoutSeconds").isNumber())
                .andExpect(jsonPath("$.session.expiresAt").isString())
                .andExpect(jsonPath("$.session.absoluteExpiresAt").isString())
                .andExpect(jsonPath("$.session.canExtend").value(true));
    }

    /** AuthApiSecurityTest의 extendsAnAuthenticatedBrowserSession 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void extendsAnAuthenticatedBrowserSession() throws Exception {
        var login = oidcLogin().idToken(token -> token
                .issuer("https://idp.example").subject("operator-session"));

        mockMvc.perform(post("/api/auth/session/extend").with(login).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idleTimeoutSeconds").isNumber())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.absoluteExpiresAt").isString())
                .andExpect(jsonPath("$.canExtend").value(true));
    }

    /** AuthApiSecurityTest의 rejectsMutationWithoutCsrf 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsMutationWithoutCsrf() throws Exception {
        var login = oidcLogin().idToken(token -> token.issuer("https://idp.example").subject("operator-2"));
        mockMvc.perform(post("/logout").with(login))
                .andExpect(status().isForbidden());
    }

    /** AuthApiSecurityTest의 rejectsAuthenticatedUsersWithoutExplicitRoleOrGroupMapping 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsAuthenticatedUsersWithoutExplicitRoleOrGroupMapping() throws Exception {
        mockMvc.perform(get("/api/clusters").with(oidcLogin().idToken(token -> token
                        .issuer("https://idp.example").subject("unassigned"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/clusters").with(oidcLogin().idToken(token -> token
                        .issuer("https://idp.example").subject("viewer")
                        .claim("groups", java.util.List.of("aiops-viewers")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /** AuthApiSecurityTest의 filtersClusterCollectionByServerSideScope 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void filtersClusterCollectionByServerSideScope() throws Exception {
        UUID visible = UUID.randomUUID();
        UUID hidden = UUID.randomUUID();
        insertCluster(visible, "visible-" + visible);
        insertCluster(hidden, "hidden-" + hidden);
        var login = oidcLogin().idToken(token -> token
                .issuer("https://idp.example").subject("cluster-viewer")
                .claim("preferred_username", "cluster-viewer"));
        mockMvc.perform(get("/api/auth/me").with(login)).andExpect(status().isOk());
        UUID userId = jdbcTemplate.queryForObject(
                "select id from aiops_users where subject = ?", UUID.class, "cluster-viewer");
        jdbcTemplate.update("""
                insert into role_bindings (
                    id, principal_type, principal_key, role_name, scope_type,
                    cluster_id, namespace, created_by, created_at
                ) values (?, 'USER', ?, 'VIEWER', 'CLUSTER', ?, null, 'test', ?)
                """, UUID.randomUUID(), userId.toString(), visible, java.sql.Timestamp.from(Instant.now()));

        mockMvc.perform(get("/api/clusters").with(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(visible)).exists())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(hidden)).doesNotExist());
    }

    /** AuthApiSecurityTest의 insertCluster 처리에 필요한 업무 로직을 수행한다. */
    private void insertCluster(UUID id, String name) {
        jdbcTemplate.update("""
                insert into clusters (id, tenant_id, workspace_id, name, description, environment, provider, region, status,
                                      created_by, created_at, updated_at)
                values (?, '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002',
                        ?, '', 'DEV', 'ETC', '', 'REGISTERED', 'test', ?, ?)
                """, id, name, java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now()));
    }
}
