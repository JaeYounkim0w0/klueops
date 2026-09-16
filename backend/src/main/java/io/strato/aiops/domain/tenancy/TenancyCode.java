package io.strato.aiops.domain.tenancy;

import java.util.Locale;
import java.util.regex.Pattern;

final class TenancyCode {
    private static final Pattern VALID = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    /** TenancyCode 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private TenancyCode() {
    }

    /** TenancyCode의 normalize 처리 데이터를 필요한 표현으로 변환한다. */
    static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("code is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code must contain only lowercase letters, numbers, and internal hyphens");
        }
        return normalized;
    }
}
