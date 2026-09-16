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

    /** SessionAbsoluteLifetimeFilterTest의 clearSecurityContext 처리 대상과 관련 상태를 안전하게 정리한다. */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** SessionAbsoluteLifetimeFilterTest의 invalidatesTheSessionAndAuthenticationAfterTheAbsoluteDeadline 처리에 필요한 업무 로직을 수행한다. */
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
