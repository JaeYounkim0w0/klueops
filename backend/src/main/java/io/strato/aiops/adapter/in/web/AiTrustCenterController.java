package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.AiTrustCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Trust Center", description = "Consistent AI quality, calibration, benchmark and release evidence snapshot")
@RestController
@RequestMapping("/api/operations/ai-trust")
public class AiTrustCenterController {

    private final AiTrustCenterService service;

    public AiTrustCenterController(AiTrustCenterService service) {
        this.service = service;
    }

    @Operation(summary = "Get a point-in-time AI trust evidence snapshot")
    @GetMapping
    public AiTrustCenterService.Snapshot snapshot() {
        return service.snapshot();
    }
}
