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

    /** OperationalTelemetryInterceptor 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OperationalTelemetryInterceptor(OperationalTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    /** OperationalTelemetryInterceptor의 preHandle 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START, System.nanoTime());
        return true;
    }

    /** OperationalTelemetryInterceptor의 afterCompletion 처리에 필요한 업무 로직을 수행한다. */
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
