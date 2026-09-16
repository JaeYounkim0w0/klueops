package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.ApplicationDeliveryRepositoryPort;
import io.strato.aiops.application.port.out.ChartArchiveInspectionPort;
import io.strato.aiops.application.port.out.ChartAcquisitionPort;
import io.strato.aiops.application.port.out.ChartCatalogPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.application.port.out.RemoteSourceValidationPort;
import io.strato.aiops.domain.applicationdelivery.ChartSourceType;
import io.strato.aiops.domain.applicationdelivery.ChartSource;
import io.strato.aiops.domain.applicationdelivery.ChartTrustStatus;
import io.strato.aiops.domain.applicationdelivery.ChartVersion;
import io.strato.aiops.domain.applicationdelivery.TenantChart;
import io.strato.aiops.domain.applicationdelivery.ValuesProfile;
import io.strato.aiops.domain.applicationdelivery.ValuesRevision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class ApplicationDeliveryCatalogService {
    private static final int MAXIMUM_LIBRARY_RESULTS = 200;
    private final ChartCatalogPort catalog;
    private final ChartAcquisitionPort acquisition;
    private final ChartArchiveInspectionPort archiveInspector;
    private final ApplicationDeliveryRepositoryPort repository;
    private final SecretCryptoPort secretCrypto;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RemoteSourceValidationPort sourceValidator;
    private final HelmValuesSuggestionService valuesSuggestion;
    private final ValuesRevisionWriter revisionWriter;

    /** ApplicationDeliveryCatalogService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ApplicationDeliveryCatalogService(ChartCatalogPort catalog, ChartAcquisitionPort acquisition,
                                             ChartArchiveInspectionPort archiveInspector,
                                             ApplicationDeliveryRepositoryPort repository,
                                             SecretCryptoPort secretCrypto, ObjectMapper objectMapper, Clock clock,
                                             RemoteSourceValidationPort sourceValidator,
                                             HelmValuesSuggestionService valuesSuggestion,
                                             ValuesRevisionWriter revisionWriter) {
        this.catalog = catalog;
        this.acquisition = acquisition;
        this.archiveInspector = archiveInspector;
        this.repository = repository;
        this.secretCrypto = secretCrypto;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.sourceValidator = sourceValidator;
        this.valuesSuggestion = valuesSuggestion;
        this.revisionWriter = revisionWriter;
    }

    /** ApplicationDeliveryCatalogService의 createSource 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Transactional
    public ChartSource createSource(UUID tenantId, ChartSourceType sourceType, String name, String endpoint,
                                    String credential, String actor) {
        validateSource(sourceType, endpoint);
        return repository.saveSource(ChartSource.create(tenantId, sourceType, name.trim(), endpoint.trim(),
                blank(credential) ? null : secretCrypto.encrypt(credential), "STRICT", actor, clock.instant()));
    }

    /** ApplicationDeliveryCatalogService의 sources 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public List<ChartSource> sources(UUID tenantId) {
        return repository.findSources(tenantId, 200);
    }

    /** ApplicationDeliveryCatalogService의 updateSource 처리 대상의 상태를 갱신한다. */
    @Transactional
    public ChartSource updateSource(UUID tenantId, UUID sourceId, String name, String endpoint,
                                    String credential, boolean enabled) {
        ChartSource current = repository.findSource(tenantId, sourceId).orElseThrow();
        validateSource(current.sourceType(), endpoint);
        return repository.saveSource(current.updated(name.trim(), endpoint.trim(),
                blank(credential) ? null : secretCrypto.encrypt(credential), "STRICT", enabled, clock.instant()));
    }

    /** ApplicationDeliveryCatalogService의 deleteSource 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Transactional
    public void deleteSource(UUID tenantId, UUID sourceId) {
        repository.deleteSource(tenantId, sourceId);
    }

    /** ApplicationDeliveryCatalogService의 search 처리에 필요한 업무 로직을 수행한다. */
    public List<ChartCatalogPort.CatalogPackage> search(String query, int limit) {
        return catalog.search(query, Math.max(1, Math.min(limit, 50)));
    }

    /** ApplicationDeliveryCatalogService의 details 처리에 필요한 업무 로직을 수행한다. */
    public ChartCatalogPort.CatalogPackage details(String repositoryName, String packageName, String version) {
        return catalog.details(repositoryName, packageName, version);
    }

    /** ApplicationDeliveryCatalogService의 importFromCatalog 처리에 필요한 업무 로직을 수행한다. */
    public ImportedChart importFromCatalog(UUID tenantId, String repositoryName, String packageName,
                                           String version, String actor) {
        ChartCatalogPort.CatalogPackage details = catalog.details(repositoryName, packageName, version);
        var verified = acquisition.fetchVerified(new ChartAcquisitionPort.FetchRequest(details.repositoryUrl(),
                packageName, details.version(), details.contentUrl(), Duration.ofSeconds(90)));
        // 저장소 식별자와 사용자에게 보여 줄 제공사 이름을 분리해 Chart 출처를 명확히 보존한다.
        return importPayload(tenantId, ChartSourceType.ARTIFACT_HUB, repositoryName,
                details.repositoryDisplayName(), details.repositoryUrl(),
                packageName, details.description(), verified.payload(), actor, verified.trustStatus());
    }

    /** ApplicationDeliveryCatalogService의 upload 처리에 필요한 업무 로직을 수행한다. */
    public ImportedChart upload(UUID tenantId, String sourceName, byte[] payload, String actor) {
        return importPayload(tenantId, ChartSourceType.UPLOAD, sourceName, null,
                null, "uploaded", null, payload, actor);
    }

    /** ApplicationDeliveryCatalogService의 importPayload 처리에 필요한 업무 로직을 수행한다. */
    @Transactional
    public ImportedChart importPayload(UUID tenantId, ChartSourceType sourceType, String sourceName, String providerName,
                                       String repositoryUrl, String requestedPackageName, String description,
                                       byte[] payload, String actor) {
        return importPayload(tenantId, sourceType, sourceName, providerName, repositoryUrl, requestedPackageName,
                description, payload, actor, ChartTrustStatus.CHECKSUMMED);
    }

    /** 검증 결과를 Chart와 immutable version 양쪽에 동일하게 보존한다. */
    private ImportedChart importPayload(UUID tenantId, ChartSourceType sourceType, String sourceName, String providerName,
                                       String repositoryUrl, String requestedPackageName, String description,
                                       byte[] payload, String actor, ChartTrustStatus trustStatus) {
        var inspected = archiveInspector.inspect(payload);
        String digest = sha256(payload);
        var artifact = repository.saveArtifact(digest, payload, clock.instant());
        String packageName = sourceType == ChartSourceType.UPLOAD ? inspected.name() : requestedPackageName;
        TenantChart chart = repository.findChartByCoordinate(tenantId, sourceType, sourceName, packageName)
                .orElseGet(() -> repository.saveChart(TenantChart.create(tenantId, inspected.name(),
                        description == null ? inspected.description() : description, sourceType, sourceName, providerName,
                        repositoryUrl, packageName, trustStatus, actor, clock.instant())));
        if (chart.archivedAt() != null) {
            // 제거했던 동일 Chart를 다시 가져오면 기존 immutable version/history를 보존한 채 Library에 복원한다.
            chart = repository.updateChartArchiveState(chart.restored(clock.instant()));
        }
        ChartVersion existing = repository.findVersionByChartAndVersion(tenantId, chart.id(), inspected.version())
                .orElse(null);
        if (existing != null) {
            if (!existing.digestSha256().equals(digest)) {
                throw new IllegalStateException("The same chart version already exists with a different digest");
            }
            return new ImportedChart(chart, existing, artifact.sizeBytes(), inspected.fileCount(), inspected.expandedBytes());
        }
        ChartVersion imported = repository.saveVersion(new ChartVersion(UUID.randomUUID(), chart.id(),
                inspected.version(), inspected.appVersion(), sourceReference(repositoryUrl, packageName, inspected.version()),
                digest, trustStatus, artifact.id(), metadata(inspected), actor, clock.instant()));
        return new ImportedChart(chart, imported, artifact.sizeBytes(), inspected.fileCount(), inspected.expandedBytes());
    }

    /** ApplicationDeliveryCatalogService의 library 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public List<LibraryChart> library(UUID tenantId, boolean includeArchived) {
        List<TenantChart> charts = repository.findCharts(tenantId, includeArchived, MAXIMUM_LIBRARY_RESULTS);
        Map<UUID, List<ChartVersion>> versions = repository.findVersions(tenantId,
                        charts.stream().map(TenantChart::id).toList(), 20).stream()
                .collect(Collectors.groupingBy(ChartVersion::tenantChartId));
        return charts.stream().map(chart -> new LibraryChart(chart,
                versions.getOrDefault(chart.id(), List.of()))).toList();
    }

    /** ApplicationDeliveryCatalogService의 removeChart 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Transactional
    public void removeChart(UUID tenantId, UUID chartId, String confirmationText) {
        TenantChart chart = repository.findChart(tenantId, chartId).orElseThrow();
        if (chart.archivedAt() != null) return;
        String expected = removalConfirmation(chart);
        if (!expected.equals(confirmationText == null ? "" : confirmationText.trim())) {
            throw new IllegalArgumentException("Chart removal confirmation must exactly match: " + expected);
        }
        // 배포 이력과 Values revision의 참조 무결성을 유지하기 위해 물리 삭제 대신 Tenant Library에서 숨긴다.
        repository.updateChartArchiveState(chart.archived(clock.instant()));
    }

    /** ApplicationDeliveryCatalogService의 removalConfirmation 처리에 필요한 업무 로직을 수행한다. */
    public String removalConfirmation(TenantChart chart) {
        return (blank(chart.sourceName()) ? "local" : chart.sourceName()) + "/" + chart.packageName();
    }

    /** ApplicationDeliveryCatalogService의 createProfile 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Transactional
    public ValuesProfile createProfile(UUID tenantId, UUID chartVersionId, String name, String description,
                                       String actor) {
        repository.findVersion(tenantId, chartVersionId).orElseThrow();
        return repository.saveProfile(ValuesProfile.create(tenantId, chartVersionId, name, description,
                actor, clock.instant()));
    }

    /** ApplicationDeliveryCatalogService의 profiles 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public List<ValuesProfile> profiles(UUID tenantId, UUID chartVersionId) {
        repository.findVersion(tenantId, chartVersionId).orElseThrow();
        return repository.findProfiles(tenantId, chartVersionId, 100);
    }

    /** ApplicationDeliveryCatalogService의 createRevision 처리에 필요한 데이터를 생성하거나 저장한다. */
    public ValuesRevision createRevision(UUID tenantId, UUID profileId, String valuesYaml, String actor) {
        ValuesProfile profile = repository.findProfile(tenantId, profileId).orElseThrow();
        // Helm CLI 검증은 DB transaction 밖에서 수행해 느린 외부 프로세스가 connection을 점유하지 않게 한다.
        valuesSuggestion.requireValid(tenantId, profile.chartVersionId(), valuesYaml);
        return revisionWriter.create(tenantId, profileId, valuesYaml, actor);
    }

    /** ApplicationDeliveryCatalogService의 revisions 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public List<ValuesRevisionSummary> revisions(UUID tenantId, UUID profileId) {
        repository.findProfile(tenantId, profileId).orElseThrow();
        return repository.findRevisions(tenantId, profileId, 100).stream().map(ValuesRevisionSummary::from).toList();
    }

    /** ApplicationDeliveryCatalogService의 values 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public String values(UUID tenantId, UUID revisionId) {
        ValuesRevision revision = repository.findRevision(tenantId, revisionId).orElseThrow();
        return secretCrypto.decrypt(revision.encryptedValues());
    }

    /** ApplicationDeliveryCatalogService의 suggestValues 처리에 필요한 업무 로직을 수행한다. */
    public HelmValuesSuggestionService.SuggestionResult suggestValues(UUID tenantId, UUID chartVersionId,
                                                                       String currentValues, String instruction) {
        return valuesSuggestion.suggest(tenantId, chartVersionId, currentValues, instruction);
    }

    /** 정확한 Chart version의 기본 Values와 JSON Schema를 Form/YAML 편집기에 제공한다. */
    @Transactional(readOnly = true)
    public ValuesContract valuesContract(UUID tenantId, UUID chartVersionId) {
        repository.findVersion(tenantId, chartVersionId).orElseThrow();
        var inspected = archiveInspector.inspect(repository.loadArtifact(tenantId, chartVersionId));
        return new ValuesContract(inspected.defaultValuesYaml(), inspected.valuesSchemaJson(),
                inspected.valuesSchemaJson() != null && !inspected.valuesSchemaJson().isBlank());
    }

    /** ApplicationDeliveryCatalogService의 metadata 처리에 필요한 업무 로직을 수행한다. */
    private String metadata(ChartArchiveInspectionPort.InspectedArchive inspected) {
        try {
            return objectMapper.writeValueAsString(Map.of("name", inspected.name(), "version", inspected.version(),
                    "fileCount", inspected.fileCount(), "expandedBytes", inspected.expandedBytes()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Chart metadata serialization failed", exception);
        }
    }

    public record ValuesContract(String defaultValuesYaml, String valuesSchemaJson, boolean schemaIncluded) { }

    /** ApplicationDeliveryCatalogService의 sourceReference 처리에 필요한 업무 로직을 수행한다. */
    private String sourceReference(String repositoryUrl, String packageName, String version) {
        return repositoryUrl == null ? "upload://" + packageName + ":" + version
                : repositoryUrl + "/" + packageName + ":" + version;
    }

    /** ApplicationDeliveryCatalogService의 sha256 처리에 필요한 업무 로직을 수행한다. */
    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** ApplicationDeliveryCatalogService의 validateSource 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void validateSource(ChartSourceType sourceType, String endpoint) {
        if (sourceType != ChartSourceType.HELM_REPOSITORY && sourceType != ChartSourceType.OCI_REGISTRY) {
            throw new IllegalArgumentException("Only Helm repository and OCI registry sources can be configured");
        }
        sourceValidator.requirePublicHttps(endpoint);
    }

    /** ApplicationDeliveryCatalogService의 blank 처리에 필요한 업무 로직을 수행한다. */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ImportedChart(TenantChart chart, ChartVersion version, long compressedBytes, int fileCount,
                                long expandedBytes) {
    }
    public record LibraryChart(TenantChart chart, List<ChartVersion> versions) {
    }
    public record ValuesRevisionSummary(UUID id, int revision, String valuesSha256, Integer parentRevision,
                                        String createdBy, java.time.Instant createdAt) {
        /** ValuesRevisionSummary의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static ValuesRevisionSummary from(ValuesRevision value) {
            return new ValuesRevisionSummary(value.id(), value.revision(), value.valuesSha256(), value.parentRevision(),
                    value.createdBy(), value.createdAt());
        }
    }
}
