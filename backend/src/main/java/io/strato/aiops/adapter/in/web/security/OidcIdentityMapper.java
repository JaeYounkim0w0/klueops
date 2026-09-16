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

    /** OidcIdentityMapper 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OidcIdentityMapper(@Value("${aiops.security.oidc-groups-claim:groups}") String groupsClaim) {
        this.groupsClaim = groupsClaim;
    }

    /** OidcIdentityMapper의 map 처리 데이터를 필요한 표현으로 변환한다. */
    public ExternalIdentity map(OidcUser user) {
        String issuer = user.getIssuer() == null ? "unknown-issuer" : user.getIssuer().toString();
        String username = first(user.getClaimAsString("preferred_username"), user.getSubject());
        String displayName = first(user.getFullName(), username);
        return new ExternalIdentity(issuer, user.getSubject(), username, displayName, user.getEmail(),
                Boolean.TRUE.equals(user.getEmailVerified()), groups(user));
    }

    /** OidcIdentityMapper의 groups 처리에 필요한 업무 로직을 수행한다. */
    private Set<String> groups(OidcUser user) {
        Object claim = user.getClaim(groupsClaim);
        if (claim instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    /** OidcIdentityMapper의 first 처리에 필요한 업무 로직을 수행한다. */
    private String first(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
