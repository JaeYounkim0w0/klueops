package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.OperationalTelemetry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Component
public class OperationalTelemetryInterceptor implements HandlerInterceptor {

    private static final String START = OperationalTelemetryInterceptor.class.getName() + ".start";
    private final OperationalTelemetry telemetry;

    public OperationalTelemetryInterceptor(OperationalTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        Object started = request.getAttribute(START);
        if (!(started instanceof Long start)) return;
        String outcome = exception == null ? Integer.toString(response.getStatus())
                : exception.getClass().getSimpleName();
        telemetry.record(request.getRequestURI(), outcome, Duration.ofNanos(System.nanoTime() - start));
    }
}
