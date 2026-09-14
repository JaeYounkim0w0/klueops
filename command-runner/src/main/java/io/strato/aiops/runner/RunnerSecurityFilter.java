package io.strato.aiops.runner;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
final class RunnerSecurityFilter extends OncePerRequestFilter {
    private final byte[] expected;

    RunnerSecurityFilter(@Value("${runner.token}") String token) {
        if (token == null || token.length() < 32) throw new IllegalStateException("Runner token must contain at least 32 characters");
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/")) {
            chain.doFilter(request, response);
            return;
        }
        String supplied = request.getHeader("X-AIOPS-Runner-Token");
        if (supplied == null || !MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
