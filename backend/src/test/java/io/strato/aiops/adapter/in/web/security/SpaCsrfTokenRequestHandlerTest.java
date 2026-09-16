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

    /** SpaCsrfTokenRequestHandlerTest의 resolvesThePlainTokenFromTheSpaHeader 처리에 필요한 결과를 조합해 반환한다. */
    @Test
    void resolvesThePlainTokenFromTheSpaHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-XSRF-TOKEN", "plain-token");

        assertThat(handler.resolveCsrfTokenValue(request, token)).isEqualTo("plain-token");
    }

    /** SpaCsrfTokenRequestHandlerTest의 resolvesThePlainTokenFromTheTopLevelLogoutForm 처리에 필요한 결과를 조합해 반환한다. */
    @Test
    void resolvesThePlainTokenFromTheTopLevelLogoutForm() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/logout");
        request.setParameter("_csrf", "plain-token");

        assertThat(handler.resolveCsrfTokenValue(request, token)).isEqualTo("plain-token");
    }

    /** SpaCsrfTokenRequestHandlerTest의 loadsTheDeferredTokenSoTheCookieRepositoryCanIssueAFreshCookie 처리 결과를 조회해 반환한다. */
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
