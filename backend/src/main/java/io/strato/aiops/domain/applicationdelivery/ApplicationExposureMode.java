package io.strato.aiops.domain.applicationdelivery;

import java.util.Locale;

/**
 * Helm Application이 외부 접근 경로를 소유하는 방식을 구분한다.
 */
public enum ApplicationExposureMode {
    NONE,
    CHART_MANAGED,
    HTTP_ROUTE;

    public static ApplicationExposureMode fromNullable(String value) {
        if (value == null || value.isBlank()) return NONE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported exposure type", exception);
        }
    }

    public boolean requiresExposureCapability() {
        return this != NONE;
    }
}
