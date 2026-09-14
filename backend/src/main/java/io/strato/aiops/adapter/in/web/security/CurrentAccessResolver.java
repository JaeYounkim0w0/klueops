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

    public CurrentAccessResolver(IdentityAccessService identityAccessService, OidcIdentityMapper identityMapper) {
        this.identityAccessService = identityAccessService;
        this.identityMapper = identityMapper;
    }

    public ResolvedAccess resolve(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new AccessDeniedException("An OIDC platform account is required");
        }
        ExternalIdentity identity = identityMapper.map(oidcUser);
        UserAccount user = identityAccessService.provision(identity);
        return identityAccessService.resolveAccess(user, identity.groups());
    }

}
