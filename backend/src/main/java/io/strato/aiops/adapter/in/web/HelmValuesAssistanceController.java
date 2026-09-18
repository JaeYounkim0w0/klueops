package io.strato.aiops.adapter.in.web;

import io.strato.aiops.adapter.in.web.security.CurrentAccessResolver;
import io.strato.aiops.application.service.HelmValuesAssistanceService;
import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.TenantFeatureGuard;
import io.strato.aiops.application.service.ValuesAssistanceJobService;
import io.strato.aiops.adapter.in.web.dto.JobResponse;
import java.util.UUID;
import java.util.Map;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.identity.FeatureKey;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 요청 해석 및 보충 질문을 제공하는 Values 도우미 API이다. */
@RestController
@RequestMapping("/api/v2/application-delivery")
public class HelmValuesAssistanceController {
    private final HelmValuesAssistanceService service;
    private final CurrentAccessResolver access;
    private final IdentityAccessService identity;
    private final TenantFeatureGuard features;
    private final ValuesAssistanceJobService jobs;

    public HelmValuesAssistanceController(HelmValuesAssistanceService service, CurrentAccessResolver access,
                                          IdentityAccessService identity, TenantFeatureGuard features, ValuesAssistanceJobService jobs) {
        this.service = service; this.access = access; this.identity = identity; this.features = features;
        this.jobs = jobs;
    }

    /** 빠르게 Job ID를 반환하여 모델 생성 시간이 HTTP timeout에 묶이지 않게 한다. */
    @PostMapping("/values-assistance/jobs")
    @Operation(summary = "Start an owner-scoped asynchronous Values assistance job")
    public Map<String, UUID> start(@Valid @RequestBody ApplicationDeliveryCatalogController.ValuesSuggestionRequest request,
                                   Authentication authentication) {
        authorize(request.tenantId(), authentication);
        return Map.of("jobId", jobs.submit(request.tenantId(), authentication.getName(), request.chartVersionId(),
                request.currentValuesYaml(), request.instruction()));
    }

    /** Tenant 권한과 원래 요청한 사용자를 모두 확인하여 상태를 조회한다. */
    @GetMapping("/values-assistance/jobs/{id}")
    @Operation(summary = "Read the requesting user's Values assistance job status")
    public JobResponse status(@PathVariable UUID id, @RequestParam UUID tenantId, Authentication authentication) {
        authorize(tenantId, authentication);
        return JobResponse.from(jobs.status(id, tenantId, authentication.getName()));
    }

    /** 결과는 일반 Job 목록에 포함하지 않고 별도 권한 확인 후 반환한다. */
    @GetMapping("/values-assistance/jobs/{id}/result")
    @Operation(summary = "Read the requesting user's decrypted Values proposal")
    public ValuesAssistanceJobService.Outcome result(@PathVariable UUID id, @RequestParam UUID tenantId, Authentication authentication) {
        authorize(tenantId, authentication);
        return jobs.result(id, tenantId, authentication.getName());
    }

    /** 모든 생성·조회 요청에서 현재 Tenant의 Values 편집 권한을 재검사한다. */
    private void authorize(UUID tenant, Authentication authentication) {
        features.requireEnabled(tenant, FeatureKey.APPLICATION_DELIVERY);
        if (!identity.allowsTenant(access.resolve(authentication), Capability.VALUES_EDIT, tenant))
            throw new org.springframework.security.access.AccessDeniedException("Values 편집 권한이 필요합니다.");
    }

    /** 동일 Tenant의 Values 편집 권한을 확인한 뒤 생성 또는 입력 보완 결과를 반환한다. */
    @PostMapping("/values-assistance")
    @Operation(summary = "Interpret requirements and map exact Chart Values, or return clarification questions")
    public HelmValuesAssistanceService.Result assist(
            @Valid @RequestBody ApplicationDeliveryCatalogController.ValuesSuggestionRequest request,
            Authentication authentication) {
        features.requireEnabled(request.tenantId(), FeatureKey.APPLICATION_DELIVERY);
        var actor = access.resolve(authentication);
        if (!identity.allowsTenant(actor, Capability.VALUES_EDIT, request.tenantId()))
            throw new org.springframework.security.access.AccessDeniedException("Values 편집 권한이 필요합니다.");
        return service.assist(request.tenantId(), request.chartVersionId(), request.currentValuesYaml(), request.instruction());
    }
}
