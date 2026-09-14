package io.strato.aiops.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import io.strato.aiops.application.port.out.AnalysisSessionRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisSession;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;
import io.strato.aiops.application.service.OperationsControlPlaneService;
import io.strato.aiops.application.service.IncidentReportExportService;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OperationsControlPlaneApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClusterRepositoryPort clusterRepository;
    @Autowired
    private AnalysisSessionRepositoryPort analysisRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OperationsControlPlaneService operationsService;
    @Autowired
    private IncidentReportExportService incidentReportExportService;

    @Test
    void exposesConsistentAiTrustSnapshot() throws Exception {
        mockMvc.perform(get("/api/operations/ai-trust"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("ai-trust.v1"))
                .andExpect(jsonPath("$.state").isNotEmpty())
                .andExpect(jsonPath("$.quality").exists())
                .andExpect(jsonPath("$.calibration").exists())
                .andExpect(jsonPath("$.recentRegressions").isArray())
                .andExpect(jsonPath("$.recentReleaseGates").isArray());
    }

    @Test
    void incidentReconciliationDeduplicatesEvidenceAndReopensResolvedIncident() throws Exception {
        Cluster cluster = clusterRepository.save(Cluster.register("operations-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));
        AnalysisSession first = analysisRepository.save(analysis(cluster.id(), "default"));

        mockMvc.perform(post("/api/operations/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openIncidents", greaterThanOrEqualTo(1)));

        String firstList = mockMvc.perform(get("/api/incidents").param("clusterId", cluster.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].occurrenceCount").value(1))
                .andReturn().getResponse().getContentAsString();
        String incidentId = JsonPath.read(firstList, "$[0].id");

        mockMvc.perform(post("/api/operations/reconcile"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/incidents/{incidentId}", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.occurrenceCount").value(1))
                .andExpect(jsonPath("$.incident.resourceKind").value("Pod"))
                .andExpect(jsonPath("$.incident.resourceName").value("configmap-db-pod"))
                .andExpect(jsonPath("$.evidence[0].sourceRef").value("analysis:" + first.id()));

        var markdownReport = mockMvc.perform(get("/api/incidents/{incidentId}/report", incidentId).param("format", "markdown"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", containsString("incident-" + incidentId)))
                .andReturn();
        mockMvc.perform(asyncDispatch(markdownReport))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("# Incident Report")));

        var zipReport = mockMvc.perform(get("/api/incidents/{incidentId}/report", incidentId).param("format", "zip"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .exists("X-Content-SHA256"))
                .andReturn();
        mockMvc.perform(asyncDispatch(zipReport))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType("application/zip"));
        var streamedReport = incidentReportExportService.export(UUID.fromString(incidentId), "zip", "test", "test-request");
        ByteArrayOutputStream streamedBytes = new ByteArrayOutputStream();
        streamedReport.writeTo(streamedBytes);
        byte[] archive = streamedBytes.toByteArray();
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) entries.add(entry.getName());
        }
        org.assertj.core.api.Assertions.assertThat(entries)
                .containsExactlyInAnyOrder("report.md", "report.json", "commands.csv", "command-executions.csv", "manifest.sha256");

        mockMvc.perform(patch("/api/incidents/{incidentId}/state", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"state":"RESOLVED","note":"verified in test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RESOLVED"));

        analysisRepository.save(analysis(cluster.id(), "default"));
        mockMvc.perform(post("/api/operations/reconcile"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/incidents/{incidentId}", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.state").value("REOPENED"))
                .andExpect(jsonPath("$.incident.occurrenceCount").value(2))
                .andExpect(jsonPath("$.incident.reopenCount").value(1))
                .andExpect(jsonPath("$.evidence.length()").value(2));
    }

    @Test
    void exposesPoliciesRunbooksSettingsAndAuditContracts() throws Exception {
        mockMvc.perform(get("/api/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].enabled").isBoolean());

        mockMvc.perform(get("/api/runbooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificationCommand").exists())
                .andExpect(jsonPath("$[0].safetyLevel").value("READ_ONLY"));

        mockMvc.perform(get("/api/settings/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisRetentionDays").value(90))
                .andExpect(jsonPath("$.auditRetentionDays").value(365))
                .andExpect(jsonPath("$.commandRetentionDays").value(90));

        mockMvc.perform(post("/api/settings/operations/cleanup-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executed").value(false))
                .andExpect(jsonPath("$.auditLogs").isNumber())
                .andExpect(jsonPath("$.commandExecutions").isNumber());

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk());
    }

    @Test
    void groupsWatchSignalsPromotesHighRiskAndExposesScorecardAndCalibration() throws Exception {
        Cluster cluster = clusterRepository.save(Cluster.register("triage-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));
        operationsService.ingestWatchSignal(new WatchSignal(UUID.randomUUID(), cluster.id(), cluster.name(),
                "nginx", "Pod", "web-1", "MODIFIED", "CrashLoopBackOff", "Pending",
                "container exits after binding port", Instant.now()));

        String triage = mockMvc.perform(get("/api/operations/triage")
                        .param("clusterId", cluster.id().toString()).param("namespace", "nginx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].state").value("INCIDENT_CREATED"))
                .andExpect(jsonPath("$.items[0].incidentId").exists())
                .andReturn().getResponse().getContentAsString();
        String groupId = JsonPath.read(triage, "$.items[0].id");

        mockMvc.perform(patch("/api/operations/triage/{groupId}/state", groupId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"SUPPRESSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUPPRESSED"));
        mockMvc.perform(get("/api/operations/scorecard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotedSignalGroups", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.weeklyTrend.direction").exists());
        mockMvc.perform(get("/api/operations/ai-calibration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groundTruthCoverageRate").isNumber());
    }

    @Test
    void exposesGuardedValidationBenchmarkReliabilityTrendAndOutcomeLearning() throws Exception {
        Cluster cluster = clusterRepository.save(Cluster.register("readiness-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));

        mockMvc.perform(get("/api/operations/validation-lab/live/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.safetyMode").value("LIVE_GUARDED"))
                .andExpect(jsonPath("$.requiredConfirmation").value("RUN LIVE VALIDATION"));

        mockMvc.perform(post("/api/operations/validation-lab/live/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clusterId":"%s","scenarioId":"failed-mount","ttlSeconds":300}
                                """.formatted(cluster.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executable").value(false))
                .andExpect(jsonPath("$.blockingReasons[0]").exists())
                .andExpect(jsonPath("$.plannedResources[0]").value("Pod/aiops-failed-mount"));

        mockMvc.perform(post("/api/operations/validation-lab/benchmarks"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("PASSED"))
                .andExpect(jsonPath("$.classificationAccuracy").value(100.0))
                .andExpect(jsonPath("$.evidenceCoverage").value(100.0))
                .andExpect(jsonPath("$.commandSafetyRate").value(100.0))
                .andExpect(jsonPath("$.releaseRecommendation").value("RELEASE_READY"));

        mockMvc.perform(get("/api/operations/validation-lab/benchmarks/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PASSED"))
                .andExpect(jsonPath("$.classificationAccuracy").value(100.0))
                .andExpect(jsonPath("$.evidenceCoverage").value(100.0))
                .andExpect(jsonPath("$.commandSafetyRate").value(100.0))
                .andExpect(jsonPath("$.releaseRecommendation").value("RELEASE_READY"));

        mockMvc.perform(get("/api/operations/reliability-trend").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceType").value("EVENT_BASED"))
                .andExpect(jsonPath("$.daily.length()", greaterThanOrEqualTo(30)));

        analysisRepository.save(analysis(cluster.id(), "default"));
        mockMvc.perform(post("/api/operations/reconcile")).andExpect(status().isOk());
        String incidents = mockMvc.perform(get("/api/incidents").param("clusterId", cluster.id().toString()))
                .andReturn().getResponse().getContentAsString();
        String incidentId = JsonPath.read(incidents, "$[0].id");
        mockMvc.perform(get("/api/operations/incidents/{incidentId}/remediation-learning", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.comparableSamples").value(0))
                .andExpect(jsonPath("$.evidenceNotice").exists());
    }

    @Test
    void cleanupRemovesExpiredSyncDataButRetainsLatestClusterSnapshot() throws Exception {
        Cluster cluster = clusterRepository.save(Cluster.register("cleanup-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));
        UUID oldJobId = UUID.randomUUID();
        UUID latestJobId = UUID.randomUUID();
        UUID oldSyncId = UUID.randomUUID();
        UUID latestSyncId = UUID.randomUUID();
        Instant old = Instant.now().minusSeconds(70L * 24 * 60 * 60);
        Instant recent = Instant.now().minusSeconds(60);
        insertJob(oldJobId, old);
        insertJob(latestJobId, recent);
        insertSync(oldSyncId, oldJobId, cluster.id(), old);
        insertSync(latestSyncId, latestJobId, cluster.id(), recent);
        jdbcTemplate.update("""
                insert into kubernetes_event_snapshots
                (id, cluster_id, sync_job_id, namespace, involved_kind, involved_name, reason, type, message,
                 event_time, count, collected_at)
                values (?, ?, ?, 'default', 'Pod', 'old-pod', 'BackOff', 'Warning', 'old event', ?, 1, ?)
                """, UUID.randomUUID(), cluster.id(), oldSyncId, Timestamp.from(old), Timestamp.from(old));
        jdbcTemplate.update("""
                insert into kubernetes_resource_snapshots
                (id, cluster_id, sync_job_id, namespace, resource_type, resource_name, resource_uid, status,
                 summary_json, raw_json, truncated, collected_at)
                values (?, ?, ?, 'default', 'Pod', 'old-pod', null, 'Running', '{}', null, false, ?)
                """, UUID.randomUUID(), cluster.id(), oldSyncId, Timestamp.from(old));

        mockMvc.perform(post("/api/settings/operations/cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventSnapshots", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.jobs", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.executed").value(true));

        org.assertj.core.api.Assertions.assertThat(countRows("sync_jobs", oldSyncId)).isZero();
        org.assertj.core.api.Assertions.assertThat(countRows("async_jobs", oldJobId)).isZero();
        org.assertj.core.api.Assertions.assertThat(countRows("sync_jobs", latestSyncId)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(countRows("async_jobs", latestJobId)).isEqualTo(1);
    }

    @Test
    void evaluatesReferencesPortsAndResilienceOnlyFromAvailableEvidence() throws Exception {
        Cluster cluster = clusterRepository.save(Cluster.register("policy-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));
        UUID jobId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        Instant now = Instant.now();
        insertJob(jobId, now);
        insertSync(syncId, jobId, cluster.id(), now);
        insertResource(cluster.id(), syncId, "Deployment", "web", "2/2", """
                {"availableReplicas":2,"desiredReplicas":2,"templateLabels":{"app":"web"},
                 "containers":[{"name":"web","image":"nginx:1.27","requests":{"cpu":"100m","memory":"64Mi"},
                 "limits":{"cpu":"500m","memory":"128Mi"},"hasReadinessProbe":true,"hasLivenessProbe":true,
                 "ports":[{"name":"http","containerPort":8080}]}],
                 "volumes":[{"name":"config","sourceKind":"ConfigMap","sourceName":"missing-config"}]}
                """);
        insertResource(cluster.id(), syncId, "Service", "web", "ClusterIP", """
                {"selector":{"app":"web"},"ports":[{"name":"http","port":80,"targetPort":"9090","protocol":"TCP"}]}
                """);
        insertResource(cluster.id(), syncId, "Endpoint", "web", "ACTIVE",
                "{\"readyAddresses\":1,\"notReadyAddresses\":0}");

        mockMvc.perform(post("/api/policies/evaluate").param("clusterId", cluster.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.policyId == 'REFERENCE_MISSING')].result", hasItem("FAIL")))
                .andExpect(jsonPath("$[?(@.policyId == 'PORT_MISMATCH')].result", hasItem("FAIL")))
                .andExpect(jsonPath("$[?(@.policyId == 'HPA_COVERAGE')].result", hasItem("WARN")))
                .andExpect(jsonPath("$[?(@.policyId == 'PDB_COVERAGE')].result", hasItem("WARN")));
    }

    @Test
    void autoResolvesIncidentOnlyAfterTwoDistinctHealthySnapshots() throws Exception {
        Cluster cluster = clusterRepository.save(Cluster.register("recovery-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));
        analysisRepository.save(analysis(cluster.id(), "default"));
        mockMvc.perform(post("/api/operations/reconcile")).andExpect(status().isOk());
        String incidents = mockMvc.perform(get("/api/incidents").param("clusterId", cluster.id().toString()))
                .andReturn().getResponse().getContentAsString();
        String incidentId = JsonPath.read(incidents, "$[0].id");
        Instant firstObservedAt = Instant.now().plusSeconds(2);
        UUID firstJobId = UUID.randomUUID();
        UUID firstSyncId = UUID.randomUUID();
        insertJob(firstJobId, firstObservedAt);
        insertSync(firstSyncId, firstJobId, cluster.id(), firstObservedAt);
        insertResource(cluster.id(), firstSyncId, "Pod", "configmap-db-pod", "Running", "{}", firstObservedAt);

        mockMvc.perform(post("/api/operations/reconcile")).andExpect(status().isOk());
        mockMvc.perform(get("/api/incidents/{incidentId}", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.state").value("MONITORING"))
                .andExpect(jsonPath("$.recovery.consecutiveHealthyCount").value(1))
                .andExpect(jsonPath("$.recovery.requiredHealthyCount").value(2));

        mockMvc.perform(post("/api/operations/reconcile")).andExpect(status().isOk());
        mockMvc.perform(get("/api/incidents/{incidentId}", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.state").value("MONITORING"))
                .andExpect(jsonPath("$.recovery.consecutiveHealthyCount").value(1));

        Instant secondObservedAt = firstObservedAt.plusSeconds(2);
        UUID secondJobId = UUID.randomUUID();
        UUID secondSyncId = UUID.randomUUID();
        insertJob(secondJobId, secondObservedAt);
        insertSync(secondSyncId, secondJobId, cluster.id(), secondObservedAt);
        insertResource(cluster.id(), secondSyncId, "Pod", "configmap-db-pod", "Running", "{}", secondObservedAt);

        mockMvc.perform(post("/api/operations/reconcile")).andExpect(status().isOk());
        mockMvc.perform(get("/api/incidents/{incidentId}", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.state").value("RESOLVED"))
                .andExpect(jsonPath("$.recovery.consecutiveHealthyCount").value(2))
                .andExpect(jsonPath("$.timeline[?(@.activityType == 'AUTO_RESOLVED')]").exists());
    }

    @Test
    void pagesLatestResourceInventoryWithServerSideFacets() throws Exception {
        Cluster cluster = clusterRepository.save(Cluster.register("paging-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));
        Instant observedAt = Instant.now();
        UUID jobId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        insertJob(jobId, observedAt);
        insertSync(syncId, jobId, cluster.id(), observedAt);
        for (int index = 0; index < 24; index++) {
            insertResource(cluster.id(), syncId, index < 20 ? "Pod" : "Service", "resource-" + index,
                    index == 0 ? "Pending" : "Running", "{}", observedAt.plusMillis(index));
        }

        mockMvc.perform(get("/api/clusters/{clusterId}/resources/page", cluster.id())
                        .param("page", "0").param("size", "20").param("namespace", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(24))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.problemCount").value(1))
                .andExpect(jsonPath("$.namespaceFacets[0].value").value("default"))
                .andExpect(jsonPath("$.resourceTypeFacets[?(@.value == 'Pod')].count", hasItem(20)));
    }

    @Test
    void incidentDetailExplainsBlastRadiusChangeCandidatesConfidenceAndSafeVerification() throws Exception {
        Cluster cluster = clusterRepository.save(Cluster.register("intelligence-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));
        Instant observedAt = Instant.now();
        UUID jobId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        insertJob(jobId, observedAt);
        insertSync(syncId, jobId, cluster.id(), observedAt);
        insertResource(cluster.id(), syncId, "Pod", "configmap-db-pod", "Pending", """
                {"phase":"Pending","labels":{"app":"web"},"ownerReferences":[{"kind":"ReplicaSet","name":"web-7c9"}],
                 "volumes":[{"name":"config","sourceKind":"ConfigMap","sourceName":"db-conf"}]}
                """, observedAt);
        insertResource(cluster.id(), syncId, "ReplicaSet", "web-7c9", "0/1", """
                {"ownerReferences":[{"kind":"Deployment","name":"web"}],"templateLabels":{"app":"web"}}
                """, observedAt);
        insertResource(cluster.id(), syncId, "Deployment", "web", "0/1",
                "{\"templateLabels\":{\"app\":\"web\"},\"ownerReferences\":[]}", observedAt);
        insertResource(cluster.id(), syncId, "ConfigMap", "db-conf", "ACTIVE",
                "{\"ownerReferences\":[]}", observedAt);
        insertResource(cluster.id(), syncId, "Service", "web", "ClusterIP",
                "{\"selector\":{\"app\":\"web\"},\"ownerReferences\":[]}", observedAt);
        insertResource(cluster.id(), syncId, "Endpoint", "web", "ACTIVE",
                "{\"readyAddresses\":0,\"ownerReferences\":[]}", observedAt);
        analysisRepository.save(analysis(cluster.id(), "default"));

        mockMvc.perform(post("/api/operations/reconcile")).andExpect(status().isOk());
        String incidents = mockMvc.perform(get("/api/incidents").param("clusterId", cluster.id().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String incidentId = JsonPath.read(incidents, "$[0].id");
        jdbcTemplate.update("""
                insert into incident_evidence
                (id, incident_id, evidence_key, evidence_type, source_ref, summary, factual, occurred_at)
                values (?, ?, ?, 'KUBERNETES_EVENT', 'event:FailedMount', 'ConfigMap db-conf was not found', true, ?)
                """, UUID.randomUUID(), UUID.fromString(incidentId), UUID.randomUUID().toString(),
                Timestamp.from(observedAt));
        jdbcTemplate.update("""
                insert into resource_change_events
                (id, cluster_id, namespace, resource_kind, resource_name, change_type, previous_status,
                 current_status, previous_hash, current_hash, summary, detected_at)
                values (?, ?, 'default', 'Deployment', 'web', 'UPDATED', '1/1', '0/1', 'before', 'after',
                        'Deployment template changed', ?)
                """, UUID.randomUUID(), cluster.id(), Timestamp.from(observedAt.minusSeconds(60)));

        mockMvc.perform(get("/api/incidents/{incidentId}", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intelligence.correlation.impactedResourceCount", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.intelligence.correlation.nodes[?(@.resourceKind == 'Deployment')].resourceName",
                        hasItem("web")))
                .andExpect(jsonPath("$.intelligence.correlation.edges[?(@.relation == 'OWNED_BY')]").exists())
                .andExpect(jsonPath("$.intelligence.changeCandidates[0].resourceName").value("web"))
                .andExpect(jsonPath("$.intelligence.changeCandidates[0].relevanceScore", greaterThanOrEqualTo(60)))
                .andExpect(jsonPath("$.intelligence.confidence.factualEvidenceCount").value(1))
                .andExpect(jsonPath("$.intelligence.confidence.freshness").value("FRESH"))
                .andExpect(jsonPath("$.intelligence.verificationPlan[0].destructive").value(false))
                .andExpect(jsonPath("$.intelligence.verificationPlan[0].command").value(
                        "kubectl describe pod/configmap-db-pod -n default"));
    }

    @Test
    void regressionCertificationPersistsDetailedSafetyAssertions() throws Exception {
        String body = mockMvc.perform(post("/api/analysis-regression/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.passedCases").value(50))
                .andExpect(jsonPath("$.totalCases").value(50))
                .andExpect(jsonPath("$.cases.length()").value(50))
                .andExpect(jsonPath("$.cases[?(@.caseId == 'storage-configmap')].status", hasItem("PASSED")))
                .andExpect(jsonPath("$.cases[?(@.caseId == 'startup-crashloop')].status", hasItem("PASSED")))
                .andExpect(jsonPath("$.cases[?(@.caseId == 'image-pull-auth')].status", hasItem("PASSED")))
                .andExpect(jsonPath("$.cases[?(@.caseId == 'traffic-target-port')].status", hasItem("PASSED")))
                .andExpect(jsonPath("$.cases[?(@.caseId == 'probe-startup')].status", hasItem("PASSED")))
                .andExpect(jsonPath("$.cases[?(@.caseId == 'storage-pvc')].status", hasItem("PASSED")))
                .andExpect(jsonPath("$.cases[?(@.caseId == 'capacity-oom')].status", hasItem("PASSED")))
                .andExpect(jsonPath("$.cases[?(@.caseId == 'rollout-rollback')].status", hasItem("PASSED")))
                .andExpect(jsonPath("$.cases[?(@.caseId == 'unknown-injection')].status", hasItem("PASSED")))
                .andExpect(jsonPath("$.cases[?(@.caseId == 'unknown-no-evidence')].status", hasItem("PASSED")))
                .andReturn().getResponse().getContentAsString();
        String runId = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/analysis-regression/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineVersion").value("k8s-analysis-contract-v1"))
                .andExpect(jsonPath("$.cases[0].assertions.length()", greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.cases[0].failures.length()").value(0));
    }

    @Test
    void noisePolicySuppressesLowSignalAndCanBeRemoved() throws Exception {
        Cluster cluster = clusterRepository.save(Cluster.register("noise-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));
        String created = mockMvc.perform(post("/api/operations/noise-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"qa maintenance",
                                  "clusterId":"%s",
                                  "namespacePattern":"nginx",
                                  "severityFloor":"HIGH",
                                  "repeatThreshold":5,
                                  "snoozeUntil":"%s",
                                  "enabled":true
                                }
                                """.formatted(cluster.id(), Instant.now().plusSeconds(3600))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severityFloor").value("HIGH"))
                .andReturn().getResponse().getContentAsString();
        String policyId = JsonPath.read(created, "$.id");

        operationsService.ingestWatchSignal(new WatchSignal(UUID.randomUUID(), cluster.id(), cluster.name(),
                "nginx", "Pod", "web-1", "MODIFIED", "BackOff", "Warning",
                "temporary restart during maintenance", Instant.now()));

        mockMvc.perform(get("/api/operations/triage").param("clusterId", cluster.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].state").value("SUPPRESSED"))
                .andExpect(jsonPath("$.suppressedSignals").value(1));
        mockMvc.perform(delete("/api/operations/noise-policies/{policyId}", policyId))
                .andExpect(status().isNoContent());
    }

    @Test
    void closedLoopObservationUsesLatestSnapshotAndGeneratesFactualPostmortem() throws Exception {
        Cluster cluster = clusterRepository.save(Cluster.register("closed-loop-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));
        operationsService.ingestWatchSignal(new WatchSignal(UUID.randomUUID(), cluster.id(), cluster.name(),
                "default", "Pod", "api", "MODIFIED", "CrashLoopBackOff", "Pending",
                "container exited with code 1", Instant.now()));
        String incidents = mockMvc.perform(get("/api/incidents").param("clusterId", cluster.id().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String incidentId = JsonPath.read(incidents, "$[0].id");

        UUID jobId = UUID.randomUUID();
        UUID syncId = UUID.randomUUID();
        insertJob(jobId, Instant.now());
        insertSync(syncId, jobId, cluster.id(), Instant.now());
        insertResource(cluster.id(), syncId, "Pod", "api", "Running", "{\"phase\":\"Running\"}");

        String observation = mockMvc.perform(post("/api/incidents/{incidentId}/remediation-observations", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observationSeconds\":30}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("OBSERVING"))
                .andReturn().getResponse().getContentAsString();
        String observationId = JsonPath.read(observation, "$.id");
        mockMvc.perform(post("/api/remediation-observations/{observationId}/evaluate", observationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.latestJson").exists());

        mockMvc.perform(post("/api/incidents/{incidentId}/postmortem", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.rootCause").exists())
                .andExpect(jsonPath("$.prevention.length()", greaterThanOrEqualTo(3)));
    }

    @Test
    void exposesContinuityAndBlocksOrPassesAiCandidateFromExplicitThresholds() throws Exception {
        Cluster cluster = clusterRepository.save(Cluster.register("continuity-" + UUID.randomUUID(), "test",
                ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test"));
        jdbcTemplate.update("""
                insert into watch_continuity (cluster_id, pod_resource_version, event_resource_version,
                continuity_state, gap_signal_count, last_reconciled_at, last_error, updated_at)
                values (?, '101', '202', 'RECONCILED', 2, ?, null, ?)
                """, cluster.id(), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));

        mockMvc.perform(get("/api/operations/watch/continuity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.clusterId == '%s')].gapSignalCount".formatted(cluster.id()), hasItem(2)));
        mockMvc.perform(post("/api/operations/ai-release-gates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateVersion":"candidate-test",
                                  "baselineVersion":"k8s-analysis-contract-v1",
                                  "minimumRegressionScore":90,
                                  "minimumGroundTruthSamples":0,
                                  "minimumVerifiedAccuracy":0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("PASSED"))
                .andExpect(jsonPath("$.regressionScore", greaterThanOrEqualTo(90.0)));

        mockMvc.perform(post("/api/operations/ai-release-gates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "candidateVersion":"candidate-insufficient-ground-truth",
                                  "baselineVersion":"k8s-analysis-contract-v1",
                                  "minimumRegressionScore":90,
                                  "minimumGroundTruthSamples":1000,
                                  "minimumVerifiedAccuracy":100
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("BLOCKED"))
                .andExpect(jsonPath("$.reasons.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void exposesFleetShiftBriefingAndSafeValidationLab() throws Exception {
        mockMvc.perform(get("/api/operations/fleet-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(get("/api/operations/shift-briefing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posture").exists())
                .andExpect(jsonPath("$.beginnerSummary").exists())
                .andExpect(jsonPath("$.immediateActions").isArray());

        mockMvc.perform(get("/api/operations/validation-lab/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[?(@.id == 'port-mismatch')].safetyMode", hasItem("VIRTUAL_SAFE")));

        mockMvc.perform(post("/api/operations/validation-lab/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.mode").value("VIRTUAL_SAFE"))
                .andExpect(jsonPath("$.passedCases").value(50))
                .andExpect(jsonPath("$.cases[?(@.scenarioId == 'capacity-oom')].status", hasItem("PASSED")));
    }

    private void insertJob(UUID jobId, Instant createdAt) {
        jdbcTemplate.update("insert into async_jobs (id, type, status, created_at, started_at, completed_at) "
                        + "values (?, 'CLUSTER_SYNC', 'SUCCEEDED', ?, ?, ?)",
                jobId, Timestamp.from(createdAt), Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private void insertSync(UUID syncId, UUID jobId, UUID clusterId, Instant createdAt) {
        jdbcTemplate.update("""
                insert into sync_jobs
                (id, async_job_id, cluster_id, sync_type, status, requested_by, resource_count, event_count,
                 started_at, completed_at, created_at)
                values (?, ?, ?, 'MANUAL_CLUSTER', 'SUCCEEDED', 'test', 1, 1, ?, ?, ?)
                """, syncId, jobId, clusterId, Timestamp.from(createdAt), Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    private void insertResource(UUID clusterId, UUID syncId, String kind, String name, String status,
                                String summaryJson) {
        insertResource(clusterId, syncId, kind, name, status, summaryJson, Instant.now());
    }

    private void insertResource(UUID clusterId, UUID syncId, String kind, String name, String status,
                                String summaryJson, Instant collectedAt) {
        jdbcTemplate.update("""
                insert into kubernetes_resource_snapshots
                (id, cluster_id, sync_job_id, namespace, resource_type, resource_name, resource_uid, status,
                 summary_json, raw_json, truncated, collected_at)
                values (?, ?, ?, 'default', ?, ?, null, ?, ?, null, false, ?)
                """, UUID.randomUUID(), clusterId, syncId, kind, name, status, summaryJson, Timestamp.from(collectedAt));
    }

    private long countRows(String table, UUID id) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + table + " where id=?", Long.class, id);
        return count == null ? 0 : count;
    }

    private AnalysisSession analysis(UUID clusterId, String namespace) {
        return AnalysisSession.succeeded(clusterId, null, namespace, """
                {
                  "schemaVersion":"analysis-result.v1",
                  "severity":"HIGH",
                  "summary":"FailedMount was detected",
                  "issueGroups":[{
                    "title":"FailedMount · Pod/configmap-db-pod",
                    "severity":"HIGH",
                    "category":"STORAGE_CONFIG",
                    "summary":"ConfigMap db-conf was not found",
                    "recommendation":"Verify ConfigMap and volume references"
                  }]
                }
                """, "test");
    }
}
