package io.strato.aiops.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import io.strato.aiops.adapter.in.web.security.SpaCsrfTokenRequestHandler;

import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class SecurityConfig {

    @Bean
    @Profile("local")
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Profile("security-oidc | security-oidc-test")
    SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                                ClientRegistrationRepository registrations,
                                                @Value("${aiops.security.session.absolute-timeout:8h}")
                                                Duration absoluteTimeout) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        OidcClientInitiatedLogoutSuccessHandler oidcLogout =
                new OidcClientInitiatedLogoutSuccessHandler(registrations);
        oidcLogout.setPostLogoutRedirectUri("{baseUrl}/login?reason=logout");
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/me",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/oauth2/**",
                                "/login/**",
                                "/ws/command-sessions/**",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2.defaultSuccessUrl("/", true))
                .logout(logout -> logout
                        .deleteCookies("SESSION", "XSRF-TOKEN")
                        .logoutSuccessHandler(oidcLogout))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemEntryPoint(objectMapper))
                        .accessDeniedHandler(problemAccessDeniedHandler(objectMapper)))
                .addFilterAfter(new SessionAbsoluteLifetimeFilter(absoluteTimeout), SecurityContextHolderFilter.class)
                .build();
    }

    @Bean
    @Profile("!local & !security-oidc & !security-oidc-test")
    SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/swagger-ui/**",
                                "/v3/api-docs/**", "/ws/command-sessions/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                .build();
    }

    private AuthenticationEntryPoint problemEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) -> writeProblem(
                objectMapper, response, HttpServletResponse.SC_UNAUTHORIZED,
                "Authentication required", "AUTHENTICATION_REQUIRED", request.getRequestURI());
    }

    private AccessDeniedHandler problemAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) -> writeProblem(
                objectMapper, response, HttpServletResponse.SC_FORBIDDEN,
                "Access denied", "ACCESS_DENIED", request.getRequestURI());
    }

    private void writeProblem(ObjectMapper objectMapper, HttpServletResponse response, int status,
                              String title, String code, String instance) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status);
        body.put("detail", title);
        body.put("instance", instance);
        body.put("code", code);
        body.put("timestamp", Instant.now());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
