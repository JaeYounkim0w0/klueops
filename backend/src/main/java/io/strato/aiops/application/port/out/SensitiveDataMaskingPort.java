package io.strato.aiops.application.port.out;

public interface SensitiveDataMaskingPort {

    /** SensitiveDataMaskingPort의 isSensitiveKey 처리 조건의 충족 여부를 판단한다. */
    boolean isSensitiveKey(String key);

    /** SensitiveDataMaskingPort의 maskValue 처리 계약을 정의한다. */
    String maskValue(String value);

    /** SensitiveDataMaskingPort의 maskText 처리 계약을 정의한다. */
    String maskText(String value);
}
