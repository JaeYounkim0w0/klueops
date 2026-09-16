package io.strato.aiops.domain.identity;

import java.util.Objects;
import java.util.Set;

public record ExternalIdentity(
        String issuer,
        String subject,
        String username,
        String displayName,
        String email,
        boolean emailVerified,
        Set<String> groups
) {
    /** ExternalIdentity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ExternalIdentity(String issuer, String subject, String username, String displayName, String email,
                            Set<String> groups) {
        this(issuer, subject, username, displayName, email, false, groups);
    }
    /** ExternalIdentity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ExternalIdentity {
        Objects.requireNonNull(issuer, "issuer is required");
        Objects.requireNonNull(subject, "subject is required");
        username = normalized(username, subject);
        displayName = normalized(displayName, username);
        groups = groups == null ? Set.of() : Set.copyOf(groups);
    }

    /** ExternalIdentity의 normalized 처리 데이터를 필요한 표현으로 변환한다. */
    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
