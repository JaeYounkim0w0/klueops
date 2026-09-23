package io.product.aiops.domain.analysis;

public enum AnalysisCommandSafety {
    READ_ONLY,
    DIAGNOSE,
    CHANGE,
    DESTRUCTIVE,
    BLOCKED
}
