package io.strato.aiops.domain.tenancy;

import java.util.Locale;
import java.util.regex.Pattern;

final class TenancyCode {
    private static final Pattern VALID = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    private TenancyCode() {
    }

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
