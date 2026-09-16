package io.strato.aiops.application.service;

import java.util.Locale;

/** Centralizes conservative classification of operator-provided kubectl commands. */
public final class AnalysisCommandPolicy {

    /** AnalysisCommandPolicy의 safetyLevel 처리에 필요한 업무 로직을 수행한다. */
    public String safetyLevel(String command) {
        String normalized = normalize(command);
        if (normalized.matches("kubectl\\s+(get|describe|logs|top|api-resources|explain|auth can-i)\\b.*")) {
            return "READ_ONLY";
        }
        if (isDestructive(command)) {
            return "DESTRUCTIVE";
        }
        if (normalized.matches("kubectl\\s+.*\\b(apply|patch|edit|set|scale|create)\\b.*")
                || normalized.contains(" rollout restart ")) {
            return "RISKY_CHANGE";
        }
        return "REVIEW_REQUIRED";
    }

    /** AnalysisCommandPolicy의 beginnerExplanation 처리에 필요한 업무 로직을 수행한다. */
    public String beginnerExplanation(String level) {
        return switch (normalize(level).toUpperCase(Locale.ROOT)) {
            case "READ_ONLY" -> "조회 명령입니다. 클러스터 상태를 바꾸지 않으므로 원인 확인에 먼저 사용합니다.";
            case "RISKY_CHANGE" -> "리소스를 만들거나 수정할 수 있습니다. 영향 범위를 확인하고 운영 승인 후 실행하세요.";
            case "DESTRUCTIVE" -> "리소스를 삭제하거나 서비스 영향이 큰 변경을 할 수 있습니다. 기본적으로 숨기고 별도 승인해야 합니다.";
            default -> "자동 분류가 애매한 명령입니다. 실행 전 Kubernetes 숙련자에게 검토받으세요.";
        };
    }

    /** AnalysisCommandPolicy의 runbookCategory 처리의 핵심 작업 흐름을 실행한다. */
    public String runbookCategory(String command, boolean destructive) {
        if (destructive || isDestructive(command)) {
            return "destructive";
        }
        String normalized = normalize(command);
        if (normalized.contains(" logs ") || normalized.contains(" describe ") || normalized.contains(" get events")) {
            return "diagnosis";
        }
        if (normalized.contains(" rollout status") || normalized.contains(" auth can-i") || normalized.contains(" top ")) {
            return "verification";
        }
        if (normalized.contains(" apply ") || normalized.contains(" patch ") || normalized.contains(" scale ")
                || normalized.contains(" rollout restart")) {
            return "safe-action";
        }
        return "verification";
    }

    /** AnalysisCommandPolicy의 commandType 처리에 필요한 업무 로직을 수행한다. */
    public String commandType(String command, boolean destructive) {
        if (destructive || isDestructive(command)) {
            return "destructive";
        }
        String normalized = normalize(command);
        if (normalized.contains(" apply ") || normalized.contains(" patch ") || normalized.contains(" scale ")
                || normalized.contains(" rollout restart")) {
            return "safe-change";
        }
        return "read-only";
    }

    /** AnalysisCommandPolicy의 isDestructive 처리 조건의 충족 여부를 판단한다. */
    public boolean isDestructive(String command) {
        String normalized = normalize(command);
        return normalized.matches("kubectl\\s+.*\\b(delete|drain|cordon|uncordon)\\b.*")
                || normalized.contains(" replace --force")
                || normalized.contains(" rollout undo ")
                || normalized.matches(".*\\bscale\\b.*--replicas(?:=|\\s+)0(?:\\s|$).*");
    }

    /** AnalysisCommandPolicy의 normalize 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
