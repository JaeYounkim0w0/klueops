package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.domain.analysis.AnalysisCommandExecution;
import io.strato.aiops.domain.analysis.AnalysisCommandSafety;
import io.strato.aiops.domain.analysis.AnalysisCommandStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisCommandEvidenceMergerTest {

    private static final UUID EXECUTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ANALYSIS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CLUSTER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant EXECUTED_AT = Instant.parse("2026-09-11T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnalysisCommandEvidenceMerger merger = new AnalysisCommandEvidenceMerger(objectMapper);
    private final AnalysisCommandParser parser = new AnalysisCommandParser();

    /** AnalysisCommandEvidenceMergerTest의 connectsSuccessfulCommandEvidenceToMatchingIssueAndConclusion 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void connectsSuccessfulCommandEvidenceToMatchingIssueAndConclusion() throws Exception {
        String resultJson = """
                {
                  "issueGroups": [{
                    "representativeResourceKind": "Deployment",
                    "representativeResourceName": "api",
                    "affectedResources": ["Deployment/api"]
                  }],
                  "evidenceLedger": {"items": []},
                  "conclusionValidation": {
                    "commandResults": [{"id":"old","status":"FAILED"}]
                  }
                }
                """;
        String command = "kubectl get deployment/api -n demo";

        Optional<String> merged = merger.merge(resultJson,
                execution(EXECUTION_ID, command, AnalysisCommandStatus.SUCCEEDED, "deployment.apps/api available"),
                parser.parse(command, "demo"));

        assertThat(merged).isPresent();
        JsonNode root = objectMapper.readTree(merged.orElseThrow());
        assertThat(root.path("commandVerification").path("lastStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(root.path("commandVerification").path("executions")).hasSize(1);

        JsonNode evidence = root.path("evidenceLedger").path("items").get(0);
        assertThat(evidence.path("evidenceType").asText()).isEqualTo("COMMAND_RESULT");
        assertThat(evidence.path("confidence").asText()).isEqualTo("HIGH");
        assertThat(evidence.path("commandExecutionId").asText()).isEqualTo(EXECUTION_ID.toString());

        JsonNode issue = root.path("issueGroups").get(0);
        assertThat(issue.path("verificationStatus").asText()).isEqualTo("COMMAND_VERIFIED");
        assertThat(issue.path("commandVerification").path("history")).hasSize(1);

        JsonNode conclusion = root.path("conclusionValidation");
        assertThat(conclusion.path("latestCommandMatchedIssueGroups").asInt()).isEqualTo(1);
        assertThat(conclusion.path("commandResultCount").asInt()).isEqualTo(2);
        assertThat(conclusion.path("verifiedByCommandCount").asInt()).isEqualTo(1);
        assertThat(conclusion.path("needsFollowUpCommandCount").asInt()).isEqualTo(1);
    }

    /** AnalysisCommandEvidenceMergerTest의 matchesEventFieldSelectorToTheAffectedResourceName 처리 조건의 충족 여부를 판단한다. */
    @Test
    void matchesEventFieldSelectorToTheAffectedResourceName() throws Exception {
        String resultJson = """
                {"issueGroups":[{"resourceKind":"Pod","resourceName":"api-0"}]}
                """;
        String command = "kubectl get events -n demo --field-selector involvedObject.name=api-0";

        String merged = merger.merge(resultJson,
                execution(EXECUTION_ID, command, AnalysisCommandStatus.FAILED, "forbidden"),
                parser.parse(command, "demo")).orElseThrow();

        JsonNode root = objectMapper.readTree(merged);
        assertThat(root.path("issueGroups").get(0).path("verificationStatus").asText())
                .isEqualTo("COMMAND_FAILED");
        assertThat(root.path("conclusionValidation").path("latestCommandMatchedIssueGroups").asInt())
                .isEqualTo(1);
    }

    /** AnalysisCommandEvidenceMergerTest의 deduplicatesLatestExecutionAndKeepsBoundedHistories 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void deduplicatesLatestExecutionAndKeepsBoundedHistories() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode verification = root.putObject("commandVerification");
        ArrayNode executions = verification.putArray("executions");
        executions.addObject().put("id", EXECUTION_ID.toString()).put("status", "FAILED");
        for (int index = 0; index < 12; index++) {
            executions.addObject().put("id", "old-" + index).put("status", "FAILED");
        }
        ObjectNode conclusion = root.putObject("conclusionValidation");
        ArrayNode results = conclusion.putArray("commandResults");
        results.addObject().put("id", EXECUTION_ID.toString()).put("status", "FAILED");
        for (int index = 0; index < 12; index++) {
            results.addObject().put("id", "old-" + index).put("status", "FAILED");
        }
        String command = "kubectl get pods -n demo";

        String merged = merger.merge(objectMapper.writeValueAsString(root),
                execution(EXECUTION_ID, command, AnalysisCommandStatus.SUCCEEDED, "ok"),
                parser.parse(command, "demo")).orElseThrow();

        JsonNode mergedRoot = objectMapper.readTree(merged);
        assertThat(mergedRoot.path("commandVerification").path("executions")).hasSize(10);
        assertThat(mergedRoot.path("conclusionValidation").path("commandResults")).hasSize(10);
        assertThat(countId(mergedRoot.path("commandVerification").path("executions"), EXECUTION_ID.toString()))
                .isEqualTo(1);
    }

    /** AnalysisCommandEvidenceMergerTest의 ignoresBlankMalformedAndNonObjectAnalysisResults 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void ignoresBlankMalformedAndNonObjectAnalysisResults() {
        AnalysisCommandExecution execution = execution(EXECUTION_ID, "kubectl get pods",
                AnalysisCommandStatus.BLOCKED, "blocked");
        AnalysisCommandParser.ParsedCommand parsed = parser.parse("kubectl get pods", "default");

        assertThat(merger.merge("", execution, parsed)).isEmpty();
        assertThat(merger.merge("{invalid", execution, parsed)).isEmpty();
        assertThat(merger.merge("[]", execution, parsed)).isEmpty();
    }

    /** AnalysisCommandEvidenceMergerTest의 execution 처리에 필요한 업무 로직을 수행한다. */
    private AnalysisCommandExecution execution(UUID id, String command, AnalysisCommandStatus status, String output) {
        return new AnalysisCommandExecution(id, ANALYSIS_ID, CLUSTER_ID, "demo", command,
                AnalysisCommandSafety.READ_ONLY, status, "test reason",
                status == AnalysisCommandStatus.SUCCEEDED ? output : "",
                status == AnalysisCommandStatus.SUCCEEDED ? "" : output,
                status == AnalysisCommandStatus.SUCCEEDED ? 0 : 1, 25L, "tester", EXECUTED_AT);
    }

    /** AnalysisCommandEvidenceMergerTest의 countId 처리에 필요한 업무 로직을 수행한다. */
    private long countId(JsonNode items, String id) {
        long count = 0;
        for (JsonNode item : items) {
            if (id.equals(item.path("id").asText())) {
                count++;
            }
        }
        return count;
    }
}
