package io.strato.aiops.adapter.in.web;

import io.strato.aiops.adapter.in.web.security.CurrentAccessResolver;
import io.strato.aiops.application.port.out.ChartCatalogPort;
import io.strato.aiops.application.service.ApplicationDeliveryCatalogService;
import io.strato.aiops.application.service.HelmValuesSuggestionService;
import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import io.strato.aiops.application.service.TenantFeatureGuard;
import io.strato.aiops.domain.applicationdelivery.ChartVersion;
import io.strato.aiops.domain.applicationdelivery.ChartSource;
import io.strato.aiops.domain.applicationdelivery.ChartSourceType;
import io.strato.aiops.domain.applicationdelivery.TenantChart;
import io.strato.aiops.domain.applicationdelivery.ValuesProfile;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.identity.FeatureKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/application-delivery")
@Tag(name = "Application Delivery Catalog", description = "Artifact Hub discovery, tenant chart library and values profiles")
public class ApplicationDeliveryCatalogController {
    private final ApplicationDeliveryCatalogService catalogService;
    private final CurrentAccessResolver currentAccessResolver;
    private final IdentityAccessService identityAccessService;
    private final TenantFeatureGuard featureGuard;

    /** ApplicationDeliveryCatalogController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ApplicationDeliveryCatalogController(ApplicationDeliveryCatalogService catalogService,
                                                CurrentAccessResolver currentAccessResolver,
                                                IdentityAccessService identityAccessService,
                                                TenantFeatureGuard featureGuard) {
        this.catalogService = catalogService;
        this.currentAccessResolver = currentAccessResolver;
        this.identityAccessService = identityAccessService;
        this.featureGuard = featureGuard;
    }

    /** ApplicationDeliveryCatalogController의 search 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Search Helm charts in Artifact Hub")
    @GetMapping("/catalog/search")
    public List<CatalogPackageResponse> search(@RequestParam UUID tenantId, @RequestParam(defaultValue = "") String query,
                                               @RequestParam(defaultValue = "20") int limit,
                                               Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_READ);
        return catalogService.search(query, limit).stream().map(CatalogPackageResponse::from).toList();
    }

    /** ApplicationDeliveryCatalogController의 sources 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List tenant Helm and OCI sources")
    @GetMapping("/sources")
    public List<ChartSourceResponse> sources(@RequestParam UUID tenantId, Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_READ);
        return catalogService.sources(tenantId).stream().map(ChartSourceResponse::from).toList();
    }

    /** ApplicationDeliveryCatalogController의 createSource 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Operation(summary = "Add a tenant Helm or OCI source")
    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public ChartSourceResponse createSource(@Valid @RequestBody CreateChartSourceRequest request,
                                            Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.CHART_MANAGE);
        return ChartSourceResponse.from(catalogService.createSource(request.tenantId(), request.sourceType(),
                request.name(), request.endpoint(), request.credential(), actor.user().id().toString()));
    }

    /** ApplicationDeliveryCatalogController의 updateSource 처리 대상의 상태를 갱신한다. */
    @PatchMapping("/sources/{sourceId}")
    public ChartSourceResponse updateSource(@PathVariable UUID sourceId,
                                            @Valid @RequestBody UpdateChartSourceRequest request,
                                            Authentication authentication) {
        require(authentication, request.tenantId(), Capability.CHART_MANAGE);
        return ChartSourceResponse.from(catalogService.updateSource(request.tenantId(), sourceId, request.name(),
                request.endpoint(), request.credential(), request.enabled()));
    }

