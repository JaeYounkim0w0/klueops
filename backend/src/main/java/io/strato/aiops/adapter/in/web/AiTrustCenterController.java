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

    /** AiTrustCenterController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AiTrustCenterController(AiTrustCenterService service) {
        this.service = service;
    }

    /** AiTrustCenterController의 snapshot 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get a point-in-time AI trust evidence snapshot")
    @GetMapping
    public AiTrustCenterService.Snapshot snapshot() {
        return service.snapshot();
    }
}
