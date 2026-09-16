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

    /** SessionAbsoluteLifetimeFilter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    SessionAbsoluteLifetimeFilter(Duration absoluteTimeout) {
        this.absoluteTimeout = absoluteTimeout;
    }

    /** SessionAbsoluteLifetimeFilter의 doFilterInternal 처리에 필요한 업무 로직을 수행한다. */
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

    /** SessionAbsoluteLifetimeFilter의 isExpired 처리 조건의 충족 여부를 판단한다. */
    private boolean isExpired(HttpSession session) {
        Instant deadline = Instant.ofEpochMilli(session.getCreationTime()).plus(absoluteTimeout);
        return !Instant.now().isBefore(deadline);
    }
}
