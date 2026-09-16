package io.strato.aiops.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TenancyApiTest {

    @Autowired
    private MockMvc mockMvc;

    /** TenancyApiTest의 exposesDefaultTenantAndWorkspaceForMigratedData 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void exposesDefaultTenantAndWorkspaceForMigratedData() throws Exception {
        mockMvc.perform(get("/api/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'default')]", hasSize(1)));

        mockMvc.perform(get("/api/tenants/00000000-0000-0000-0000-000000000001/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'default')]", hasSize(1)));
    }

    /** TenancyApiTest의 createsTenantWorkspaceAndFiltersClustersByPlacement 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Test
    void createsTenantWorkspaceAndFiltersClustersByPlacement() throws Exception {
        String tenantResponse = mockMvc.perform(post("/api/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"customer-a","name":"Customer A","description":"Isolated customer"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("customer-a"))
                .andReturn().getResponse().getContentAsString();
        String tenantId = JsonPath.read(tenantResponse, "$.id");

        String workspaceResponse = mockMvc.perform(post("/api/tenants/{tenantId}/workspaces", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"payments","name":"Payments","description":"Payment operations"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenantId))
                .andReturn().getResponse().getContentAsString();
        String workspaceId = JsonPath.read(workspaceResponse, "$.id");

        mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "workspaceId":"%s",
                                  "name":"customer-a-dev",
                                  "environment":"DEV",
                                  "provider":"KIND",
                                  "credentialType":"KUBECONFIG",
                                  "kubeconfig":"apiVersion: v1\\nkind: Config\\nclusters: []"
                                }
                                """.formatted(tenantId, workspaceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenantId))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId));

        mockMvc.perform(get("/api/clusters").queryParam("tenantId", tenantId).queryParam("workspaceId", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("customer-a-dev"));

        mockMvc.perform(get("/api/clusters")
                        .queryParam("tenantId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'customer-a-dev')]", hasSize(0)));
    }

    /** TenancyApiTest의 rejectsWorkspaceFromAnotherTenantDuringClusterRegistration 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsWorkspaceFromAnotherTenantDuringClusterRegistration() throws Exception {
        mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"00000000-0000-0000-0000-000000000001",
                                  "workspaceId":"00000000-0000-0000-0000-000000000099",
                                  "name":"invalid-placement",
                                  "environment":"DEV",
                                  "provider":"KIND",
                                  "credentialType":"KUBECONFIG",
                                  "kubeconfig":"apiVersion: v1\\nkind: Config\\nclusters: []"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
