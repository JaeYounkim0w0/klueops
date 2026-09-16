package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.SupportedLocale;

import java.util.UUID;

public record AnalyzeClusterCommand(
        UUID clusterId,
        SupportedLocale locale
) {
    /** AnalyzeClusterCommand 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AnalyzeClusterCommand(UUID clusterId) {
        this(clusterId, SupportedLocale.ENGLISH);
    }

    /** AnalyzeClusterCommand 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AnalyzeClusterCommand {
        locale = locale == null ? SupportedLocale.ENGLISH : locale;
    }
}
