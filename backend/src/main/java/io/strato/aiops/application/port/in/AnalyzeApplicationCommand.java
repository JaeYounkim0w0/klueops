package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.SupportedLocale;

import java.util.UUID;

public record AnalyzeApplicationCommand(
        UUID applicationId,
        SupportedLocale locale
) {
    public AnalyzeApplicationCommand(UUID applicationId) {
        this(applicationId, SupportedLocale.ENGLISH);
    }

    public AnalyzeApplicationCommand {
        locale = locale == null ? SupportedLocale.ENGLISH : locale;
    }
}
