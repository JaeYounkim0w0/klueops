package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.SupportedLocale;

import java.util.UUID;

public record AnalyzeClusterCommand(
        UUID clusterId,
        SupportedLocale locale
) {
    public AnalyzeClusterCommand(UUID clusterId) {
        this(clusterId, SupportedLocale.ENGLISH);
    }

    public AnalyzeClusterCommand {
        locale = locale == null ? SupportedLocale.ENGLISH : locale;
    }
}
