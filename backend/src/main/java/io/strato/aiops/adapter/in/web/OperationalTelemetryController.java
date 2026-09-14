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

    public OperationalTelemetryController(OperationalTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Operation(summary = "Get current backend instance operational telemetry snapshot")
    @GetMapping
    public OperationalTelemetry.Snapshot snapshot() {
        return telemetry.snapshot();
    }
}
