package io.strato.aiops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
public class OidcClientConfiguration {

    /** OidcClientConfiguration의 explicitOidcClientRegistrationRepository 처리에 필요한 업무 로직을 수행한다. */
    @Bean
    @ConditionalOnProperty(
            prefix = "aiops.security.oidc-provider",
            name = "explicit-endpoints-enabled",
            havingValue = "true"
    )
    ClientRegistrationRepository explicitOidcClientRegistrationRepository(
            @Value("${spring.security.oauth2.client.registration.aiops.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.aiops.client-secret}") String clientSecret,
            @Value("${spring.security.oauth2.client.registration.aiops.redirect-uri}") String redirectUri,
            @Value("${spring.security.oauth2.client.registration.aiops.scope:openid,profile,email}") String scopes,
            @Value("${aiops.security.oidc-provider.issuer-uri}") String issuerUri,
            @Value("${aiops.security.oidc-provider.authorization-uri}") String authorizationUri,
            @Value("${aiops.security.oidc-provider.token-uri}") String tokenUri,
            @Value("${aiops.security.oidc-provider.jwk-set-uri}") String jwkSetUri,
            @Value("${aiops.security.oidc-provider.user-info-uri}") String userInfoUri,
            @Value("${aiops.security.oidc-provider.user-name-attribute:sub}") String userNameAttribute
    ) {
        requireNonBlank("issuer URI", issuerUri);
        requireNonBlank("authorization URI", authorizationUri);
        requireNonBlank("token URI", tokenUri);
        requireNonBlank("JWK Set URI", jwkSetUri);
        requireNonBlank("user-info URI", userInfoUri);

        ClientRegistration registration = ClientRegistration.withRegistrationId("aiops")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope(parseScopes(scopes))
                .authorizationUri(authorizationUri)
                .tokenUri(tokenUri)
                .jwkSetUri(jwkSetUri)
                .issuerUri(issuerUri)
                .userInfoUri(userInfoUri)
                .userNameAttributeName(userNameAttribute)
                .clientName("AIOps")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    /** OidcClientConfiguration의 parseScopes 처리 데이터를 필요한 표현으로 변환한다. */
    private Set<String> parseScopes(String scopes) {
        Set<String> parsed = new LinkedHashSet<>();
        Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .forEach(parsed::add);
        if (!parsed.contains("openid")) {
            throw new IllegalStateException("OIDC scopes must include openid");
        }
        return Set.copyOf(parsed);
    }

    /** OidcClientConfiguration의 requireNonBlank 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Explicit OIDC " + name + " is required");
        }
    }
}

