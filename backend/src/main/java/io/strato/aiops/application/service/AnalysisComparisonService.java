package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.domain.analysis.AnalysisSession;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class AnalysisComparisonService {

    private final ObjectMapper objectMapper;

    public AnalysisComparisonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String withComparison(String resultJson, Optional<AnalysisSession> previous) {
        try {
            JsonNode parsed = objectMapper.readTree(resultJson);
            if (!(parsed instanceof ObjectNode root)) return resultJson;
            root.set("analysisComparison", previous.map(value -> comparison(root, value)).orElseGet(() -> baseline(root)));
            previous.ifPresent(value -> carryCommandEvidence(root, value));
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            return resultJson;
        }
    }

    private void carryCommandEvidence(ObjectNode current, AnalysisSession previous) {
        JsonNode previousRoot = parse(previous.resultJson());
        JsonNode previousVerification = previousRoot.path("commandVerification");
        if (previousVerification instanceof ObjectNode verification) {
            ObjectNode carried = verification.deepCopy();
            carried.put("carriedFromAnalysisId", previous.id().toString());
            carried.put("evidenceScope", "PREVIOUS_ANALYSIS_COMMANDS");
            current.set("commandVerification", carried);
        }

        JsonNode previousItems = previousRoot.path("evidenceLedger").path("items");
        if (!previousItems.isArray()) return;
        ObjectNode currentLedger = current.path("evidenceLedger") instanceof ObjectNode ledger
                ? ledger : current.putObject("evidenceLedger");
        ArrayNode merged = objectMapper.createArrayNode();
        JsonNode currentItems = currentLedger.path("items");
        if (currentItems.isArray()) {
            currentItems.forEach(item -> merged.add(item.deepCopy()));
        }
        for (JsonNode item : previousItems) {
            if (merged.size() >= 50) break;
            if ("COMMAND_RESULT".equals(item.path("evidenceType").asText()) && !containsEvidence(merged, item)) {
                ObjectNode carried = item.deepCopy();
                carried.put("carriedFromAnalysisId", previous.id().toString());
                merged.add(carried);
            }
        }
        currentLedger.set("items", merged);
    }

    private boolean containsEvidence(ArrayNode items, JsonNode candidate) {
        String evidenceId = candidate.path("evidenceId").asText("");
        String commandExecutionId = candidate.path("commandExecutionId").asText("");
        for (JsonNode item : items) {
            if ((!evidenceId.isBlank() && evidenceId.equals(item.path("evidenceId").asText()))
                    || (!commandExecutionId.isBlank()
                    && commandExecutionId.equals(item.path("commandExecutionId").asText()))) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode baseline(JsonNode current) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("hasPrevious", false);
        result.put("trend", "BASELINE");
        result.put("summary", "이전 동일 scope 분석이 없어 현재 결과를 기준선으로 저장합니다.");
        result.put("riskScoreBefore", 0);
        result.put("riskScoreAfter", riskScore(current));
        result.put("riskScoreDelta", 0);
        result.put("severityBefore", "");
        result.put("severityAfter", severity(current));
        result.put("severityChanged", false);
        result.put("issueGroupCountBefore", 0);
        result.put("issueGroupCountAfter", issueGroups(current).size());
        result.putArray("newIssueGroups");
        result.putArray("resolvedIssueGroups");
        result.putArray("persistentIssueGroups");
        return result;
    }

    private ObjectNode comparison(JsonNode current, AnalysisSession previous) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("hasPrevious", true);
        result.put("previousAnalysisId", previous.id().toString());
        result.put("previousAnalyzedAt", previous.createdAt().toString());
        JsonNode previousRoot = parse(previous.resultJson());
        int previousRisk = riskScore(previousRoot);
        int currentRisk = riskScore(current);
        Map<String, JsonNode> previousGroups = issueGroups(previousRoot);
        Map<String, JsonNode> currentGroups = issueGroups(current);
        result.put("riskScoreBefore", previousRisk);
        result.put("riskScoreAfter", currentRisk);
        result.put("riskScoreDelta", currentRisk - previousRisk);
        result.put("severityBefore", severity(previousRoot));
        result.put("severityAfter", severity(current));
        result.put("severityChanged", !severity(previousRoot).equals(severity(current)));
        result.put("issueGroupCountBefore", previousGroups.size());
        result.put("issueGroupCountAfter", currentGroups.size());

        ArrayNode newGroups = result.putArray("newIssueGroups");
        currentGroups.forEach((key, group) -> { if (!previousGroups.containsKey(key)) newGroups.add(summary(group, key)); });
        ArrayNode resolvedGroups = result.putArray("resolvedIssueGroups");
        previousGroups.forEach((key, group) -> { if (!currentGroups.containsKey(key)) resolvedGroups.add(summary(group, key)); });
        ArrayNode persistentGroups = result.putArray("persistentIssueGroups");
        currentGroups.forEach((key, group) -> {
            JsonNode previousGroup = previousGroups.get(key);
            if (previousGroup == null) return;
            ObjectNode item = summary(group, key);
            item.put("eventOccurrenceDelta", group.path("eventOccurrenceCount").asInt(0)
                    - previousGroup.path("eventOccurrenceCount").asInt(0));
            item.put("severityBefore", previousGroup.path("severity").asText(""));
            item.put("severityAfter", group.path("severity").asText(""));
            persistentGroups.add(item);
        });
        result.put("trend", trend(currentRisk - previousRisk, newGroups.size(), resolvedGroups.size()));
        result.put("summary", comparisonSummary(result));
        return result;
    }

    JsonNode parse(String json) {
        try {
            JsonNode parsed = objectMapper.readTree(json);
            return parsed == null || parsed.isMissingNode() ? objectMapper.createObjectNode() : parsed;
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private Map<String, JsonNode> issueGroups(JsonNode root) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        JsonNode groups = root == null ? null : root.path("issueGroups");
        if (groups != null && groups.isArray()) {
            for (JsonNode group : groups) {
                String key = group.path("groupKey").asText("");
                if (key.isBlank()) key = group.path("category").asText("") + ":" + group.path("title").asText("");
                if (!key.isBlank()) result.putIfAbsent(key, group);
            }
        }
        if (!result.isEmpty()) return result;
        JsonNode cards = root == null ? null : root.path("problemCards");
        if (cards != null && cards.isArray()) {
            for (JsonNode card : cards) {
                String key = card.path("resourceKind").asText("") + "/" + card.path("resourceName").asText("")
                        + ":" + card.path("title").asText("");
                if (!key.isBlank()) result.putIfAbsent(key, card);
            }
        }
        return result;
    }

    private ObjectNode summary(JsonNode group, String fallbackKey) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("groupKey", group.path("groupKey").asText(fallbackKey));
        item.put("issueGroupId", group.path("issueGroupId").asText(""));
        item.put("title", group.path("title").asText(group.path("rootCause").asText("Issue group")));
        item.put("category", group.path("category").asText(""));
        item.put("severity", group.path("severity").asText("INFO"));
        item.put("fixReadiness", group.path("fixReadiness").asText(""));
        item.put("resourceKind", group.path("representativeResourceKind").asText(group.path("resourceKind").asText("")));
        item.put("resourceName", group.path("representativeResourceName").asText(group.path("resourceName").asText("")));
        item.put("eventOccurrenceCount", group.path("eventOccurrenceCount").asInt(0));
        item.put("logSignalCount", group.path("logSignalCount").asInt(0));
        return item;
    }

    private int riskScore(JsonNode root) { return root == null ? 0 : root.path("riskScore").asInt(0); }
    private String severity(JsonNode root) { return root == null ? "" : root.path("severity").asText(""); }

    private String trend(int riskDelta, int newIssues, int resolvedIssues) {
        if (riskDelta >= 10 || newIssues > resolvedIssues) return "DEGRADED";
        if (riskDelta <= -10 || resolvedIssues > newIssues) return "IMPROVED";
        return "UNCHANGED";
    }

    private String comparisonSummary(JsonNode result) {
        String trend = result.path("trend").asText("UNCHANGED");
        int riskDelta = result.path("riskScoreDelta").asInt(0);
        int newCount = result.path("newIssueGroups").size();
        int resolvedCount = result.path("resolvedIssueGroups").size();
        int persistentCount = result.path("persistentIssueGroups").size();
        return switch (trend) {
            case "DEGRADED" -> "직전 동일 scope 분석 대비 위험도가 악화되었습니다. risk delta=" + riskDelta
                    + ", 신규 issue=" + newCount + ", 지속 issue=" + persistentCount + "입니다.";
            case "IMPROVED" -> "직전 동일 scope 분석 대비 상태가 개선되었습니다. risk delta=" + riskDelta
                    + ", 해결 issue=" + resolvedCount + ", 지속 issue=" + persistentCount + "입니다.";
            default -> "직전 동일 scope 분석 대비 큰 변화는 없습니다. risk delta=" + riskDelta
                    + ", 신규 issue=" + newCount + ", 해결 issue=" + resolvedCount + "입니다.";
        };
    }
}
