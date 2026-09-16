package io.strato.aiops.domain.applicationdelivery;

import java.util.Locale;

/**
 * Helm Application이 외부 접근 경로를 소유하는 방식을 구분한다.
 */
public enum ApplicationExposureMode {
    NONE,
    CHART_MANAGED,
    HTTP_ROUTE,
    INGRESS,
    TCP_ROUTE;

    /** ApplicationExposureMode의 fromNullable 처리 데이터를 필요한 표현으로 변환한다. */
    public static ApplicationExposureMode fromNullable(String value) {
        if (value == null || value.isBlank()) return NONE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported exposure type", exception);
        }
    }

    /** ApplicationExposureMode의 requiresExposureCapability 처리 입력과 현재 상태의 유효성을 검증한다. */
    public boolean requiresExposureCapability() {
        return this != NONE;
    }
}
