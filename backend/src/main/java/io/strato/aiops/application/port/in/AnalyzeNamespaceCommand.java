package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.SupportedLocale;

import java.util.UUID;

public record AnalyzeNamespaceCommand(
        UUID clusterId,
        String namespace,
        SupportedLocale locale
) {
    public AnalyzeNamespaceCommand(UUID clusterId, String namespace) {
        this(clusterId, namespace, SupportedLocale.ENGLISH);
    }

    public AnalyzeNamespaceCommand {
        locale = locale == null ? SupportedLocale.ENGLISH : locale;
    }
}
