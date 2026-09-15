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
    public ExternalIdentity(String issuer, String subject, String username, String displayName, String email,
                            Set<String> groups) {
        this(issuer, subject, username, displayName, email, false, groups);
    }
    public ExternalIdentity {
        Objects.requireNonNull(issuer, "issuer is required");
        Objects.requireNonNull(subject, "subject is required");
        username = normalized(username, subject);
        displayName = normalized(displayName, username);
        groups = groups == null ? Set.of() : Set.copyOf(groups);
    }

    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
