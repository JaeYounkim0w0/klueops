package io.strato.aiops.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import io.strato.aiops.application.port.out.AiAnalysisPort;
import io.strato.aiops.application.port.out.AiAnalysisInvalidResponseException;
import io.strato.aiops.application.port.out.KubernetesAccessReviewResult;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesDeploymentRevision;
import io.strato.aiops.application.port.out.KubernetesStateInventory;
import io.strato.aiops.application.port.out.KubernetesStateSyncPort;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnosticsPort;
import io.strato.aiops.application.port.out.KubernetesPodLogs;
import io.strato.aiops.application.port.out.KubernetesMutationPort;
import io.strato.aiops.application.port.out.KubernetesMutationResult;
import io.strato.aiops.application.port.out.KubernetesRollbackPlan;
import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AnalysisApiTest {

    @Autowired
    private MockMvc mockMvc;

    /** AnalysisApiTest의 analyzesApplicationAndStoresAnalysisSession 처리의 핵심 작업 흐름을 실행한다. */
    @Test
    void analyzesApplicationAndStoresAnalysisSession() throws Exception {
        String clusterId = registerCluster("analysis-api-cluster-" + UUID.randomUUID());
        String applicationId = deployDockerApplication(clusterId, "analysis-api-service-" + UUID.randomUUID());

        String response = mockMvc.perform(post("/api/analysis/applications/{applicationId}", applicationId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.clusterId").value(clusterId))
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.schemaVersion").value("analysis-result.v1"))
                .andExpect(jsonPath("$.resultJson", containsString("test-analysis")))
                .andExpect(jsonPath("$.resultJson", containsString("sectioned-ai-analysis")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String analysisId = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/analysis/{analysisId}", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(analysisId));

        mockMvc.perform(get("/api/analysis/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", notNullValue()));
    }

    /** AnalysisApiTest의 analyzesNamespace 처리의 핵심 작업 흐름을 실행한다. */
    @Test
    void analyzesNamespace() throws Exception {
        String clusterId = registerCluster("namespace-analysis-cluster-" + UUID.randomUUID());

        mockMvc.perform(post("/api/analysis/namespaces/{namespace}", "default")
                        .param("clusterId", clusterId)
                        .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.clusterId").value(clusterId))
                .andExpect(jsonPath("$.namespace").value("default"))
                .andExpect(jsonPath("$.locale").value("ko-KR"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultJson", containsString("\"analysisComparison\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"port-mismatch\"")))
                .andExpect(jsonPath("$.resultJson", containsString("targetPort=8080")))
                .andExpect(jsonPath("$.resultJson", containsString("\"logIntelligence\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"port-startup\"")))
                .andExpect(jsonPath("$.resultJson", containsString("bind() to 0.0.0.0:80 failed")))
                .andExpect(jsonPath("$.resultJson", containsString("LOG_INTELLIGENCE_SIGNAL")))
                .andExpect(jsonPath("$.resultJson", containsString("\"actionRecommendations\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"analysisQuality\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"collection\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"status\":\"PARTIAL\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"source\":\"events.k8s.io\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"locale\":\"ko-KR\"")))
                .andExpect(jsonPath("$.resultJson", containsString("privileged port 사용 방식 수정")))
                .andExpect(jsonPath("$.resultJson", containsString("CHANGE_REQUIRES_REVIEW")))
                .andExpect(jsonPath("$.resultJson", containsString("\"hasPrevious\":false")));
    }

    /** AnalysisApiTest의 filtersAnalysisHistoryByClusterAndNamespace 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void filtersAnalysisHistoryByClusterAndNamespace() throws Exception {
        String firstClusterId = registerCluster("history-filter-cluster-a-" + UUID.randomUUID());
        String secondClusterId = registerCluster("history-filter-cluster-b-" + UUID.randomUUID());

        mockMvc.perform(post("/api/analysis/namespaces/{namespace}", "default")
                        .param("clusterId", firstClusterId))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/analysis/namespaces/{namespace}", "kube-system")
                        .param("clusterId", secondClusterId))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/analysis/history")
                        .param("clusterId", firstClusterId)
                        .param("namespace", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[*].clusterId", everyItem(org.hamcrest.Matchers.is(firstClusterId))))
                .andExpect(jsonPath("$[*].namespace", everyItem(org.hamcrest.Matchers.is("default"))));

        mockMvc.perform(get("/api/analysis/history")
                        .param("clusterId", secondClusterId)
                        .param("namespace", "kube-system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[*].clusterId", everyItem(org.hamcrest.Matchers.is(secondClusterId))))
                .andExpect(jsonPath("$[*].namespace", everyItem(org.hamcrest.Matchers.is("kube-system"))));
    }

    /** AnalysisApiTest의 deletesAnalysisSession 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Test
    void deletesAnalysisSession() throws Exception {
        String clusterId = registerCluster("analysis-delete-cluster-" + UUID.randomUUID());
        String analysisId = createNamespaceAnalysis(clusterId);

        mockMvc.perform(delete("/api/analysis/{analysisId}", analysisId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/analysis/{analysisId}", analysisId))
                .andExpect(status().isNotFound());
    }

    /** AnalysisApiTest의 comparesAnalysisWithPreviousSameScope 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void comparesAnalysisWithPreviousSameScope() throws Exception {
        String clusterId = registerCluster("namespace-analysis-compare-cluster-" + UUID.randomUUID());

        mockMvc.perform(post("/api/analysis/namespaces/{namespace}", "default")
                        .param("clusterId", clusterId))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/analysis/namespaces/{namespace}", "default")
                        .param("clusterId", clusterId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultJson", containsString("\"analysisComparison\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"hasPrevious\":true")))
                .andExpect(jsonPath("$.resultJson", containsString("\"previousAnalysisId\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"status\":\"REUSED\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"reusedSections\":3")));
    }

    /** AnalysisApiTest의 doesNotReuseNaturalLanguageSectionsAcrossLocales 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void doesNotReuseNaturalLanguageSectionsAcrossLocales() throws Exception {
        String clusterId = registerCluster("namespace-analysis-locale-cache-" + UUID.randomUUID());

        mockMvc.perform(post("/api/analysis/namespaces/{namespace}", "default")
                        .param("clusterId", clusterId)
                        .header("Accept-Language", "en-US"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locale").value("en-US"));

        mockMvc.perform(post("/api/analysis/namespaces/{namespace}", "default")
                        .param("clusterId", clusterId)
                        .header("Accept-Language", "ko-KR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locale").value("ko-KR"))
                .andExpect(jsonPath("$.resultJson", containsString("\"reusedSections\":0")));
    }

    /** AnalysisApiTest의 startsNamespaceAnalysisJobAndReturnsResultByJobId 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void startsNamespaceAnalysisJobAndReturnsResultByJobId() throws Exception {
        String clusterId = registerCluster("namespace-analysis-job-cluster-" + UUID.randomUUID());

        String response = mockMvc.perform(post("/api/analysis/namespaces/{namespace}/jobs", "default")
                        .param("clusterId", clusterId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andExpect(jsonPath("$.analysisId", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String jobId = JsonPath.read(response, "$.jobId");
        String analysisId = JsonPath.read(response, "$.analysisId");

        waitForJob(jobId);

        mockMvc.perform(get("/api/analysis/jobs/{jobId}/result", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(analysisId))
                .andExpect(jsonPath("$.asyncJobId").value(jobId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultJson", containsString("test-analysis")));
    }

    /** AnalysisApiTest의 analyzesCluster 처리의 핵심 작업 흐름을 실행한다. */
    @Test
    void analyzesCluster() throws Exception {
        String clusterId = registerCluster("cluster-analysis-cluster-" + UUID.randomUUID());

        mockMvc.perform(post("/api/analysis/clusters/{clusterId}", clusterId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.clusterId").value(clusterId))
                .andExpect(jsonPath("$.namespace").doesNotExist())
                .andExpect(jsonPath("$.applicationId").doesNotExist())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultJson", containsString("\"analysisMode\":\"cluster-sectioned\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"name\":\"cluster-root-cause\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"name\":\"cluster-risk-posture\"")))
                .andExpect(jsonPath("$.resultJson", containsString("\"name\":\"cluster-runbook-operations\"")))
                .andExpect(jsonPath("$.resultJson", containsString("kubectl get pods -A")));
    }

    /** AnalysisApiTest의 clusterAnalysisFallsBackToKubernetesEvidenceWhenAiTimesOut 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void clusterAnalysisFallsBackToKubernetesEvidenceWhenAiTimesOut() throws Exception {
        String clusterId = registerCluster("cluster-timeout-" + UUID.randomUUID());

        mockMvc.perform(post("/api/analysis/clusters/{clusterId}", clusterId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.clusterId").value(clusterId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.schemaVersion").value("analysis-result.v1"))
                .andExpect(jsonPath("$.resultJson", containsString("cluster-sectioned-partial-fallback")))
                .andExpect(jsonPath("$.resultJson", containsString("\"status\":\"FALLBACK\"")))
                .andExpect(jsonPath("$.resultJson", containsString("cluster-sample-pod")));
    }

    /** AnalysisApiTest의 clusterAnalysisFallsBackToKubernetesEvidenceWhenAiResponseIsInvalid 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void clusterAnalysisFallsBackToKubernetesEvidenceWhenAiResponseIsInvalid() throws Exception {
        String clusterId = registerCluster("cluster-invalid-response-" + UUID.randomUUID());

        mockMvc.perform(post("/api/analysis/clusters/{clusterId}", clusterId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultJson", containsString("cluster-sectioned-partial-fallback")))
                .andExpect(jsonPath("$.resultJson", containsString("AI response did not satisfy the analysis schema")));
    }

    /** AnalysisApiTest의 clusterAnalysisFallsBackWhenAiResponseHasNoOperationalContent 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void clusterAnalysisFallsBackWhenAiResponseHasNoOperationalContent() throws Exception {
        String clusterId = registerCluster("cluster-empty-response-" + UUID.randomUUID());

        mockMvc.perform(post("/api/analysis/clusters/{clusterId}", clusterId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultJson", containsString("cluster-sectioned-partial-fallback")))
                .andExpect(jsonPath("$.resultJson", containsString("contained no operational evidence or actions")))
                .andExpect(jsonPath("$.resultJson", containsString("kubectl get events -A")));
    }

    /** AnalysisApiTest의 returnsNamespaceDiagnosticsPreview 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void returnsNamespaceDiagnosticsPreview() throws Exception {
        String clusterId = registerCluster("namespace-diagnostics-cluster-" + UUID.randomUUID());

        mockMvc.perform(get("/api/analysis/namespaces/{namespace}/diagnostics", "default")
                        .param("clusterId", clusterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterId").value(clusterId))
                .andExpect(jsonPath("$.namespace").value("default"))
                .andExpect(jsonPath("$.resourceCount").value(2))
                .andExpect(jsonPath("$.eventCount").value(1))
                .andExpect(jsonPath("$.podLogCount").value(1))
                .andExpect(jsonPath("$.resourceKinds[0].resourceType").value("Pod"))
                .andExpect(jsonPath("$.resourceKinds[0].count").value(1))
                .andExpect(jsonPath("$.riskForecast.overallRisk").value(86))
                .andExpect(jsonPath("$.riskForecast.riskLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.riskForecast.predictions[0].category").value("port-startup"))
                .andExpect(jsonPath("$.changeTimeline").isArray())
                .andExpect(jsonPath("$.runbookActions[0].title").value("컨테이너 포트 바인딩 권한 오류"))
                .andExpect(jsonPath("$.podLogSources[0].podName").value("sample-pod"));
    }

    /** AnalysisApiTest의 returnsResourceLogsForAnalysisTroubleshooting 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void returnsResourceLogsForAnalysisTroubleshooting() throws Exception {
        String clusterId = registerCluster("resource-logs-cluster-" + UUID.randomUUID());

        mockMvc.perform(get("/api/analysis/namespaces/{namespace}/resources/{resourceType}/{resourceName}/logs",
                        "default", "Deployment", "sample-deployment")
                        .param("clusterId", clusterId)
                        .param("tailLines", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterId").value(clusterId))
                .andExpect(jsonPath("$.namespace").value("default"))
                .andExpect(jsonPath("$.podName").value("Deployment/sample-deployment"))
                .andExpect(jsonPath("$.tailLines").value(50))
                .andExpect(jsonPath("$.containers[0].containerName").value("app"))
                .andExpect(jsonPath("$.containers[0].log", containsString("sample log")));
    }

    /** AnalysisApiTest의 executesReadOnlyAnalysisCommandAndStoresResult 처리의 핵심 작업 흐름을 실행한다. */
    @Test
    void executesReadOnlyAnalysisCommandAndStoresResult() throws Exception {
        String clusterId = registerCluster("analysis-command-cluster-" + UUID.randomUUID());
        String analysisId = createNamespaceAnalysis(clusterId);

        mockMvc.perform(post("/api/analysis/{analysisId}/commands", analysisId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command": "kubectl describe pod sample-pod -n default"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.analysisId").value(analysisId))
                .andExpect(jsonPath("$.clusterId").value(clusterId))
                .andExpect(jsonPath("$.safety").value("READ_ONLY"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.stdoutText", containsString("Pod/sample-pod")));

        mockMvc.perform(get("/api/analysis/{analysisId}/commands", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].command").value("kubectl describe pod sample-pod -n default"))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"));
    }

    /** AnalysisApiTest의 executesSupportedSafeMutationOnlyWithConfirmation 처리의 핵심 작업 흐름을 실행한다. */
    @Test
    void executesSupportedSafeMutationOnlyWithConfirmation() throws Exception {
        String clusterId = registerCluster("analysis-command-change-cluster-" + UUID.randomUUID());
        String analysisId = createNamespaceAnalysis(clusterId);

        mockMvc.perform(post("/api/analysis/{analysisId}/commands/preview", analysisId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command": "kubectl rollout restart deployment/sample-deployment -n default"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.safety").value("CHANGE"))
                .andExpect(jsonPath("$.executable").value(true))
                .andExpect(jsonPath("$.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.confirmationText").value("APPLY default/sample-deployment"))
                .andExpect(jsonPath("$.rbacAllowed").value(true))
                .andExpect(jsonPath("$.dryRunPassed").value(true))
                .andExpect(jsonPath("$.rollbackGuardPassed").value(true))
                .andExpect(jsonPath("$.guardMessage", containsString("RBAC and dry-run guard passed")))
                .andExpect(jsonPath("$.dryRunSummary", containsString("dryRun=true")));

        mockMvc.perform(post("/api/analysis/{analysisId}/commands", analysisId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command": "kubectl rollout restart deployment/sample-deployment -n default"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.safety").value("CHANGE"))
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.reason", containsString("확인 문구")));

        mockMvc.perform(post("/api/analysis/{analysisId}/commands", analysisId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command": "kubectl rollout restart deployment/sample-deployment -n default",
                                  "confirmText": "APPLY default/sample-deployment"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.safety").value("CHANGE"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.stdoutText", containsString("rollout-restart")))
                .andExpect(jsonPath("$.stdoutText", containsString("Deployment/sample-deployment")));
    }

    /** AnalysisApiTest의 executesRollbackOnlyWhenRevisionGuardAndConfirmationPass 처리의 핵심 작업 흐름을 실행한다. */
    @Test
    void executesRollbackOnlyWhenRevisionGuardAndConfirmationPass() throws Exception {
        String clusterId = registerCluster("analysis-command-rollback-cluster-" + UUID.randomUUID());
        String analysisId = createNamespaceAnalysis(clusterId);

        mockMvc.perform(post("/api/analysis/{analysisId}/commands/preview", analysisId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command": "kubectl rollout undo deployment/sample-deployment -n default --to-revision=2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.safety").value("CHANGE"))
                .andExpect(jsonPath("$.executable").value(true))
                .andExpect(jsonPath("$.rollbackGuardPassed").value(true))
                .andExpect(jsonPath("$.confirmationText").value("ROLLBACK default/sample-deployment TO REVISION 2"));

        mockMvc.perform(post("/api/analysis/{analysisId}/commands", analysisId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command": "kubectl rollout undo deployment/sample-deployment -n default --to-revision=2",
                                  "confirmText": "ROLLBACK default/sample-deployment TO REVISION 2"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.safety").value("CHANGE"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.stdoutText", containsString("rollback")))
                .andExpect(jsonPath("$.stdoutText", containsString("Deployment/sample-deployment")));
    }

    /** AnalysisApiTest의 blocksRollbackWithoutExplicitRevisionAtPreview 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void blocksRollbackWithoutExplicitRevisionAtPreview() throws Exception {
        String clusterId = registerCluster("analysis-command-rollback-guard-cluster-" + UUID.randomUUID());
        String analysisId = createNamespaceAnalysis(clusterId);

        mockMvc.perform(post("/api/analysis/{analysisId}/commands/preview", analysisId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command": "kubectl rollout undo deployment/sample-deployment -n default"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.safety").value("CHANGE"))
                .andExpect(jsonPath("$.executable").value(false))
                .andExpect(jsonPath("$.requiresConfirmation").value(false))
                .andExpect(jsonPath("$.rollbackGuardPassed").value(false))
                .andExpect(jsonPath("$.guardMessage", containsString("지원하지 않는 변경 명령")));
    }

    /** AnalysisApiTest의 blocksMutatingAnalysisCommandAndPersistsWorkflowState 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void blocksMutatingAnalysisCommandAndPersistsWorkflowState() throws Exception {
        String clusterId = registerCluster("analysis-command-block-cluster-" + UUID.randomUUID());
        String analysisId = createNamespaceAnalysis(clusterId);

        mockMvc.perform(post("/api/analysis/{analysisId}/commands", analysisId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "command": "kubectl delete pod sample-pod -n default"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.safety").value("DESTRUCTIVE"))
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.reason", containsString("차단")));

        mockMvc.perform(post("/api/analysis/{analysisId}/workflow/{issueGroupId}", analysisId, "IG-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "INVESTIGATING",
                                  "note": "checking evidence"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId))
                .andExpect(jsonPath("$.issueGroupId").value("IG-1"))
                .andExpect(jsonPath("$.status").value("INVESTIGATING"));

        mockMvc.perform(get("/api/analysis/{analysisId}/workflow", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].issueGroupId").value("IG-1"))
                .andExpect(jsonPath("$[0].status").value("INVESTIGATING"));
    }

    /** AnalysisApiTest의 deployDockerApplication 처리에 필요한 업무 로직을 수행한다. */
    private String deployDockerApplication(String clusterId, String name) throws Exception {
        String response = mockMvc.perform(post("/api/applications/deploy/docker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clusterId": "%s",
                                  "namespace": "default",
                                  "name": "%s",
                                  "image": "registry.example.com/api:1.0.0"
                                }
                                """.formatted(clusterId, name)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.application.id");
    }

    /** AnalysisApiTest의 registerCluster 처리에 필요한 데이터를 생성하거나 저장한다. */
    private String registerCluster(String name) throws Exception {
        String response = mockMvc.perform(post("/api/clusters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "cluster for analysis api test",
                                  "environment": "DEV",
                                  "provider": "KIND",
                                  "region": "local",
                                  "credentialType": "KUBECONFIG",
                                  "kubeconfig": "apiVersion: v1\\nkind: Config\\nclusters: []",
                                  "syncSettings": {
                                    "autoSyncEnabled": true,
                                    "syncIntervalSeconds": 300
                                  }
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    /** AnalysisApiTest의 createNamespaceAnalysis 처리에 필요한 데이터를 생성하거나 저장한다. */
    private String createNamespaceAnalysis(String clusterId) throws Exception {
        String response = mockMvc.perform(post("/api/analysis/namespaces/{namespace}", "default")
                        .param("clusterId", clusterId))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    /** AnalysisApiTest의 waitForJob 처리에 필요한 업무 로직을 수행한다. */
    private void waitForJob(String jobId) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            String response = mockMvc.perform(get("/api/jobs/{jobId}", jobId))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            String status = JsonPath.read(response, "$.status");
            if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Job did not complete: " + jobId);
    }

    @TestConfiguration
    static class FakeAiAnalysisConfig {

        /** FakeAiAnalysisConfig의 aiAnalysisPort 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        @Primary
        AiAnalysisPort aiAnalysisPort() {
            return context -> {
                if (context.contains("clusterName=cluster-timeout-")) {
                    throw new RuntimeException("timeout");
                }
                if (context.contains("clusterName=cluster-invalid-response-")) {
                    throw new AiAnalysisInvalidResponseException("Ollama analysis response was not valid JSON");
                }
                if (context.contains("clusterName=cluster-empty-response-")) {
                    return """
                            {
                              "schemaVersion":"analysis-result.v1",
                              "summary":"Review findings and verification commands before taking action.",
                              "severity":"INFO",
                              "confidence":0.35,
                              "riskScore":0,
                              "findings":[],
                              "rootCauses":[],
                              "logAnalysis":[],
                              "recommendations":[],
                              "changeTimeline":[],
                              "runbookActions":[],
                              "nextActions":[],
                              "verificationCommands":[],
                              "evidence":{"resources":[],"events":[],"logs":[]},
                              "performance":{"summary":"Metrics unavailable","bottlenecks":[],"improvements":[]},
                              "scaling":{"summary":"Needs verification","scaleUpCandidates":[],"hpaRecommendations":[],"capacityNotes":[]},
                              "riskForecast":{"summary":"No signals","predictions":[]},
                              "operationsGuide":{"summary":"Review findings and verification commands before taking action.","shortTerm":[],"mediumTerm":[],"questionsForOperator":[]}
                            }
                            """;
                }
                if (context.contains("analysisSection=cluster-root-cause")) {
                    return """
                            {
                              "summary":"test-analysis",
                              "severity":"MEDIUM",
                              "riskScore":42,
                              "findings":[{"title":"cluster finding","evidence":["Pending pod"]}],
                              "rootCauses":[{"title":"cluster root cause","evidence":["FailedScheduling"]}],
                              "evidence":{"resources":["default Pod/cluster-sample-pod"],"events":[],"logs":[]}
                            }
                            """;
                }
                if (context.contains("analysisSection=cluster-risk-posture")) {
                    return """
                            {
                              "performance":{"summary":"Metrics unavailable","bottlenecks":[],"improvements":["Set requests and limits"]},
                              "scaling":{"summary":"Check scheduling first","scaleUpCandidates":[],"hpaRecommendations":[],"capacityNotes":["Review node capacity"]},
                              "riskForecast":{"summary":"Scheduling risk","predictions":[{"category":"stability","severity":"MEDIUM"}]},
                              "changeTimeline":[]
                            }
                            """;
                }
                if (context.contains("analysisSection=cluster-runbook-operations")) {
                    return """
                            {
                              "runbookActions":[],
                              "recommendations":[],
                              "operationsGuide":{"summary":"Verify first","shortTerm":["Inspect pending pods"],"mediumTerm":[],"questionsForOperator":[]},
                              "nextActions":[{"priority":"P1","action":"Inspect pending pods"}],
                              "verificationCommands":["kubectl get pods -A"]
                            }
                            """;
                }
                return """
                        {
                          "schemaVersion": "v1",
                          "summary": "test-analysis",
                          "severity": "INFO",
                          "confidence": 0.50,
                          "rootCauses": [],
                          "recommendations": [],
                          "verificationCommands": ["kubectl get pods -A"],
                          "evidence": {}
                        }
                        """;
            };
        }

        /** FakeAiAnalysisConfig의 kubernetesNamespaceDiagnosticsPort 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        @Primary
        KubernetesNamespaceDiagnosticsPort kubernetesNamespaceDiagnosticsPort() {
            return new KubernetesNamespaceDiagnosticsPort() {
                /** 익명 구현체의 collectNamespaceDiagnostics 처리의 핵심 작업 흐름을 실행한다. */
                @Override
                public KubernetesNamespaceDiagnostics collectNamespaceDiagnostics(KubernetesConnectionCredential credential, String namespace) {
                    return new KubernetesNamespaceDiagnostics(
                            List.of(
                                    new KubernetesNamespaceDiagnostics.DiagnosticResource(
                                            namespace,
                                            "Pod",
                                            "sample-pod",
                                            "Running",
                                            """
                                                    {
                                                      "phase":"Running",
                                                      "restartCount":0,
                                                      "labels":{"app":"sample"},
                                                      "containers":[
                                                        {
                                                          "name":"app",
                                                          "image":"nginx",
                                                          "ports":[{"name":"http","containerPort":80,"protocol":"TCP"}]
                                                        }
                                                      ]
                                                    }
                                                    """
                                    ),
                                    new KubernetesNamespaceDiagnostics.DiagnosticResource(
                                            namespace,
                                            "Service",
                                            "sample-service",
                                            "ClusterIP",
                                            """
                                                    {
                                                      "type":"ClusterIP",
                                                      "selector":{"app":"sample"},
                                                      "ports":[{"name":"http","port":80,"targetPort":"8080","protocol":"TCP"}]
                                                    }
                                                    """
                                    )
                            ),
                            List.of(new KubernetesNamespaceDiagnostics.DiagnosticEvent(
                                    namespace,
                                    "Pod",
                                    "sample-pod",
                                    "Started",
                                    "Normal",
                                    "Started container sample",
                                    Instant.now(),
                                    1
                            )),
                            List.of(new KubernetesNamespaceDiagnostics.DiagnosticPodLog(
                                    namespace,
                                    "sample-pod",
                                    "app",
                                    """
                                            Previous terminated container log:
                                            2026/07/09 15:21:44 [emerg] 1#1: bind() to 0.0.0.0:80 failed (13: Permission denied)
                                            nginx: [emerg] bind() to 0.0.0.0:80 failed (13: Permission denied)
                                            """,
                                    true
                            )),
                            Instant.now(),
                            List.of(
                                    new KubernetesNamespaceDiagnostics.CollectionStage("pods", "SUCCEEDED", 1, 12, null),
                                    new KubernetesNamespaceDiagnostics.CollectionStage(
                                            "events.k8s.io", "FAILED", 0, 50, "RBAC denied list events")
                            )
                    );
                }

                /** 익명 구현체의 collectPodLogs 처리의 핵심 작업 흐름을 실행한다. */
                @Override
                public KubernetesPodLogs collectPodLogs(KubernetesConnectionCredential credential, String namespace, String podName,
                                                        String containerName, int tailLines, boolean previous) {
                    return podLogs(namespace, podName, tailLines);
                }

                /** 익명 구현체의 collectResourceLogs 처리의 핵심 작업 흐름을 실행한다. */
                @Override
                public KubernetesPodLogs collectResourceLogs(KubernetesConnectionCredential credential, String namespace, String resourceType,
                                                             String resourceName, String containerName, int tailLines, boolean previous) {
                    return podLogs(namespace, resourceType + "/" + resourceName, tailLines);
                }

                /** 익명 구현체의 podLogs 처리에 필요한 업무 로직을 수행한다. */
                private KubernetesPodLogs podLogs(String namespace, String podName, int tailLines) {
                    return new KubernetesPodLogs(
                            namespace,
                            podName,
                            tailLines,
                            List.of(new KubernetesPodLogs.ContainerLog("app", "sample log line", false)),
                            Instant.now()
                    );
                }
            };
        }

        /** FakeAiAnalysisConfig의 kubernetesMutationPort 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        @Primary
        KubernetesMutationPort kubernetesMutationPort() {
            return new KubernetesMutationPort() {
                /** 익명 구현체의 canI 처리 조건의 충족 여부를 판단한다. */
                @Override
                public KubernetesAccessReviewResult canI(KubernetesConnectionCredential credential, String namespace, String verb,
                                                         String group, String resource, String subresource, String resourceName) {
                    return new KubernetesAccessReviewResult(true, verb, resource, subresource, namespace, "allowed");
                }

                /** 익명 구현체의 dryRunRolloutRestartDeployment 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesMutationResult dryRunRolloutRestartDeployment(KubernetesConnectionCredential credential,
                                                                                String namespace,
                                                                                String deploymentName) {
                    return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "rollout-restart-dry-run",
                            "generation=1", "generation=2 dryRun=true", Instant.now());
                }

                /** 익명 구현체의 rolloutRestartDeployment 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesMutationResult rolloutRestartDeployment(KubernetesConnectionCredential credential, String namespace,
                                                                         String deploymentName) {
                    return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "rollout-restart",
                            "generation=1", "generation=2 annotation=aiops.strato.io/restartedAt:now", Instant.now());
                }

                /** 익명 구현체의 dryRunScaleDeployment 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesMutationResult dryRunScaleDeployment(KubernetesConnectionCredential credential, String namespace,
                                                                      String deploymentName, int replicas) {
                    return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "scale-dry-run",
                            "replicas=1", "replicas=" + replicas + " dryRun=true", Instant.now());
                }

                /** 익명 구현체의 scaleDeployment 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesMutationResult scaleDeployment(KubernetesConnectionCredential credential, String namespace,
                                                                String deploymentName, int replicas) {
                    return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "scale",
                            "replicas=1", "replicas=" + replicas, Instant.now());
                }

                /** 익명 구현체의 listDeploymentRevisions 처리 결과를 조회해 반환한다. */
                @Override
                public List<KubernetesDeploymentRevision> listDeploymentRevisions(KubernetesConnectionCredential credential,
                                                                                  String namespace,
                                                                                  String deploymentName) {
                    return List.of(
                            new KubernetesDeploymentRevision("3", true, deploymentName + "-rs3", 1,
                                    "image:v3", "revision=3 image=v3", Instant.now()),
                            new KubernetesDeploymentRevision("2", false, deploymentName + "-rs2", 1,
                                    "image:v2", "revision=2 image=v2", Instant.now())
                    );
                }

                /** 익명 구현체의 previewRollbackDeployment 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesRollbackPlan previewRollbackDeployment(KubernetesConnectionCredential credential,
                                                                        String namespace, String deploymentName,
                                                                        Integer targetRevision) {
                    return new KubernetesRollbackPlan(namespace, deploymentName, "3", String.valueOf(targetRevision),
                            targetRevision != null && targetRevision > 0,
                            "Rollback guard passed",
                            "ROLLBACK " + namespace + "/" + deploymentName + " TO REVISION " + targetRevision,
                            "revision=3 image=v3", "revision=" + targetRevision + " image=v" + targetRevision,
                            Instant.now());
                }

                /** 익명 구현체의 rollbackDeployment 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public KubernetesMutationResult rollbackDeployment(KubernetesConnectionCredential credential, String namespace,
                                                                   String deploymentName, Integer targetRevision) {
                    return new KubernetesMutationResult(namespace, "Deployment", deploymentName, "rollback",
                            "revision=3", "revision=" + targetRevision, Instant.now());
                }
            };
        }

        /** FakeAiAnalysisConfig의 kubernetesStateSyncPort 처리에 필요한 업무 로직을 수행한다. */
        @Bean
        @Primary
        KubernetesStateSyncPort kubernetesStateSyncPort() {
            return credential -> new KubernetesStateInventory(
                    List.of(
                            new KubernetesResourceSnapshot.CollectedResource(
                                    null,
                                    "Namespace",
                                    "default",
                                    "ns-default",
                                    "Active",
                                    "{\"phase\":\"Active\"}",
                                    null,
                                    false,
                                    Instant.now()
                            ),
                            new KubernetesResourceSnapshot.CollectedResource(
                                    "default",
                                    "Pod",
                                    "cluster-sample-pod",
                                    "pod-cluster-sample",
                                    "Pending",
                                    "{\"phase\":\"Pending\",\"restartCount\":0}",
                                    null,
                                    false,
                                    Instant.now()
                            )
                    ),
                    List.of(new KubernetesEventSnapshot.CollectedEvent(
                            "default",
                            "Pod",
                            "cluster-sample-pod",
                            "FailedScheduling",
                            "Warning",
                            "0/1 nodes are available",
                            Instant.now(),
                            3,
                            Instant.now()
                    ))
            );
        }
    }
}
