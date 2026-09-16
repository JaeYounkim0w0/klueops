package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.OperationalTelemetry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Operations Telemetry", description = "Bounded-cardinality instance operational telemetry")
@RestController
@RequestMapping("/api/operations/telemetry")
public class OperationalTelemetryController {

    private final OperationalTelemetry telemetry;

    /** OperationalTelemetryController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OperationalTelemetryController(OperationalTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    /** OperationalTelemetryController의 snapshot 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get current backend instance operational telemetry snapshot")
    @GetMapping
    public OperationalTelemetry.Snapshot snapshot() {
        return telemetry.snapshot();
    }
}
