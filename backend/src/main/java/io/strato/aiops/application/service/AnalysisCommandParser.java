package io.strato.aiops.application.service;

import io.strato.aiops.domain.analysis.AnalysisCommandSafety;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the deliberately small kubectl subset supported by the analysis console. */
final class AnalysisCommandParser {

    /** AnalysisCommandParser의 parse 처리 데이터를 필요한 표현으로 변환한다. */
    ParsedCommand parse(String rawCommand, String analysisNamespace) {
        String command = value(rawCommand).trim().replace("${namespace}", defaultNamespace(analysisNamespace));
        if (command.isBlank()) {
            return ParsedCommand.blocked(command, defaultNamespace(analysisNamespace), AnalysisCommandSafety.BLOCKED,
                    "명령이 비어 있습니다.");
        }
        List<String> tokens = tokenize(command);
        if (tokens.size() < 2 || !"kubectl".equals(tokens.get(0))) {
            return ParsedCommand.blocked(command, defaultNamespace(analysisNamespace), AnalysisCommandSafety.BLOCKED,
                    "kubectl 조회 명령만 UI에서 실행할 수 있습니다.");
        }

        AnalysisCommandSafety safety = classifySafety(tokens);
        String namespace = extractNamespace(tokens, defaultNamespace(analysisNamespace));
        String operation = tokens.get(1).toLowerCase(Locale.ROOT);
        ParsedResource resource = parseResource(tokens, operation);
        int replicas = extractIntOption(tokens, "--replicas", -1);
        int targetRevision = extractIntOption(tokens, "--to-revision", -1);
        if (safety == AnalysisCommandSafety.CHANGE
                && !isSupportedSafeMutation(tokens, operation, resource, replicas, targetRevision)) {
            return ParsedCommand.blocked(command, namespace, safety,
                    "지원하지 않는 변경 명령입니다. 현재 UI 실행기는 Deployment rollout restart, replicas 1~20 scale, revision 지정 rollback만 지원합니다.");
        }
        if (safety == AnalysisCommandSafety.DESTRUCTIVE || safety == AnalysisCommandSafety.BLOCKED) {
            return ParsedCommand.blocked(command, namespace, safety,
                    "클러스터 상태를 크게 바꾸거나 지원하지 않는 명령은 UI 실행기에서 차단됩니다. RBAC/dry-run/rollback guard 적용 대상이 아닙니다.");
        }

        boolean change = safety == AnalysisCommandSafety.CHANGE;
        String confirmation = change ? confirmationText(namespace, resource.resourceName(), operation, tokens, targetRevision) : "";
        String reason = switch (safety) {
            case READ_ONLY -> "조회 명령입니다. 클러스터 상태를 변경하지 않으므로 원인 확인에 먼저 사용합니다.";
            case DIAGNOSE -> "진단 명령입니다. 상태 변경 없이 확인 목적으로 실행합니다.";
            case CHANGE -> "지원되는 안전 변경 명령입니다. 대상과 변경 내용을 확인한 뒤 확인 문구를 입력해야 실행됩니다.";
            default -> "지원하지 않는 명령입니다.";
        };
        return new ParsedCommand(command, operation, resource.resourceType(), resource.resourceName(), namespace,
                extractOption(tokens, "-c", "--container"), extractIntOption(tokens, "--tail", 300),
                extractFieldSelectorInvolvedName(tokens), hasOption(tokens, "--previous", "-p"), replicas,
                targetRevision, safety, true, change, confirmation, reason);
    }

    /** AnalysisCommandParser의 classifySafety 처리에 필요한 업무 로직을 수행한다. */
    private AnalysisCommandSafety classifySafety(List<String> tokens) {
        String normalized = String.join(" ", tokens).toLowerCase(Locale.ROOT);
        if (containsAny(normalized, " delete ", " apply ", " patch ", " replace ", " edit ", " create ",
                " expose ", " run ", " scale ", " drain ", " cordon ", " uncordon ", " taint ", " label ",
                " annotate ", " rollout restart", " rollout undo", " set ")) {
            return normalized.contains(" delete ") || normalized.contains(" drain ")
                    || normalized.contains("--replicas=0") || normalized.contains("--replicas 0")
                    ? AnalysisCommandSafety.DESTRUCTIVE : AnalysisCommandSafety.CHANGE;
        }
        if (tokens.size() > 1 && List.of("get", "describe", "logs").contains(tokens.get(1).toLowerCase(Locale.ROOT))) {
            return "logs".equals(tokens.get(1).toLowerCase(Locale.ROOT))
                    ? AnalysisCommandSafety.DIAGNOSE : AnalysisCommandSafety.READ_ONLY;
        }
        if (tokens.size() > 2 && "auth".equalsIgnoreCase(tokens.get(1)) && "can-i".equalsIgnoreCase(tokens.get(2))) {
            return AnalysisCommandSafety.READ_ONLY;
        }
        return AnalysisCommandSafety.BLOCKED;
    }

    /** AnalysisCommandParser의 containsAny 처리에 필요한 업무 로직을 수행한다. */
    private boolean containsAny(String value, String... needles) {
        String padded = " " + value + " ";
        for (String needle : needles) {
            if (padded.contains(needle)) return true;
        }
        return false;
    }

