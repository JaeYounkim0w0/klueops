package io.strato.aiops.application.port.out;

public interface RenderedManifestSanitizationPort {
    /** RenderedManifestSanitizationPort의 sanitize 처리 계약을 정의한다. */
    String sanitize(String manifest);
}
