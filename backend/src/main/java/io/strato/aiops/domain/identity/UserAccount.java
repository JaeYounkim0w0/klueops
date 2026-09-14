package io.strato.aiops.domain.identity;

import java.time.Instant;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String issuer,
        String subject,
        String username,
        String displayName,
        String email,
        boolean active,
        Instant firstSeenAt,
        Instant lastLoginAt,
        Instant updatedAt
) {
    public UserAccount {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(issuer, "issuer is required");
        Objects.requireNonNull(subject, "subject is required");
        Objects.requireNonNull(username, "username is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(firstSeenAt, "firstSeenAt is required");
        Objects.requireNonNull(lastLoginAt, "lastLoginAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static UserAccount firstLogin(ExternalIdentity identity, Instant now) {
        return new UserAccount(UUID.randomUUID(), identity.issuer(), identity.subject(), identity.username(),
                identity.displayName(), identity.email(), true, now, now, now);
    }

    public UserAccount refresh(ExternalIdentity identity, Instant now) {
        return new UserAccount(id, issuer, subject, identity.username(), identity.displayName(), identity.email(),
                active, firstSeenAt, now, now);
    }

    public boolean needsRefresh(ExternalIdentity identity, Instant now, Duration interval) {
        return !username.equals(identity.username())
                || !displayName.equals(identity.displayName())
                || !Objects.equals(email, identity.email())
                || lastLoginAt.plus(interval).isBefore(now);
    }

    public UserAccount withActive(boolean enabled) {
        return new UserAccount(id, issuer, subject, username, displayName, email, enabled, firstSeenAt, lastLoginAt,
                updatedAt);
    }


    public UserAccount withActive(boolean enabled, Instant now) {
        return new UserAccount(id, issuer, subject, username, displayName, email, enabled, firstSeenAt, lastLoginAt,
                now);
    }
}
