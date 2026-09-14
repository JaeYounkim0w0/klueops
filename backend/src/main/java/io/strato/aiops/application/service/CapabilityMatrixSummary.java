package io.strato.aiops.application.service;

import java.util.List;

public record CapabilityMatrixSummary(String status, int allowed, int denied, int unknown, int score) {

    public static CapabilityMatrixSummary from(List<String> states) {
        int allowed = (int) states.stream().filter("ALLOWED"::equals).count();
        int denied = (int) states.stream().filter("DENIED"::equals).count();
        int unknown = (int) states.stream().filter("UNKNOWN"::equals).count();
        int score = states.isEmpty() ? 0 : Math.round(allowed * 100f / states.size());
        String status = states.isEmpty() || unknown == states.size() ? "UNKNOWN"
                : allowed == states.size() ? "ALLOWED"
                : denied == states.size() ? "DENIED" : "PARTIAL";
        return new CapabilityMatrixSummary(status, allowed, denied, unknown, score);
    }
}