    /** ApplicationDeliveryCatalogController의 deleteSource 처리 대상과 관련 상태를 안전하게 정리한다. */
    @DeleteMapping("/sources/{sourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSource(@PathVariable UUID sourceId, @RequestParam UUID tenantId,
                             Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_MANAGE);
        catalogService.deleteSource(tenantId, sourceId);
    }

    /** ApplicationDeliveryCatalogController의 details 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get an Artifact Hub Helm chart version")
    @GetMapping("/catalog/packages/{repository}/{name}")
    public CatalogPackageResponse details(@PathVariable String repository, @PathVariable String name,
                                          @RequestParam UUID tenantId,
                                          @RequestParam(required = false) String version,
                                          Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_READ);
        return CatalogPackageResponse.from(catalogService.details(repository, name, version));
    }

    /** ApplicationDeliveryCatalogController의 importChart 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Import an Artifact Hub Helm chart into the tenant library")
    @PostMapping("/charts/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportedChartResponse importChart(@Valid @RequestBody ImportChartRequest request,
                                             Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.CHART_IMPORT);
        return ImportedChartResponse.from(catalogService.importFromCatalog(request.tenantId(), request.repository(),
                request.name(), request.version(), actor.user().id().toString()));
    }

    /** ApplicationDeliveryCatalogController의 upload 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Upload a Helm chart archive into the tenant library")
    @PostMapping(value = "/charts/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportedChartResponse upload(@RequestParam UUID tenantId,
                                        @RequestParam(defaultValue = "manual-upload") String sourceName,
                                        @RequestParam MultipartFile file,
                                        Authentication authentication) throws IOException {
        ResolvedAccess actor = require(authentication, tenantId, Capability.CHART_IMPORT);
        return ImportedChartResponse.from(catalogService.upload(tenantId, sourceName, file.getBytes(),
                actor.user().id().toString()));
    }

    /** ApplicationDeliveryCatalogController의 charts 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List charts in the selected tenant library")
    @GetMapping("/charts")
    public List<LibraryChartResponse> charts(@RequestParam UUID tenantId,
                                             @RequestParam(defaultValue = "false") boolean includeArchived,
                                             Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_READ);
        return catalogService.library(tenantId, includeArchived).stream().map(LibraryChartResponse::from).toList();
    }

    /** ApplicationDeliveryCatalogController의 removeChart 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Operation(summary = "Remove a Helm chart from the selected tenant library")
    @DeleteMapping("/charts/{chartId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeChart(@PathVariable UUID chartId, @Valid @RequestBody RemoveChartRequest request,
                            Authentication authentication) {
        require(authentication, request.tenantId(), Capability.CHART_MANAGE);
        catalogService.removeChart(request.tenantId(), chartId, request.confirmationText());
    }

    /** ApplicationDeliveryCatalogController의 createProfile 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Operation(summary = "Create a reusable custom values profile")
    @PostMapping("/values-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ValuesProfileResponse createProfile(@Valid @RequestBody CreateValuesProfileRequest request,
                                               Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.VALUES_EDIT);
        return ValuesProfileResponse.from(catalogService.createProfile(request.tenantId(), request.chartVersionId(),
                request.name(), request.description(), actor.user().id().toString()));
    }

    /** ApplicationDeliveryCatalogController의 profiles 처리에 필요한 업무 로직을 수행한다. */
    @GetMapping("/values-profiles")
    public List<ValuesProfileResponse> profiles(@RequestParam UUID tenantId, @RequestParam UUID chartVersionId,
                                                Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_READ);
        return catalogService.profiles(tenantId, chartVersionId).stream().map(ValuesProfileResponse::from).toList();
    }

    /** ApplicationDeliveryCatalogController의 createRevision 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Operation(summary = "Create an immutable custom values revision")
    @PostMapping("/values-profiles/{profileId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public ValuesRevisionResponse createRevision(@PathVariable UUID profileId,
                                                 @Valid @RequestBody CreateValuesRevisionRequest request,
                                                 Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.VALUES_EDIT);
        return ValuesRevisionResponse.from(catalogService.createRevision(request.tenantId(), profileId,
                request.valuesYaml(), actor.user().id().toString()));
    }

    /** ApplicationDeliveryCatalogController의 revisions 처리에 필요한 업무 로직을 수행한다. */
    @GetMapping("/values-profiles/{profileId}/revisions")
    public List<ValuesRevisionResponse> revisions(@PathVariable UUID profileId, @RequestParam UUID tenantId,
                                                  Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_READ);
        return catalogService.revisions(tenantId, profileId).stream().map(ValuesRevisionResponse::from).toList();
    }

    /** ApplicationDeliveryCatalogController의 values 처리에 필요한 업무 로직을 수행한다. */
    @GetMapping("/values-revisions/{revisionId}/values")
    public ValuesPayloadResponse values(@PathVariable UUID revisionId, @RequestParam UUID tenantId,
                                        Authentication authentication) {
        require(authentication, tenantId, Capability.VALUES_EDIT);
        return new ValuesPayloadResponse(catalogService.values(tenantId, revisionId));
    }

    /** Chart 기본 Values와 JSON Schema를 Form/YAML 양방향 편집기에 제공한다. */
    @GetMapping("/chart-versions/{chartVersionId}/values-contract")
    public ValuesContractResponse valuesContract(@PathVariable UUID chartVersionId, @RequestParam UUID tenantId,
                                                  Authentication authentication) {
        require(authentication, tenantId, Capability.VALUES_EDIT);
        var contract = catalogService.valuesContract(tenantId, chartVersionId);
        return new ValuesContractResponse(contract.defaultValuesYaml(), contract.valuesSchemaJson(),
                contract.schemaIncluded());
    }

    /** ApplicationDeliveryCatalogController의 suggestValues 처리에 필요한 업무 로직을 수행한다. */
    @PostMapping("/values-suggestions")
    @Operation(summary = "Suggest and Helm-validate custom Values for an exact chart version")
    public ValuesSuggestionResponse suggestValues(@Valid @RequestBody ValuesSuggestionRequest request,
                                                   Authentication authentication) {
        require(authentication, request.tenantId(), Capability.VALUES_EDIT);
        return ValuesSuggestionResponse.from(catalogService.suggestValues(request.tenantId(), request.chartVersionId(),
                request.currentValuesYaml(), request.instruction()));
    }

    /** ApplicationDeliveryCatalogController의 require 처리 입력과 현재 상태의 유효성을 검증한다. */
    private ResolvedAccess require(Authentication authentication, UUID tenantId, Capability capability) {
        featureGuard.requireEnabled(tenantId, FeatureKey.APPLICATION_DELIVERY);
        ResolvedAccess access = currentAccessResolver.resolve(authentication);
        if (!identityAccessService.allowsTenant(access, capability, tenantId)) {
            throw new AccessDeniedException(capability.value() + " capability is not granted for this tenant");
        }
        return access;
    }

    public record ImportChartRequest(@NotNull UUID tenantId, @NotBlank String repository,
                                     @NotBlank String name, @NotBlank String version) {
    }
    public record CreateValuesProfileRequest(@NotNull UUID tenantId, @NotNull UUID chartVersionId,
                                             @NotBlank String name, String description) {
    }
    public record CreateValuesRevisionRequest(@NotNull UUID tenantId, @NotBlank String valuesYaml) {
    }
    public record RemoveChartRequest(@NotNull UUID tenantId, @NotBlank String confirmationText) {
    }
    public record CreateChartSourceRequest(@NotNull UUID tenantId, @NotNull ChartSourceType sourceType,
                                           @NotBlank String name, @NotBlank String endpoint, String credential) {
    }
    public record UpdateChartSourceRequest(@NotNull UUID tenantId, @NotBlank String name,
                                           @NotBlank String endpoint, String credential, boolean enabled) {
    }
    public record ValuesPayloadResponse(String valuesYaml) {
    }
    public record ValuesContractResponse(String defaultValuesYaml, String valuesSchemaJson, boolean schemaIncluded) {
    }
    public record ValuesSuggestionResponse(String valuesYaml, String promptVersion, String validationStatus,
                                           int attempts, String chartName, String providerName, String chartVersion,
                                           String applicationVersion, boolean schemaIncluded) {
        /** ValuesSuggestionResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static ValuesSuggestionResponse from(HelmValuesSuggestionService.SuggestionResult result) {
            return new ValuesSuggestionResponse(result.valuesYaml(), result.promptVersion(), result.validationStatus(),
                    result.attempts(), result.chartName(), result.providerName(), result.chartVersion(),
                    result.applicationVersion(), result.schemaIncluded());
        }
    }
    public record ValuesSuggestionRequest(@NotNull UUID tenantId, @NotNull UUID chartVersionId,
                                          @NotNull @Size(max = 1048576) String currentValuesYaml,
                                          @NotBlank @Size(max = 2000) String instruction) { }

    public record CatalogPackageResponse(String packageId, String repository, String repositoryDisplayName,
                                         String repositoryUrl, String name, String description, String version,
                                         String appVersion, String contentUrl, boolean official,
                                         boolean verifiedPublisher, List<String> availableVersions) {
        /** CatalogPackageResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static CatalogPackageResponse from(ChartCatalogPort.CatalogPackage item) {
            return new CatalogPackageResponse(item.packageId(), item.repository(), item.repositoryDisplayName(),
                    item.repositoryUrl(), item.name(), item.description(), item.version(), item.appVersion(),
                    item.contentUrl(), item.official(), item.verifiedPublisher(), item.availableVersions());
        }
    }

    public record ChartSourceResponse(String id, String tenantId, String sourceType, String name, String endpoint,
                                      boolean credentialConfigured, String tlsPolicy, boolean enabled,
                                      String updatedAt) {
        /** ChartSourceResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static ChartSourceResponse from(ChartSource item) {
            return new ChartSourceResponse(item.id().toString(), item.tenantId().toString(), item.sourceType().name(),
                    item.name(), item.endpoint(), item.credential() != null, item.tlsPolicy(), item.enabled(),
                    item.updatedAt().toString());
        }
    }

    public record ChartVersionResponse(String id, String chartVersion, String appVersion, String digestSha256,
                                       String provenanceStatus, String sourceReference, String importedAt) {
        /** ChartVersionResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static ChartVersionResponse from(ChartVersion item) {
            return new ChartVersionResponse(item.id().toString(), item.chartVersion(), item.appVersion(),
                    item.digestSha256(), item.provenanceStatus().name(), item.sourceReference(),
                    item.importedAt().toString());
        }
    }

    public record LibraryChartResponse(String id, String tenantId, String name, String packageName, String description,
                                       String sourceType, String sourceName, String providerName, String repositoryUrl,
                                       String trustStatus, List<ChartVersionResponse> versions) {
        /** LibraryChartResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static LibraryChartResponse from(ApplicationDeliveryCatalogService.LibraryChart item) {
            TenantChart chart = item.chart();
            return new LibraryChartResponse(chart.id().toString(), chart.tenantId().toString(), chart.name(),
                    chart.packageName(), chart.description(), chart.sourceType().name(), chart.sourceName(),
                    chart.providerName(), chart.repositoryUrl(),
                    chart.trustStatus().name(), item.versions().stream().map(ChartVersionResponse::from).toList());
        }
    }

    public record ImportedChartResponse(LibraryChartResponse chart, long compressedBytes, int fileCount,
                                        long expandedBytes) {
        /** ImportedChartResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static ImportedChartResponse from(ApplicationDeliveryCatalogService.ImportedChart item) {
            return new ImportedChartResponse(new LibraryChartResponse(item.chart().id().toString(),
                    item.chart().tenantId().toString(), item.chart().name(), item.chart().packageName(), item.chart().description(),
                    item.chart().sourceType().name(), item.chart().sourceName(), item.chart().providerName(),
                    item.chart().repositoryUrl(),
                    item.chart().trustStatus().name(), List.of(ChartVersionResponse.from(item.version()))),
                    item.compressedBytes(), item.fileCount(), item.expandedBytes());
        }
    }

    public record ValuesProfileResponse(String id, String tenantId, String chartVersionId, String name,
                                        String description, String updatedAt) {
        /** ValuesProfileResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static ValuesProfileResponse from(ValuesProfile item) {
            return new ValuesProfileResponse(item.id().toString(), item.tenantId().toString(),
                    item.chartVersionId().toString(), item.name(), item.description(), item.updatedAt().toString());
        }
    }

    public record ValuesRevisionResponse(String id, int revision, String valuesSha256, Integer parentRevision,
                                         String createdBy, String createdAt) {
        /** ValuesRevisionResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static ValuesRevisionResponse from(io.strato.aiops.domain.applicationdelivery.ValuesRevision item) {
            return new ValuesRevisionResponse(item.id().toString(), item.revision(), item.valuesSha256(),
                    item.parentRevision(), item.createdBy(), item.createdAt().toString());
        }
        /** ValuesRevisionResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static ValuesRevisionResponse from(ApplicationDeliveryCatalogService.ValuesRevisionSummary item) {
            return new ValuesRevisionResponse(item.id().toString(), item.revision(), item.valuesSha256(),
                    item.parentRevision(), item.createdBy(), item.createdAt().toString());
        }
    }
}
