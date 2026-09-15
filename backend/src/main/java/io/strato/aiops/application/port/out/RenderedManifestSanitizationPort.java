package io.strato.aiops.application.port.out;

public interface RenderedManifestSanitizationPort {
    String sanitize(String manifest);
}
