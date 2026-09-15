package io.strato.aiops.adapter.in.web.security;

import io.strato.aiops.domain.identity.ExternalIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class OidcIdentityMapper {
    private final String groupsClaim;

    public OidcIdentityMapper(@Value("${aiops.security.oidc-groups-claim:groups}") String groupsClaim) {
        this.groupsClaim = groupsClaim;
    }

    public ExternalIdentity map(OidcUser user) {
        String issuer = user.getIssuer() == null ? "unknown-issuer" : user.getIssuer().toString();
        String username = first(user.getClaimAsString("preferred_username"), user.getSubject());
        String displayName = first(user.getFullName(), username);
        return new ExternalIdentity(issuer, user.getSubject(), username, displayName, user.getEmail(),
                Boolean.TRUE.equals(user.getEmailVerified()), groups(user));
    }

    private Set<String> groups(OidcUser user) {
        Object claim = user.getClaim(groupsClaim);
        if (claim instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    private String first(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
