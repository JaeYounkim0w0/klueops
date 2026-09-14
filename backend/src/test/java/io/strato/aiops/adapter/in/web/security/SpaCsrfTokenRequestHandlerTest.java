package io.strato.aiops.adapter.in.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SpaCsrfTokenRequestHandlerTest {

    private final SpaCsrfTokenRequestHandler handler = new SpaCsrfTokenRequestHandler();
    private final DefaultCsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "plain-token");

    @Test
    void resolvesThePlainTokenFromTheSpaHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-XSRF-TOKEN", "plain-token");

        assertThat(handler.resolveCsrfTokenValue(request, token)).isEqualTo("plain-token");
    }

    @Test
    void resolvesThePlainTokenFromTheTopLevelLogoutForm() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/logout");
        request.setParameter("_csrf", "plain-token");

        assertThat(handler.resolveCsrfTokenValue(request, token)).isEqualTo("plain-token");
    }

    @Test
    void loadsTheDeferredTokenSoTheCookieRepositoryCanIssueAFreshCookie() {
        AtomicBoolean loaded = new AtomicBoolean();

        handler.handle(new MockHttpServletRequest(), new MockHttpServletResponse(), () -> {
            loaded.set(true);
            return token;
        });

        assertThat(loaded).isTrue();
    }
}
