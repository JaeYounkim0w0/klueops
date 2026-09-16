package io.strato.aiops.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        "spring.security.oauth2.client.provider.aiops.user-name-attribute=sub"
})
@AutoConfigureMockMvc
@ActiveProfiles("security-oidc-test")
class SecurityAdministrationApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** SecurityAdministrationApiTest의 deniesIdentityAdministrationWithoutCapability 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void deniesIdentityAdministrationWithoutCapability() throws Exception {
        mockMvc.perform(get("/api/security/users").with(login("ordinary-user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /** SecurityAdministrationApiTest의 allowsPlatformAdministratorToListUsersAndBindings 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void allowsPlatformAdministratorToListUsersAndBindings() throws Exception {
        RequestPostProcessor login = login("platform-admin");
        mockMvc.perform(get("/api/auth/me").with(login)).andExpect(status().isOk());
        UUID userId = jdbcTemplate.queryForObject(
                "select id from aiops_users where subject = ?", UUID.class, "platform-admin");
        jdbcTemplate.update("""
                insert into role_bindings (
                    id, principal_type, principal_key, role_name, scope_type,
                    cluster_id, namespace, created_by, created_at
                ) values (?, 'USER', ?, 'PLATFORM_ADMIN', 'PLATFORM', null, null, 'test', ?)
                """, UUID.randomUUID(), userId.toString(), java.sql.Timestamp.from(Instant.now()));

        mockMvc.perform(get("/api/security/users").with(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("platform-admin")));
        mockMvc.perform(get("/api/security/role-bindings").with(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("PLATFORM_ADMIN"));
    }

    /** SecurityAdministrationApiTest의 preventsSelfDisableAndUnknownUserBinding 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void preventsSelfDisableAndUnknownUserBinding() throws Exception {
        RequestPostProcessor login = login("safety-admin");
        mockMvc.perform(get("/api/auth/me").with(login)).andExpect(status().isOk());
        UUID userId = jdbcTemplate.queryForObject(
                "select id from aiops_users where subject = ?", UUID.class, "safety-admin");
        jdbcTemplate.update("""
                insert into role_bindings (
                    id, principal_type, principal_key, role_name, scope_type,
                    cluster_id, namespace, created_by, created_at
                ) values (?, 'USER', ?, 'PLATFORM_ADMIN', 'PLATFORM', null, null, 'test', ?)
                """, UUID.randomUUID(), userId.toString(), java.sql.Timestamp.from(Instant.now()));

        mockMvc.perform(patch("/api/security/users/{userId}", userId).with(login).with(csrf())
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/security/role-bindings").with(login).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"principalType":"USER","principalKey":"00000000-0000-0000-0000-000000000099",
                                 "role":"VIEWER","scopeType":"PLATFORM"}
                                """))
                .andExpect(status().isBadRequest());
    }

    /** SecurityAdministrationApiTest의 login 처리에 필요한 업무 로직을 수행한다. */
    private RequestPostProcessor login(String subject) {
        return oidcLogin().idToken(token -> token
                .issuer("https://idp.example")
                .subject(subject)
                .claim("preferred_username", subject)
                .claim("name", subject));
    }
}
