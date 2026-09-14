package io.strato.aiops.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SessionAbsoluteLifetimeFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidatesTheSessionAndAuthenticationAfterTheAbsoluteDeadline() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "credential"));
        AtomicBoolean continued = new AtomicBoolean();

        new SessionAbsoluteLifetimeFilter(Duration.ZERO).doFilter(
                request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(continued).isTrue();
    }
}
