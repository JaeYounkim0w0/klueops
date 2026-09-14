package io.strato.aiops.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

final class SessionAbsoluteLifetimeFilter extends OncePerRequestFilter {

    private final Duration absoluteTimeout;

    SessionAbsoluteLifetimeFilter(Duration absoluteTimeout) {
        this.absoluteTimeout = absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && isExpired(session)) {
            session.invalidate();
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    private boolean isExpired(HttpSession session) {
        Instant deadline = Instant.ofEpochMilli(session.getCreationTime()).plus(absoluteTimeout);
        return !Instant.now().isBefore(deadline);
    }
}
