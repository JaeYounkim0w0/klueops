package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.SupportedLocale;

import java.util.UUID;

public record AnalyzeNamespaceCommand(
        UUID clusterId,
        String namespace,
        SupportedLocale locale
) {
    /** AnalyzeNamespaceCommand 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AnalyzeNamespaceCommand(UUID clusterId, String namespace) {
        this(clusterId, namespace, SupportedLocale.ENGLISH);
    }

    /** AnalyzeNamespaceCommand 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AnalyzeNamespaceCommand {
        locale = locale == null ? SupportedLocale.ENGLISH : locale;
    }
}
