package io.strato.aiops.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
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
class TenantAccessApiTest {
    private static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    @Autowired private MockMvc mockMvc;

    @Test
    void invitesTenantOperatorAndResolvesSelectedScopeCapabilities() throws Exception {
        RequestPostProcessor admin = login("admin", List.of("aiops-platform-admins"));
        mockMvc.perform(post("/api/tenants/{tenantId}/members", DEFAULT_TENANT).with(admin).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"issuer":"https://idp.example","subject":"tenant-operator",
                                 "role":"OPERATOR","scopeType":"TENANT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INVITED"));

        RequestPostProcessor operator = login("tenant-operator", List.of());
        mockMvc.perform(get("/api/auth/me").with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities", hasItem("application:deploy")));
        mockMvc.perform(get("/api/me/access").param("tenantId", DEFAULT_TENANT).with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveCapabilities", hasItem("application:deploy")))
                .andExpect(jsonPath("$.navigation.applications").value(true));
    }

    @Test
    void managesExplicitGroupMappingAndRejectsMandatoryFeatureDisable() throws Exception {
        RequestPostProcessor admin = login("mapping-admin", List.of("aiops-platform-admins"));
        mockMvc.perform(post("/api/tenants/{tenantId}/oidc-group-mappings", DEFAULT_TENANT)
                        .with(admin).with(csrf()).contentType("application/json")
                        .content("""
                                {"issuer":"https://idp.example","groupValue":"/companies/aa/operators",
                                 "role":"OPERATOR","scopeType":"TENANT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupValue").value("/companies/aa/operators"));

        mockMvc.perform(get("/api/auth/me").with(login("group-user", List.of("/companies/aa/operators"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities", hasItem("application:deploy")));

        mockMvc.perform(patch("/api/tenants/{tenantId}/features", DEFAULT_TENANT)
                        .with(admin).with(csrf()).contentType("application/json")
                        .content("{\"featureKey\":\"CORE_OVERVIEW\",\"enabled\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disabledTenantFeatureBlocksBothNavigationAndDirectApi() throws Exception {
        RequestPostProcessor admin = login("feature-admin", List.of("aiops-platform-admins"));
        setFeature(admin, false);
        try {
            mockMvc.perform(get("/api/me/access").param("tenantId", DEFAULT_TENANT).with(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.navigation.applications").value(false));
            mockMvc.perform(get("/api/v2/application-delivery/charts")
                            .param("tenantId", DEFAULT_TENANT).with(admin))
                    .andExpect(status().isForbidden());
        } finally {
            // 공유 Testcontainers DB를 사용하는 다른 테스트에 기능 OFF 상태를 남기지 않는다.
            setFeature(admin, true);
        }
    }

    private void setFeature(RequestPostProcessor admin, boolean enabled) throws Exception {
        mockMvc.perform(patch("/api/tenants/{tenantId}/features", DEFAULT_TENANT)
                        .with(admin).with(csrf()).contentType("application/json")
                        .content("{\"featureKey\":\"APPLICATION_DELIVERY\",\"enabled\":" + enabled + "}"))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor login(String subject, List<String> groups) {
        return oidcLogin().idToken(token -> token
                .issuer("https://idp.example")
                .subject(subject)
                .claim("preferred_username", subject)
                .claim("name", subject)
                .claim("groups", groups));
    }
}
