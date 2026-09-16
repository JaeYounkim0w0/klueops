package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.domain.analysis.AnalysisSession;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisComparisonServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnalysisComparisonService service = new AnalysisComparisonService(mapper);

    /** AnalysisComparisonServiceTest의 createsBaselineWithoutPreviousAnalysis 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Test
    void createsBaselineWithoutPreviousAnalysis() throws Exception {
        String result = service.withComparison("{\"riskScore\":42,\"severity\":\"MEDIUM\",\"issueGroups\":[]}", Optional.empty());

        assertThat(mapper.readTree(result).path("analysisComparison").path("trend").asText()).isEqualTo("BASELINE");
    }

    /** AnalysisComparisonServiceTest의 identifiesResolvedAndNewIssueGroups 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void identifiesResolvedAndNewIssueGroups() throws Exception {
        UUID clusterId = UUID.randomUUID();
        AnalysisSession previous = AnalysisSession.succeeded(clusterId, null, "default",
                "{\"riskScore\":80,\"severity\":\"HIGH\",\"issueGroups\":[{\"groupKey\":\"old\",\"title\":\"Old\"}]}", "tester");

        String result = service.withComparison(
                "{\"riskScore\":30,\"severity\":\"LOW\",\"issueGroups\":[{\"groupKey\":\"new\",\"title\":\"New\"}]}",
                Optional.of(previous));

        var comparison = mapper.readTree(result).path("analysisComparison");
        assertThat(comparison.path("trend").asText()).isEqualTo("IMPROVED");
        assertThat(comparison.path("newIssueGroups")).hasSize(1);
        assertThat(comparison.path("resolvedIssueGroups")).hasSize(1);
    }

    /** AnalysisComparisonServiceTest의 carriesBoundedCommandVerificationAndEvidenceIntoReanalysis 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void carriesBoundedCommandVerificationAndEvidenceIntoReanalysis() throws Exception {
        UUID clusterId = UUID.randomUUID();
        AnalysisSession previous = AnalysisSession.succeeded(clusterId, null, "default", """
                {
                  "riskScore":42,
                  "severity":"MEDIUM",
                  "issueGroups":[],
                  "commandVerification":{"lastStatus":"SUCCEEDED","executions":[{"id":"command-1"}]},
                  "evidenceLedger":{"items":[
                    {"evidenceId":"CMD-command-1","evidenceType":"COMMAND_RESULT","commandExecutionId":"command-1"},
                    {"evidenceId":"K8S-1","evidenceType":"FACT"}
                  ]}
                }
                """, "tester");

        String result = service.withComparison(
                "{\"riskScore\":40,\"severity\":\"MEDIUM\",\"issueGroups\":[],\"evidenceLedger\":{\"items\":[]}}",
                Optional.of(previous));
        JsonNode root = mapper.readTree(result);

        assertThat(root.path("commandVerification").path("lastStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(root.path("commandVerification").path("carriedFromAnalysisId").asText())
                .isEqualTo(previous.id().toString());
        assertThat(root.path("commandVerification").path("evidenceScope").asText())
                .isEqualTo("PREVIOUS_ANALYSIS_COMMANDS");
        assertThat(root.path("evidenceLedger").path("items")).hasSize(1);
        assertThat(root.path("evidenceLedger").path("items").get(0).path("evidenceType").asText())
                .isEqualTo("COMMAND_RESULT");
    }
}
