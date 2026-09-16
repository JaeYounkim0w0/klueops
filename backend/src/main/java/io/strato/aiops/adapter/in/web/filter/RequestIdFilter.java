package io.strato.aiops.adapter.in.web.filter;

import io.strato.aiops.adapter.in.web.RequestAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    /** RequestIdFilter의 doFilterInternal 처리에 필요한 업무 로직을 수행한다. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = Optional.ofNullable(request.getHeader(RequestAttributes.HEADER_REQUEST_ID))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
        String correlationId = Optional.ofNullable(request.getHeader(RequestAttributes.HEADER_CORRELATION_ID))
                .filter(value -> !value.isBlank())
                .orElse(requestId);

        request.setAttribute(RequestAttributes.REQUEST_ID, requestId);
        request.setAttribute(RequestAttributes.CORRELATION_ID, correlationId);
        response.setHeader(RequestAttributes.HEADER_REQUEST_ID, requestId);
        response.setHeader(RequestAttributes.HEADER_CORRELATION_ID, correlationId);

        MDC.put(RequestAttributes.REQUEST_ID, requestId);
        MDC.put(RequestAttributes.CORRELATION_ID, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestAttributes.REQUEST_ID);
            MDC.remove(RequestAttributes.CORRELATION_ID);
        }
    }
}

