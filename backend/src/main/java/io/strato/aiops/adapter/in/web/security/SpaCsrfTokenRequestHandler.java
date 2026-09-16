package io.strato.aiops.adapter.in.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    /** SpaCsrfTokenRequestHandler의 handle 처리에서 발생한 이벤트와 후속 동작을 처리한다. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        csrfToken.get();
    }

    /** SpaCsrfTokenRequestHandler의 resolveCsrfTokenValue 처리에 필요한 결과를 조합해 반환한다. */
    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        boolean spaHeader = StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()));
        boolean spaLogoutForm = "/logout".equals(request.getRequestURI());
        return (spaHeader || spaLogoutForm ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
