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

    public ApplicationDeliveryCatalogController(ApplicationDeliveryCatalogService catalogService,
                                                CurrentAccessResolver currentAccessResolver,
                                                IdentityAccessService identityAccessService,
                                                TenantFeatureGuard featureGuard) {
        this.catalogService = catalogService;
        this.currentAccessResolver = currentAccessResolver;
        this.identityAccessService = identityAccessService;
        this.featureGuard = featureGuard;
    }

    @Operation(summary = "Search Helm charts in Artifact Hub")
    @GetMapping("/catalog/search")
    public List<CatalogPackageResponse> search(@RequestParam UUID tenantId, @RequestParam(defaultValue = "") String query,
                                               @RequestParam(defaultValue = "20") int limit,
                                               Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_READ);
        return catalogService.search(query, limit).stream().map(CatalogPackageResponse::from).toList();
    }

    @Operation(summary = "List tenant Helm and OCI sources")
    @GetMapping("/sources")
    public List<ChartSourceResponse> sources(@RequestParam UUID tenantId, Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_READ);
        return catalogService.sources(tenantId).stream().map(ChartSourceResponse::from).toList();
    }

    @Operation(summary = "Add a tenant Helm or OCI source")
    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public ChartSourceResponse createSource(@Valid @RequestBody CreateChartSourceRequest request,
                                            Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.CHART_MANAGE);
        return ChartSourceResponse.from(catalogService.createSource(request.tenantId(), request.sourceType(),
                request.name(), request.endpoint(), request.credential(), actor.user().id().toString()));
    }

    @PatchMapping("/sources/{sourceId}")
    public ChartSourceResponse updateSource(@PathVariable UUID sourceId,
                                            @Valid @RequestBody UpdateChartSourceRequest request,
                                            Authentication authentication) {
        require(authentication, request.tenantId(), Capability.CHART_MANAGE);
        return ChartSourceResponse.from(catalogService.updateSource(request.tenantId(), sourceId, request.name(),
                request.endpoint(), request.credential(), request.enabled()));
    }

    @DeleteMapping("/sources/{sourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSource(@PathVariable UUID sourceId, @RequestParam UUID tenantId,
                             Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_MANAGE);
        catalogService.deleteSource(tenantId, sourceId);
    }

    @Operation(summary = "Get an Artifact Hub Helm chart version")
    @GetMapping("/catalog/packages/{repository}/{name}")
    public CatalogPackageResponse details(@PathVariable String repository, @PathVariable String name,
                                          @RequestParam UUID tenantId,
                                          @RequestParam(required = false) String version,
                                          Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_READ);
        return CatalogPackageResponse.from(catalogService.details(repository, name, version));
    }

    @Operation(summary = "Import an Artifact Hub Helm chart into the tenant library")
    @PostMapping("/charts/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportedChartResponse importChart(@Valid @RequestBody ImportChartRequest request,
                                             Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.CHART_IMPORT);
        return ImportedChartResponse.from(catalogService.importFromCatalog(request.tenantId(), request.repository(),
                request.name(), request.version(), actor.user().id().toString()));
    }

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

    @Operation(summary = "List charts in the selected tenant library")
    @GetMapping("/charts")
    public List<LibraryChartResponse> charts(@RequestParam UUID tenantId,
                                             @RequestParam(defaultValue = "false") boolean includeArchived,
                                             Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_READ);
        return catalogService.library(tenantId, includeArchived).stream().map(LibraryChartResponse::from).toList();
    }

    @Operation(summary = "Create a reusable custom values profile")
    @PostMapping("/values-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ValuesProfileResponse createProfile(@Valid @RequestBody CreateValuesProfileRequest request,
                                               Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.VALUES_EDIT);
        return ValuesProfileResponse.from(catalogService.createProfile(request.tenantId(), request.chartVersionId(),
                request.name(), request.description(), actor.user().id().toString()));
    }

    @GetMapping("/values-profiles")
    public List<ValuesProfileResponse> profiles(@RequestParam UUID tenantId, @RequestParam UUID chartVersionId,
                                                Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_READ);
        return catalogService.profiles(tenantId, chartVersionId).stream().map(ValuesProfileResponse::from).toList();
    }

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

    @GetMapping("/values-profiles/{profileId}/revisions")
    public List<ValuesRevisionResponse> revisions(@PathVariable UUID profileId, @RequestParam UUID tenantId,
                                                  Authentication authentication) {
        require(authentication, tenantId, Capability.CHART_READ);
        return catalogService.revisions(tenantId, profileId).stream().map(ValuesRevisionResponse::from).toList();
    }

    @GetMapping("/values-revisions/{revisionId}/values")
    public ValuesPayloadResponse values(@PathVariable UUID revisionId, @RequestParam UUID tenantId,
                                        Authentication authentication) {
        require(authentication, tenantId, Capability.VALUES_EDIT);
        return new ValuesPayloadResponse(catalogService.values(tenantId, revisionId));
    }

    @PostMapping("/values-suggestions")
    @Operation(summary = "Suggest and Helm-validate custom Values for an exact chart version")
    public ValuesSuggestionResponse suggestValues(@Valid @RequestBody ValuesSuggestionRequest request,
                                                   Authentication authentication) {
        require(authentication, request.tenantId(), Capability.VALUES_EDIT);
        return ValuesSuggestionResponse.from(catalogService.suggestValues(request.tenantId(), request.chartVersionId(),
                request.currentValuesYaml(), request.instruction()));
    }

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
    public record CreateChartSourceRequest(@NotNull UUID tenantId, @NotNull ChartSourceType sourceType,
                                           @NotBlank String name, @NotBlank String endpoint, String credential) {
    }
    public record UpdateChartSourceRequest(@NotNull UUID tenantId, @NotBlank String name,
                                           @NotBlank String endpoint, String credential, boolean enabled) {
    }
    public record ValuesPayloadResponse(String valuesYaml) {
    }
    public record ValuesSuggestionResponse(String valuesYaml, String promptVersion, String validationStatus,
                                           int attempts, String chartName, String providerName, String chartVersion,
                                           String applicationVersion, boolean schemaIncluded) {
        static ValuesSuggestionResponse from(HelmValuesSuggestionService.SuggestionResult result) {
            return new ValuesSuggestionResponse(result.valuesYaml(), result.promptVersion(), result.validationStatus(),
                    result.attempts(), result.chartName(), result.providerName(), result.chartVersion(),
                    result.applicationVersion(), result.schemaIncluded());
        }
    }
    public record ValuesSuggestionRequest(@NotNull UUID tenantId, @NotNull UUID chartVersionId,
                                          @NotBlank String currentValuesYaml, @NotBlank String instruction) { }

    public record CatalogPackageResponse(String packageId, String repository, String repositoryDisplayName,
                                         String repositoryUrl, String name, String description, String version,
                                         String appVersion, String contentUrl, boolean official,
                                         boolean verifiedPublisher, List<String> availableVersions) {
        static CatalogPackageResponse from(ChartCatalogPort.CatalogPackage item) {
            return new CatalogPackageResponse(item.packageId(), item.repository(), item.repositoryDisplayName(),
                    item.repositoryUrl(), item.name(), item.description(), item.version(), item.appVersion(),
                    item.contentUrl(), item.official(), item.verifiedPublisher(), item.availableVersions());
        }
    }

    public record ChartSourceResponse(String id, String tenantId, String sourceType, String name, String endpoint,
                                      boolean credentialConfigured, String tlsPolicy, boolean enabled,
                                      String updatedAt) {
        static ChartSourceResponse from(ChartSource item) {
            return new ChartSourceResponse(item.id().toString(), item.tenantId().toString(), item.sourceType().name(),
                    item.name(), item.endpoint(), item.credential() != null, item.tlsPolicy(), item.enabled(),
                    item.updatedAt().toString());
        }
    }

    public record ChartVersionResponse(String id, String chartVersion, String appVersion, String digestSha256,
                                       String provenanceStatus, String sourceReference, String importedAt) {
        static ChartVersionResponse from(ChartVersion item) {
            return new ChartVersionResponse(item.id().toString(), item.chartVersion(), item.appVersion(),
                    item.digestSha256(), item.provenanceStatus().name(), item.sourceReference(),
                    item.importedAt().toString());
        }
    }

    public record LibraryChartResponse(String id, String tenantId, String name, String description,
                                       String sourceType, String sourceName, String repositoryUrl,
                                       String trustStatus, List<ChartVersionResponse> versions) {
        static LibraryChartResponse from(ApplicationDeliveryCatalogService.LibraryChart item) {
            TenantChart chart = item.chart();
            return new LibraryChartResponse(chart.id().toString(), chart.tenantId().toString(), chart.name(),
                    chart.description(), chart.sourceType().name(), chart.sourceName(), chart.repositoryUrl(),
                    chart.trustStatus().name(), item.versions().stream().map(ChartVersionResponse::from).toList());
        }
    }

    public record ImportedChartResponse(LibraryChartResponse chart, long compressedBytes, int fileCount,
                                        long expandedBytes) {
        static ImportedChartResponse from(ApplicationDeliveryCatalogService.ImportedChart item) {
            return new ImportedChartResponse(new LibraryChartResponse(item.chart().id().toString(),
                    item.chart().tenantId().toString(), item.chart().name(), item.chart().description(),
                    item.chart().sourceType().name(), item.chart().sourceName(), item.chart().repositoryUrl(),
                    item.chart().trustStatus().name(), List.of(ChartVersionResponse.from(item.version()))),
                    item.compressedBytes(), item.fileCount(), item.expandedBytes());
        }
    }

    public record ValuesProfileResponse(String id, String tenantId, String chartVersionId, String name,
                                        String description, String updatedAt) {
        static ValuesProfileResponse from(ValuesProfile item) {
            return new ValuesProfileResponse(item.id().toString(), item.tenantId().toString(),
                    item.chartVersionId().toString(), item.name(), item.description(), item.updatedAt().toString());
        }
    }

    public record ValuesRevisionResponse(String id, int revision, String valuesSha256, Integer parentRevision,
                                         String createdBy, String createdAt) {
        static ValuesRevisionResponse from(io.strato.aiops.domain.applicationdelivery.ValuesRevision item) {
            return new ValuesRevisionResponse(item.id().toString(), item.revision(), item.valuesSha256(),
                    item.parentRevision(), item.createdBy(), item.createdAt().toString());
        }
        static ValuesRevisionResponse from(ApplicationDeliveryCatalogService.ValuesRevisionSummary item) {
            return new ValuesRevisionResponse(item.id().toString(), item.revision(), item.valuesSha256(),
                    item.parentRevision(), item.createdBy(), item.createdAt().toString());
        }
    }
}
