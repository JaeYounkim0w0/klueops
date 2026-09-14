package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.AnalysisCommandSafety;

public record AnalysisCommandPreviewResult(
        String command,
        AnalysisCommandSafety safety,
        boolean executable,
        String reason,
        String normalizedNamespace,
        boolean requiresConfirmation,
        String confirmationText,
        boolean rbacAllowed,
        boolean dryRunPassed,
        boolean rollbackGuardPassed,
        String guardMessage,
        String dryRunSummary
) {
}
