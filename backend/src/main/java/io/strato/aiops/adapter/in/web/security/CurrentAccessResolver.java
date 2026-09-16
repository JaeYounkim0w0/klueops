package io.strato.aiops.adapter.in.web.security;

import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import io.strato.aiops.domain.identity.ExternalIdentity;
import io.strato.aiops.domain.identity.UserAccount;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccessResolver {
    private final IdentityAccessService identityAccessService;
    private final OidcIdentityMapper identityMapper;

    /** CurrentAccessResolver 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public CurrentAccessResolver(IdentityAccessService identityAccessService, OidcIdentityMapper identityMapper) {
        this.identityAccessService = identityAccessService;
        this.identityMapper = identityMapper;
    }

    /** CurrentAccessResolver의 resolve 처리에 필요한 결과를 조합해 반환한다. */
    public ResolvedAccess resolve(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new AccessDeniedException("An OIDC platform account is required");
        }
        ExternalIdentity identity = identityMapper.map(oidcUser);
        UserAccount user = identityAccessService.provision(identity);
        return identityAccessService.resolveAccess(user, identity.groups());
    }

}
