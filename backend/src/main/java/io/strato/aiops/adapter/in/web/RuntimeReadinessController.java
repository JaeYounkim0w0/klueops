package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.ProductionReadinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Operations", description = "Runtime operations and production-readiness APIs")
@RestController
@RequestMapping("/api/operations")
public class RuntimeReadinessController {

    private final ProductionReadinessService productionReadinessService;

    /** RuntimeReadinessController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public RuntimeReadinessController(ProductionReadinessService productionReadinessService) {
        this.productionReadinessService = productionReadinessService;
    }

    /** RuntimeReadinessController의 getRuntimeReadiness 처리 결과를 조회해 반환한다. */
    @Operation(summary = "Assess runtime production readiness")
    @GetMapping("/runtime-readiness")
    public ProductionReadinessService.RuntimeReadiness getRuntimeReadiness() {
        return productionReadinessService.assess();
    }
}
