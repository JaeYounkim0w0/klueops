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

    public RuntimeReadinessController(ProductionReadinessService productionReadinessService) {
        this.productionReadinessService = productionReadinessService;
    }

    @Operation(summary = "Assess runtime production readiness")
    @GetMapping("/runtime-readiness")
    public ProductionReadinessService.RuntimeReadiness getRuntimeReadiness() {
        return productionReadinessService.assess();
    }
}
