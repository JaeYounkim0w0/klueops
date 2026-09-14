package io.strato.aiops.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "aiops.security.credential-reveal-enabled=false",
        "aiops.crypto.local-master-key=local-development-master-key"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class RuntimeReadinessApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsPilotModeWithActionableRuntimeChecks() throws Exception {
        mockMvc.perform(get("/api/operations/runtime-readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PILOT"))
                .andExpect(jsonPath("$.mode").value("single-operator-pilot"))
                .andExpect(jsonPath("$.checkedAt", notNullValue()))
                .andExpect(jsonPath("$.checks[*].code", hasItem("AUTHENTICATION_MODE")))
                .andExpect(jsonPath("$.checks[*].code", hasItem("IDENTITY_PROVIDER_ENDPOINTS")))
                .andExpect(jsonPath("$.checks[*].code", hasItem("DATABASE_DURABILITY")))
                .andExpect(jsonPath("$.checks[*].code", hasItem("MASTER_KEY_STRENGTH")))
                .andExpect(jsonPath("$.checks[*].code", hasItem("CREDENTIAL_REVEAL")));
    }

    @Test
    void deniesPlaintextCredentialRevealWhenFeatureIsDisabled() throws Exception {
        String response = mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "readiness-credential-cluster",
                                  "environment": "DEV",
                                  "provider": "KIND",
                                  "credentialType": "KUBECONFIG",
                                  "kubeconfig": "apiVersion: v1\\nkind: Config\\nclusters: []",
                                  "syncSettings": {
                                    "autoSyncEnabled": false,
                                    "syncIntervalSeconds": 300
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String clusterId = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        mockMvc.perform(get("/api/clusters/{clusterId}/credential", clusterId)
                        .queryParam("reveal", "true"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CREDENTIAL_REVEAL_DISABLED"))
                .andExpect(jsonPath("$.requestId", notNullValue()));
    }
}
