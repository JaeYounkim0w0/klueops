package io.strato.aiops.application.port.out;

public interface SensitiveDataMaskingPort {

    boolean isSensitiveKey(String key);

    String maskValue(String value);

    String maskText(String value);
}
