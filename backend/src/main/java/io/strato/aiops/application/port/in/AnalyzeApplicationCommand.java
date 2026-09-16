package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.SupportedLocale;

import java.util.UUID;

public record AnalyzeApplicationCommand(
        UUID applicationId,
        SupportedLocale locale
) {
    /** AnalyzeApplicationCommand 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AnalyzeApplicationCommand(UUID applicationId) {
        this(applicationId, SupportedLocale.ENGLISH);
    }

    /** AnalyzeApplicationCommand 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AnalyzeApplicationCommand {
        locale = locale == null ? SupportedLocale.ENGLISH : locale;
    }
}