    /** AnalysisCommandParser의 tokenize 처리 데이터를 필요한 표현으로 변환한다. */
    private List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)").matcher(command);
        while (matcher.find()) {
            tokens.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
        }
        return tokens;
    }

    /** AnalysisCommandParser의 parseResource 처리 데이터를 필요한 표현으로 변환한다. */
    private ParsedResource parseResource(List<String> tokens, String operation) {
        List<String> positional = new ArrayList<>();
        for (int i = 2; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("-")) {
                if (List.of("-n", "--namespace", "-c", "--container", "--tail", "--field-selector", "--replicas",
                        "--to-revision").contains(token) && i + 1 < tokens.size()) i++;
                continue;
            }
            positional.add(token);
        }
        if ("logs".equals(operation)) {
            return new ParsedResource("Pod", positional.isEmpty() ? "" : positional.get(0).replaceFirst("^pod/", ""));
        }
        if ("rollout".equals(operation) && positional.size() >= 2
                && List.of("restart", "undo").contains(positional.get(0))) {
            return parseKindName(positional.get(1));
        }
        if (positional.isEmpty()) return new ParsedResource("", "");
        if (positional.get(0).contains("/")) return parseKindName(positional.get(0));
        return new ParsedResource(positional.get(0), positional.size() > 1 ? positional.get(1) : "");
    }

    /** AnalysisCommandParser의 parseKindName 처리 데이터를 필요한 표현으로 변환한다. */
    private ParsedResource parseKindName(String value) {
        String[] parts = value.split("/", 2);
        return new ParsedResource(parts[0], parts.length > 1 ? parts[1] : "");
    }

    /** AnalysisCommandParser의 extractNamespace 처리에 필요한 업무 로직을 수행한다. */
    private String extractNamespace(List<String> tokens, String fallback) {
        String namespace = extractOption(tokens, "-n", "--namespace");
        return defaultNamespace(namespace == null ? fallback : namespace);
    }

    /** AnalysisCommandParser의 extractOption 처리에 필요한 업무 로직을 수행한다. */
    private String extractOption(List<String> tokens, String... names) {
        for (int i = 0; i < tokens.size(); i++) {
            for (String name : names) {
                if (tokens.get(i).startsWith(name + "=")) return tokens.get(i).substring(name.length() + 1);
                if (tokens.get(i).equals(name) && i + 1 < tokens.size()) return tokens.get(i + 1);
            }
        }
        return null;
    }

    /** AnalysisCommandParser의 extractIntOption 처리에 필요한 업무 로직을 수행한다. */
    private int extractIntOption(List<String> tokens, String name, int fallback) {
        String raw = extractOption(tokens, name);
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** AnalysisCommandParser의 hasOption 처리 조건의 충족 여부를 판단한다. */
    private boolean hasOption(List<String> tokens, String... names) {
        return tokens.stream().anyMatch(token -> List.of(names).contains(token));
    }

    /** AnalysisCommandParser의 extractFieldSelectorInvolvedName 처리에 필요한 업무 로직을 수행한다. */
    private String extractFieldSelectorInvolvedName(List<String> tokens) {
        String selector = extractOption(tokens, "--field-selector");
        Matcher matcher = Pattern.compile("involvedObject\\.name=([^,\\s]+)").matcher(value(selector));
        return matcher.find() ? matcher.group(1) : "";
    }

    /** AnalysisCommandParser의 isSupportedSafeMutation 처리 조건의 충족 여부를 판단한다. */
    private boolean isSupportedSafeMutation(List<String> tokens, String operation, ParsedResource resource,
                                            int replicas, int targetRevision) {
        if (!"Deployment".equals(normalizeResourceType(resource.resourceType())) || value(resource.resourceName()).isBlank()) {
            return false;
        }
        if ("rollout".equals(operation) && tokens.size() > 2) {
            return "restart".equalsIgnoreCase(tokens.get(2))
                    || "undo".equalsIgnoreCase(tokens.get(2)) && targetRevision > 0;
        }
        return "scale".equals(operation) && replicas >= 1 && replicas <= 20;
    }

    /** AnalysisCommandParser의 confirmationText 처리에 필요한 업무 로직을 수행한다. */
    private String confirmationText(String namespace, String resourceName, String operation, List<String> tokens,
                                    int targetRevision) {
        String target = defaultNamespace(namespace) + "/" + requireText(resourceName, "resourceName");
        if ("rollout".equals(operation) && tokens.size() > 2 && "undo".equalsIgnoreCase(tokens.get(2))) {
            return "ROLLBACK " + target + " TO REVISION " + targetRevision;
        }
        return "APPLY " + target;
    }

    /** AnalysisCommandParser의 normalizeResourceType 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizeResourceType(String resourceType) {
        return switch (value(resourceType).toLowerCase(Locale.ROOT)) {
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

    /** AnalysisCommandParser의 defaultNamespace 처리에 필요한 업무 로직을 수행한다. */
    private String defaultNamespace(String namespace) {
        String normalized = value(namespace).trim();
        return normalized.isBlank() ? "default" : normalized;
    }

    /** AnalysisCommandParser의 requireText 처리 입력과 현재 상태의 유효성을 검증한다. */
    private String requireText(String text, String name) {
        String normalized = value(text).trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }

    /** AnalysisCommandParser의 value 처리에 필요한 업무 로직을 수행한다. */
    private String value(String text) {
        return text == null ? "" : text;
    }

    private record ParsedResource(String resourceType, String resourceName) {
    }

    record ParsedCommand(String command, String operation, String resourceType, String resourceName,
                         String namespace, String containerName, int tailLines, String fieldSelectorInvolvedName,
                         boolean previousLogs, int replicas, int targetRevision, AnalysisCommandSafety safety,
                         boolean executable, boolean requiresConfirmation, String confirmationText, String reason) {
        /** ParsedCommand의 blocked 처리에 필요한 업무 로직을 수행한다. */
        static ParsedCommand blocked(String command, String namespace, AnalysisCommandSafety safety, String reason) {
            return new ParsedCommand(command, "", "", "", namespace, null, 300, "", false, -1, -1, safety,
                    false, false, "", reason);
        }
    }
}
