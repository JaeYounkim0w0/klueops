package io.strato.aiops.domain.analysis;

public enum AnalysisCommandSafety {
    READ_ONLY,
    DIAGNOSE,
    CHANGE,
    DESTRUCTIVE,
    BLOCKED
}
