package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.ProductionEvidenceService;
import io.strato.aiops.domain.operations.ProductionEvidence;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Production Evidence", description = "Persistent commercial acceptance and environment evidence")
@RestController
@RequestMapping("/api/operations/production-evidence")
public class ProductionEvidenceController {

    private final ProductionEvidenceService service;

    public ProductionEvidenceController(ProductionEvidenceService service) {
        this.service = service;
    }

    @Operation(summary = "Import a completed production evidence run")
    @PostMapping("/runs")
    public ProductionEvidence.Run importRun(@RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
                                            @Valid @RequestBody ImportRequest body, HttpServletRequest request) {
        return service.importEvidence(idempotencyKey, body.releaseName(), body.environment(), actor(request),
                body.checks().stream().map(CheckRequest::toInput).toList());
    }

    @Operation(summary = "List recent production evidence runs")
    @GetMapping("/runs")
    public List<ProductionEvidence.Run> runs(@RequestParam(defaultValue = "20") int limit) {
        return service.recent(limit);
    }

    @Operation(summary = "Get a production evidence run with checks and artifact metadata")
    @GetMapping("/runs/{runId}")
    public ProductionEvidence.Run run(@PathVariable UUID runId) {
        return service.get(runId);
    }

    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }

    public record ImportRequest(@NotBlank String releaseName, @NotBlank String environment,
                                @NotEmpty List<@Valid CheckRequest> checks) {
    }

    public record CheckRequest(@NotBlank String category, @NotBlank String code, @NotBlank String state,
                               @NotBlank String title, String detail, String observedValue, String action,
                               long durationMs, List<@Valid ArtifactRequest> artifacts) {
        ProductionEvidenceService.CheckInput toInput() {
            return new ProductionEvidenceService.CheckInput(category, code, state, title, detail, observedValue,
                    action, durationMs, artifacts == null ? List.of() : artifacts.stream().map(ArtifactRequest::toInput).toList());
        }
    }

    public record ArtifactRequest(@NotBlank String fileName, @NotBlank String mediaType,
                                  String content, String reference) {
        ProductionEvidenceService.ArtifactInput toInput() {
            return new ProductionEvidenceService.ArtifactInput(fileName, mediaType, content, reference);
        }
    }
}
