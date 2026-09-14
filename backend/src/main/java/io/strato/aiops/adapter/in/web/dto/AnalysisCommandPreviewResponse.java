package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.AnalysisCommandPreviewResult;
import io.strato.aiops.domain.analysis.AnalysisCommandSafety;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI analysis command preview")
public record AnalysisCommandPreviewResponse(
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
    public static AnalysisCommandPreviewResponse from(AnalysisCommandPreviewResult result) {
        return new AnalysisCommandPreviewResponse(result.command(), result.safety(), result.executable(),
                result.reason(), result.normalizedNamespace(), result.requiresConfirmation(), result.confirmationText(),
                result.rbacAllowed(), result.dryRunPassed(), result.rollbackGuardPassed(), result.guardMessage(),
                result.dryRunSummary());
    }
}
