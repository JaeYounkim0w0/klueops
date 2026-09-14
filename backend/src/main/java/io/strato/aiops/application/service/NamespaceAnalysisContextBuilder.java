package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Consumer;

@Component
final class NamespaceAnalysisContextBuilder {

    String build(UUID clusterId, String namespace, String applicationName,
                 KubernetesNamespaceDiagnostics diagnostics, String sectionName, int maxChars,
                 Consumer<StringBuilder> signalAppender) {
        StringBuilder context = new StringBuilder(Math.min(maxChars, 12_000));
        context.append("analysisMode=namespace-sectioned\n");
        context.append("section=").append(sectionName).append('\n');
        context.append("clusterId=").append(clusterId).append('\n');
        context.append("namespace=").append(namespace).append('\n');
        if (applicationName != null) {
            context.append("applicationName=").append(applicationName).append('\n');
        }
        context.append("metricsPolicy=Prometheus is not integrated. Do not invent CPU/memory/traffic metrics.\n");
        context.append("diagnosticCounts resources=").append(diagnostics.resources().size())
                .append(" events=").append(diagnostics.events().size())
                .append(" podLogs=").append(diagnostics.podLogs().size())
                .append(" collectedAt=").append(diagnostics.collectedAt())
                .append("\n\n");
        if (!diagnostics.collectionStages().isEmpty()) {
            context.append("collectionQuality=")
                    .append(diagnostics.partial() ? "PARTIAL" : "COMPLETE")
                    .append('\n');
            diagnostics.collectionStages().stream().limit(24).forEach(stage -> context.append("- source=")
                    .append(stage.source())
                    .append(" status=").append(stage.status())
                    .append(" items=").append(stage.itemCount())
                    .append(" latencyMs=").append(stage.latencyMs())
                    .append(stage.detail() == null || stage.detail().isBlank() ? "" : " detail=" + stage.detail())
                    .append('\n'));
            context.append("qualityRule=Do not claim complete namespace coverage when collectionQuality is PARTIAL.\n\n");
        }
        signalAppender.accept(context);
        return limit(context.toString(), maxChars);
    }

    private String limit(String context, int maxChars) {
        if (context.length() <= maxChars) {
            return context;
        }
        return context.substring(0, maxChars)
                + "\n[context truncated: originalChars=" + context.length()
                + ", maxChars=" + maxChars + "]";
    }
}
