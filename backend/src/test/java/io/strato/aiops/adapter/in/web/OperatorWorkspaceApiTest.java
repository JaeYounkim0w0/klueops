package io.strato.aiops.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OperatorWorkspaceApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClusterRepositoryPort clusters;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void supportsManualIncidentCollaborationAndSearch() throws Exception {
        Cluster cluster = clusters.save(Cluster.register("workspace-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));

        String created = mockMvc.perform(post("/api/incidents/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clusterId":"%s","namespace":"default","resourceKind":"Pod","resourceName":"api-0",
                                 "severity":"HIGH","title":"API pod unavailable","summary":"manual verification",
                                 "nextAction":"Inspect pod events"}
                                """.formatted(cluster.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        String incidentId = JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/incidents/{id}/collaboration", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignee":"oncall@example.com","tags":["customer-impact","api"],
                                 "acknowledgeDueAt":"2026-09-04T10:00:00Z","resolveDueAt":"2026-09-04T12:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value("oncall@example.com"))
                .andExpect(jsonPath("$.tags[0]").value("customer-impact"));

        mockMvc.perform(post("/api/incidents/{id}/comments", incidentId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Investigating node events\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/search").param("q", "API pod").param("clusterId", cluster.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("INCIDENT"))
                .andExpect(jsonPath("$[0].targetPath").value("/incidents/" + incidentId));
    }

    @Test
    void managesCustomRunbookVersionsAndRejectsUnsafeReadOnlyCommands() throws Exception {
        String created = mockMvc.perform(post("/api/runbooks/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runbookBody("kubectl get pod/{resourceName} -n {namespace}", "Initial")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("CUSTOM"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(put("/api/runbooks/custom/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runbookBody("kubectl describe pod/{resourceName} -n {namespace}", "Clarify events")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/runbooks/custom/{id}/versions", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(post("/api/runbooks/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runbookBody("kubectl delete pod/{resourceName} -n {namespace}", "Unsafe")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(delete("/api/runbooks/custom/{id}", id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/runbooks/custom/{id}/versions", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesResourceRelationshipsChangesAndLinkedIncidents() throws Exception {
        Cluster cluster = clusters.save(Cluster.register("context-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));
        UUID asyncJobId = UUID.randomUUID();
        UUID syncJobId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());
        jdbc.update("insert into async_jobs (id,type,status,created_at) values (?,?,?,?)", asyncJobId, "CLUSTER_SYNC", "SUCCEEDED", now);
        jdbc.update("""
                insert into sync_jobs (id,async_job_id,cluster_id,sync_type,status,requested_by,resource_count,event_count,completed_at,created_at)
                values (?,?,?,?,?,?,?,?,?,?)
                """, syncJobId, asyncJobId, cluster.id(), "MANUAL_CLUSTER", "SUCCEEDED", "test", 2, 0, now, now);
        jdbc.update("""
                insert into kubernetes_resource_snapshots
                (id,cluster_id,sync_job_id,namespace,resource_type,resource_name,status,summary_json,raw_json,truncated,collected_at)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), cluster.id(), syncJobId, "default", "Pod", "api-0", "Running", "{\"phase\":\"Running\",\"readyContainers\":1}",
                "{\"spec\":{\"volumes\":[{\"configMap\":{\"name\":\"api-config\"}}]}}", false,
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));
        jdbc.update("""
                insert into kubernetes_resource_snapshots
                (id,cluster_id,sync_job_id,namespace,resource_type,resource_name,status,summary_json,raw_json,truncated,collected_at)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), cluster.id(), syncJobId, "default", "Pod", "api-0", "Pending", "{}",
                "{\"spec\":{\"volumes\":[{\"configMap\":{\"name\":\"api-config\"}}]}}", false, now);
        jdbc.update("""
                insert into kubernetes_resource_snapshots
                (id,cluster_id,sync_job_id,namespace,resource_type,resource_name,status,summary_json,raw_json,truncated,collected_at)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), cluster.id(), syncJobId, "default", "ConfigMap", "api-config", "Active", "{}", "{}", false, now);

        mockMvc.perform(post("/api/incidents/manual").contentType(MediaType.APPLICATION_JSON).content("""
                {"clusterId":"%s","namespace":"default","resourceKind":"Pod","resourceName":"api-0",
                 "severity":"HIGH","title":"Pod waiting for config","summary":"config reference","nextAction":"Inspect ConfigMap"}
                """.formatted(cluster.id()))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/clusters/{clusterId}/resources/Pod/api-0/context", cluster.id()).param("namespace", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relations[0].resourceKind").value("ConfigMap"))
                .andExpect(jsonPath("$.relations[0].relation").value("REFERENCES"))
                .andExpect(jsonPath("$.fieldDiffs[0].field").value("phase"))
                .andExpect(jsonPath("$.incidents[0].title").value("Pod waiting for config"));
    }

    private String runbookBody(String command, String note) {
        return """
                {"signal":"CrashLoopBackOff","category":"APPLICATION_STARTUP","resourceKind":"Pod",
                 "title":"Custom startup diagnosis","beginnerExplanation":"Check why the container exits.",
                 "verificationCommand":"%s","expectedResult":"The exit reason is visible.",
                 "safeAction":"Review configuration before changing it.",
                 "validationCommand":"kubectl get pod/{resourceName} -n {namespace}",
                 "rollbackGuidance":"Restore the previous manifest.","safetyLevel":"READ_ONLY",
                 "enabled":true,"changeNote":"%s"}
                """.formatted(command, note);
    }
}
