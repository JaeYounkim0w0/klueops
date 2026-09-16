package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.domain.analysis.AnalysisCommandExecution;

import java.util.Locale;
import java.util.Optional;

/** Enriches a completed analysis with bounded evidence from an executed console command. */
final class AnalysisCommandEvidenceMerger {

    private static final int COMMAND_HISTORY_LIMIT = 10;
    private static final int ISSUE_GROUP_HISTORY_LIMIT = 5;

    private final ObjectMapper objectMapper;

    /** AnalysisCommandEvidenceMerger 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    AnalysisCommandEvidenceMerger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** AnalysisCommandEvidenceMerger의 merge 처리에 필요한 업무 로직을 수행한다. */
    Optional<String> merge(String resultJson, AnalysisCommandExecution execution,
                           AnalysisCommandParser.ParsedCommand parsed) {
        if (resultJson == null || resultJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode analysisJson = objectMapper.readTree(resultJson);
            if (!(analysisJson instanceof ObjectNode root)) {
                return Optional.empty();
            }
            appendCommandVerification(root, execution);
            appendCommandEvidence(root, execution);
            int matchedIssueGroups = appendIssueGroupCommandVerification(root, execution, parsed);
            appendCommandConclusion(root, execution, matchedIssueGroups);
            return Optional.of(objectMapper.writeValueAsString(root));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /** AnalysisCommandEvidenceMerger의 appendCommandVerification 처리에 필요한 업무 로직을 수행한다. */
    private void appendCommandVerification(ObjectNode root, AnalysisCommandExecution execution) {
        ObjectNode section = objectField(root, "commandVerification");
        section.put("summary", "최근 검증 명령 실행 결과를 분석 결과에 연결했습니다.");
        section.put("beginnerSummary", "AI 판단을 실제 Kubernetes 조회/조치 결과와 비교하기 위한 기록입니다.");
        section.put("lastStatus", execution.status().name());
        section.put("lastCommand", execution.command());
        section.put("lastSafety", execution.safety().name());
        section.put("lastExecutedAt", execution.createdAt().toString());
        section.put("lastDurationMs", execution.durationMs() == null ? 0L : execution.durationMs());
        section.put("lastOutputSummary", commandExecutionSummary(execution));

        ArrayNode previous = arrayOrEmpty(section.get("executions"));
        ArrayNode executions = objectMapper.createArrayNode();
        executions.add(commandExecutionJson(execution));
        copyDistinctHistory(previous, executions, execution, COMMAND_HISTORY_LIMIT);
        section.set("executions", executions);
    }

    /** AnalysisCommandEvidenceMerger의 appendCommandEvidence 처리에 필요한 업무 로직을 수행한다. */
    private void appendCommandEvidence(ObjectNode root, AnalysisCommandExecution execution) {
        ObjectNode ledger = objectField(root, "evidenceLedger");
        ledger.put("summary", "분석 판단에 사용된 사실과 추론, 검증 명령 결과를 분리해 표시합니다.");
        ledger.put("beginnerSummary", "COMMAND_RESULT는 사용자가 직접 실행한 검증/조치 명령 결과입니다.");
        ObjectNode item = arrayField(ledger, "items").addObject();
        item.put("evidenceId", "CMD-" + execution.id().toString().substring(0, 8));
        item.put("evidenceType", "COMMAND_RESULT");
        item.put("confidence", commandEvidenceConfidence(execution));
        item.put("source", "Command/" + execution.safety().name());
        item.put("message", execution.status().name() + " · " + truncate(execution.command(), 220));
        item.put("beginnerExplanation", commandBeginnerExplanation(execution));
        item.put("verificationCommand", execution.command());
        item.put("commandExecutionId", execution.id().toString());
        item.put("status", execution.status().name());
        item.put("durationMs", execution.durationMs() == null ? 0L : execution.durationMs());
        item.put("outputSummary", commandExecutionSummary(execution));
    }

    /** AnalysisCommandEvidenceMerger의 appendIssueGroupCommandVerification 처리에 필요한 업무 로직을 수행한다. */
    private int appendIssueGroupCommandVerification(ObjectNode root, AnalysisCommandExecution execution,
                                                    AnalysisCommandParser.ParsedCommand parsed) {
        ArrayNode issueGroups = arrayOrEmpty(root.get("issueGroups"));
        int matched = 0;
        for (JsonNode group : issueGroups) {
            if (!(group instanceof ObjectNode groupNode) || !commandMatchesIssueGroup(parsed, groupNode)) {
                continue;
            }
            matched++;
            ObjectNode verification = objectField(groupNode, "commandVerification");
            verification.put("status", issueGroupCommandStatus(execution));
            verification.put("latestCommandStatus", execution.status().name());
            verification.put("latestCommand", execution.command());
            verification.put("latestCommandExecutionId", execution.id().toString());
            verification.put("latestExecutedAt", execution.createdAt().toString());
            verification.put("latestSummary", commandExecutionSummary(execution));
            verification.put("operatorMeaning", issueGroupCommandMeaning(execution));
            verification.put("beginnerExplanation", commandBeginnerExplanation(execution));
            ArrayNode history = objectMapper.createArrayNode();
            history.add(commandExecutionJson(execution));
            copyDistinctHistory(arrayOrEmpty(verification.get("history")), history, execution,
                    ISSUE_GROUP_HISTORY_LIMIT);
            verification.set("history", history);
            groupNode.put("verificationStatus", issueGroupCommandStatus(execution));
            groupNode.put("verificationStatusReason", issueGroupCommandMeaning(execution));
        }
        return matched;
    }

    /** AnalysisCommandEvidenceMerger의 appendCommandConclusion 처리에 필요한 업무 로직을 수행한다. */
    private void appendCommandConclusion(ObjectNode root, AnalysisCommandExecution execution, int matchedIssueGroups) {
        ObjectNode validation = objectField(root, "conclusionValidation");
        validation.put("summary", "AI 결론과 사용자가 실행한 검증 명령 결과를 함께 추적합니다.");
        validation.put("beginnerSummary", "명령 실행 결과가 쌓이면 AI 결론이 실제 상태와 맞는지 더 쉽게 확인할 수 있습니다.");
        validation.put("latestCommandStatus", execution.status().name());
        validation.put("latestCommandSafety", execution.safety().name());
        validation.put("latestCommandSummary", commandExecutionSummary(execution));
        validation.put("latestCommandMatchedIssueGroups", matchedIssueGroups);

        ArrayNode commandResults = objectMapper.createArrayNode();
        commandResults.add(commandExecutionJson(execution));
        copyDistinctHistory(arrayOrEmpty(validation.get("commandResults")), commandResults, execution,
                COMMAND_HISTORY_LIMIT);
        validation.set("commandResults", commandResults);
        validation.put("commandResultCount", commandResults.size());
        validation.put("verifiedByCommandCount", countCommandResults(commandResults, "SUCCEEDED"));
        validation.put("needsFollowUpCommandCount", countNonSucceededCommandResults(commandResults));
    }

    /** AnalysisCommandEvidenceMerger의 copyDistinctHistory 처리에 필요한 업무 로직을 수행한다. */
    private void copyDistinctHistory(ArrayNode previous, ArrayNode target, AnalysisCommandExecution execution,
                                     int limit) {
        for (JsonNode item : previous) {
            if (target.size() >= limit) {
                break;
            }
            if (!execution.id().toString().equals(item.path("id").asText())) {
                target.add(item.deepCopy());
            }
        }
    }

    /** AnalysisCommandEvidenceMerger의 commandMatchesIssueGroup 처리에 필요한 업무 로직을 수행한다. */
    private boolean commandMatchesIssueGroup(AnalysisCommandParser.ParsedCommand parsed, JsonNode group) {
        String commandKind = normalizeResourceType(parsed.resourceType());
        String commandName = valueOrBlank(parsed.resourceName());
        if (commandName.isBlank() && !valueOrBlank(parsed.fieldSelectorInvolvedName()).isBlank()) {
            commandKind = "Event";
            commandName = parsed.fieldSelectorInvolvedName();
        }
        if (commandName.isBlank()) {
            return false;
        }

        String representativeKind = normalizeResourceType(group.path("representativeResourceKind")
                .asText(group.path("resourceKind").asText("")));
        String representativeName = group.path("representativeResourceName")
                .asText(group.path("resourceName").asText(""));
        if (commandName.equals(representativeName)
                && (commandKind.isBlank() || representativeKind.isBlank() || commandKind.equals(representativeKind)
                || "Event".equals(commandKind))) {
            return true;
        }

        for (JsonNode affected : group.path("affectedResources")) {
            String affectedValue = affected.asText("");
            if (affectedValue.equals(commandKind + "/" + commandName) || affectedValue.endsWith("/" + commandName)) {
                return true;
            }
        }
        for (JsonNode reference : group.path("relatedReferences")) {
            String kind = normalizeResourceType(reference.path("kind").asText(""));
            String name = reference.path("name").asText("");
            if (commandName.equals(name) && (commandKind.isBlank() || commandKind.equals(kind) || "Event".equals(commandKind))) {
                return true;
            }
        }
        return false;
    }

    /** AnalysisCommandEvidenceMerger의 issueGroupCommandStatus 처리 조건의 충족 여부를 판단한다. */
    private String issueGroupCommandStatus(AnalysisCommandExecution execution) {
        return switch (execution.status()) {
            case SUCCEEDED -> "COMMAND_VERIFIED";
            case FAILED -> "COMMAND_FAILED";
            case BLOCKED -> "COMMAND_BLOCKED";
        };
    }

    /** AnalysisCommandEvidenceMerger의 issueGroupCommandMeaning 처리 조건의 충족 여부를 판단한다. */
    private String issueGroupCommandMeaning(AnalysisCommandExecution execution) {
        return switch (execution.status()) {
            case SUCCEEDED -> "관련 검증 명령이 성공했습니다. 출력 내용을 근거로 다음 조치 또는 재분석을 진행하세요.";
            case FAILED -> "관련 검증 명령이 실패했습니다. 권한, 리소스명, namespace, 현재 상태를 먼저 확인해야 합니다.";
            case BLOCKED -> "관련 명령이 안전 정책으로 차단되었습니다. 클러스터 상태는 변경되지 않았습니다.";
        };
    }

    /** AnalysisCommandEvidenceMerger의 commandExecutionJson 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode commandExecutionJson(AnalysisCommandExecution execution) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("id", execution.id().toString());
        item.put("command", execution.command());
        item.put("safety", execution.safety().name());
        item.put("status", execution.status().name());
        item.put("reason", valueOrBlank(execution.reason()));
        item.put("durationMs", execution.durationMs() == null ? 0L : execution.durationMs());
        item.put("createdAt", execution.createdAt().toString());
        item.put("summary", commandExecutionSummary(execution));
        return item;
    }

    /** AnalysisCommandEvidenceMerger의 objectField 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode objectField(ObjectNode parent, String fieldName) {
        JsonNode existing = parent.get(fieldName);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(fieldName, created);
        return created;
    }

    /** AnalysisCommandEvidenceMerger의 arrayField 처리에 필요한 업무 로직을 수행한다. */
    private ArrayNode arrayField(ObjectNode parent, String fieldName) {
        JsonNode existing = parent.get(fieldName);
        if (existing instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode created = objectMapper.createArrayNode();
        parent.set(fieldName, created);
        return created;
    }

    /** AnalysisCommandEvidenceMerger의 arrayOrEmpty 처리에 필요한 업무 로직을 수행한다. */
    private ArrayNode arrayOrEmpty(JsonNode node) {
        return node instanceof ArrayNode arrayNode ? arrayNode : objectMapper.createArrayNode();
    }

    /** AnalysisCommandEvidenceMerger의 countCommandResults 처리에 필요한 업무 로직을 수행한다. */
    private long countCommandResults(ArrayNode commandResults, String status) {
        long count = 0;
        for (JsonNode item : commandResults) {
            if (status.equals(item.path("status").asText())) {
                count++;
            }
        }
        return count;
    }

    /** AnalysisCommandEvidenceMerger의 countNonSucceededCommandResults 처리에 필요한 업무 로직을 수행한다. */
    private long countNonSucceededCommandResults(ArrayNode commandResults) {
        long count = 0;
        for (JsonNode item : commandResults) {
            if (!"SUCCEEDED".equals(item.path("status").asText())) {
                count++;
            }
        }
        return count;
    }

    /** AnalysisCommandEvidenceMerger의 commandEvidenceConfidence 처리에 필요한 업무 로직을 수행한다. */
    private String commandEvidenceConfidence(AnalysisCommandExecution execution) {
        return switch (execution.status()) {
            case SUCCEEDED -> "HIGH";
            case FAILED -> "MEDIUM";
            case BLOCKED -> "LOW";
        };
    }

    /** AnalysisCommandEvidenceMerger의 commandBeginnerExplanation 처리에 필요한 업무 로직을 수행한다. */
    private String commandBeginnerExplanation(AnalysisCommandExecution execution) {
        return switch (execution.status()) {
            case SUCCEEDED -> "명령이 성공했으므로 AI 분석의 일부 근거를 실제 클러스터에서 확인한 상태입니다.";
            case FAILED -> "명령 실행이 실패했습니다. 실패 원인을 먼저 확인한 뒤 같은 scope를 다시 분석하세요.";
            case BLOCKED -> "안전 정책 또는 확인 문구 때문에 실행하지 않았습니다. 클러스터 상태는 변경되지 않았습니다.";
        };
    }

    /** AnalysisCommandEvidenceMerger의 commandExecutionSummary 처리에 필요한 업무 로직을 수행한다. */
    private String commandExecutionSummary(AnalysisCommandExecution execution) {
        String output = execution.status().name().equals("SUCCEEDED")
                ? valueOrBlank(execution.stdoutText()) : valueOrBlank(execution.stderrText());
        if (output.isBlank()) {
            output = valueOrBlank(execution.reason());
        }
        return truncate(output.replaceAll("\\s+", " ").trim(), 500);
    }

    /** AnalysisCommandEvidenceMerger의 normalizeResourceType 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizeResourceType(String resourceType) {
        return switch (valueOrBlank(resourceType).toLowerCase(Locale.ROOT)) {
            case "", "all" -> "";
            case "pod", "pods", "po" -> "Pod";
            case "service", "services", "svc" -> "Service";
            case "configmap", "configmaps", "cm" -> "ConfigMap";
            case "secret", "secrets" -> "Secret";
            case "persistentvolumeclaim", "persistentvolumeclaims", "pvc" -> "PersistentVolumeClaim";
            case "deployment", "deployments", "deploy" -> "Deployment";
            case "replicaset", "replicasets", "rs" -> "ReplicaSet";
            case "statefulset", "statefulsets", "sts" -> "StatefulSet";
            case "daemonset", "daemonsets", "ds" -> "DaemonSet";
            case "job", "jobs" -> "Job";
            case "cronjob", "cronjobs" -> "CronJob";
            case "ingress", "ingresses", "ing" -> "Ingress";
            case "horizontalpodautoscaler", "horizontalpodautoscalers", "hpa" -> "HorizontalPodAutoscaler";
            case "event", "events", "ev" -> "Event";
            default -> resourceType;
        };
    }

    /** AnalysisCommandEvidenceMerger의 truncate 처리에 필요한 업무 로직을 수행한다. */
    private String truncate(String value, int maxLength) {
        String normalized = valueOrBlank(value);
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    /** AnalysisCommandEvidenceMerger의 valueOrBlank 처리에 필요한 업무 로직을 수행한다. */
    private String valueOrBlank(String value) {
        return value == null ? "" : value;
    }
}
