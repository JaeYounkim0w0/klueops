package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.strato.aiops.adapter.out.helm.HelmChartArchiveInspector;
import io.strato.aiops.adapter.out.helm.RemoteChartSourceValidator;
import io.strato.aiops.application.port.out.ApplicationDeliveryRepositoryPort;
import io.strato.aiops.application.port.out.ChartAcquisitionPort;
import io.strato.aiops.application.port.out.ChartCatalogPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
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
    private final HelmChartArchiveInspector archiveInspector;
    private final ApplicationDeliveryRepositoryPort repository;
    private final SecretCryptoPort secretCrypto;
    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Clock clock;
    private final RemoteChartSourceValidator sourceValidator;

    public ApplicationDeliveryCatalogService(ChartCatalogPort catalog, ChartAcquisitionPort acquisition,
                                             HelmChartArchiveInspector archiveInspector,
                                             ApplicationDeliveryRepositoryPort repository,
                                             SecretCryptoPort secretCrypto, ObjectMapper objectMapper, Clock clock,
                                             RemoteChartSourceValidator sourceValidator) {
        this.catalog = catalog;
        this.acquisition = acquisition;
        this.archiveInspector = archiveInspector;
        this.repository = repository;
        this.secretCrypto = secretCrypto;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.sourceValidator = sourceValidator;
    }

    @Transactional
    public ChartSource createSource(UUID tenantId, ChartSourceType sourceType, String name, String endpoint,
                                    String credential, String actor) {
        validateSource(sourceType, endpoint);
        return repository.saveSource(ChartSource.create(tenantId, sourceType, name.trim(), endpoint.trim(),
                blank(credential) ? null : secretCrypto.encrypt(credential), "STRICT", actor, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<ChartSource> sources(UUID tenantId) {
        return repository.findSources(tenantId, 200);
    }

    @Transactional
    public ChartSource updateSource(UUID tenantId, UUID sourceId, String name, String endpoint,
                                    String credential, boolean enabled) {
        ChartSource current = repository.findSource(tenantId, sourceId).orElseThrow();
        validateSource(current.sourceType(), endpoint);
        return repository.saveSource(current.updated(name.trim(), endpoint.trim(),
                blank(credential) ? null : secretCrypto.encrypt(credential), "STRICT", enabled, clock.instant()));
    }

    @Transactional
    public void deleteSource(UUID tenantId, UUID sourceId) {
        repository.deleteSource(tenantId, sourceId);
    }

    public List<ChartCatalogPort.CatalogPackage> search(String query, int limit) {
        return catalog.search(query, Math.max(1, Math.min(limit, 50)));
    }

    public ChartCatalogPort.CatalogPackage details(String repositoryName, String packageName, String version) {
        return catalog.details(repositoryName, packageName, version);
    }

    public ImportedChart importFromCatalog(UUID tenantId, String repositoryName, String packageName,
                                           String version, String actor) {
        ChartCatalogPort.CatalogPackage details = catalog.details(repositoryName, packageName, version);
        byte[] payload = acquisition.fetch(new ChartAcquisitionPort.FetchRequest(details.repositoryUrl(),
                packageName, details.version(), details.contentUrl(), Duration.ofSeconds(90)));
        return importPayload(tenantId, ChartSourceType.ARTIFACT_HUB, repositoryName, details.repositoryUrl(),
                packageName, details.description(), payload, actor);
    }

    public ImportedChart upload(UUID tenantId, String sourceName, byte[] payload, String actor) {
        return importPayload(tenantId, ChartSourceType.UPLOAD, sourceName, null, "uploaded", null, payload, actor);
    }

    @Transactional
    public ImportedChart importPayload(UUID tenantId, ChartSourceType sourceType, String sourceName,
                                       String repositoryUrl, String requestedPackageName, String description,
                                       byte[] payload, String actor) {
        var inspected = archiveInspector.inspect(payload);
        String digest = sha256(payload);
        var artifact = repository.saveArtifact(digest, payload, clock.instant());
        String packageName = sourceType == ChartSourceType.UPLOAD ? inspected.name() : requestedPackageName;
        TenantChart chart = repository.findChartByCoordinate(tenantId, sourceType, sourceName, packageName)
                .orElseGet(() -> repository.saveChart(TenantChart.create(tenantId, inspected.name(),
                        description == null ? inspected.description() : description, sourceType, sourceName,
                        repositoryUrl, packageName, ChartTrustStatus.CHECKSUMMED, actor, clock.instant())));
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
                digest, ChartTrustStatus.CHECKSUMMED, artifact.id(), metadata(inspected), actor, clock.instant()));
        return new ImportedChart(chart, imported, artifact.sizeBytes(), inspected.fileCount(), inspected.expandedBytes());
    }

    @Transactional(readOnly = true)
    public List<LibraryChart> library(UUID tenantId, boolean includeArchived) {
        List<TenantChart> charts = repository.findCharts(tenantId, includeArchived, MAXIMUM_LIBRARY_RESULTS);
        Map<UUID, List<ChartVersion>> versions = repository.findVersions(tenantId,
                        charts.stream().map(TenantChart::id).toList(), 20).stream()
                .collect(Collectors.groupingBy(ChartVersion::tenantChartId));
        return charts.stream().map(chart -> new LibraryChart(chart,
                versions.getOrDefault(chart.id(), List.of()))).toList();
    }

    @Transactional
    public ValuesProfile createProfile(UUID tenantId, UUID chartVersionId, String name, String description,
                                       String actor) {
        repository.findVersion(tenantId, chartVersionId).orElseThrow();
        return repository.saveProfile(ValuesProfile.create(tenantId, chartVersionId, name, description,
                actor, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<ValuesProfile> profiles(UUID tenantId, UUID chartVersionId) {
        repository.findVersion(tenantId, chartVersionId).orElseThrow();
        return repository.findProfiles(tenantId, chartVersionId, 100);
    }

    @Transactional
    public ValuesRevision createRevision(UUID tenantId, UUID profileId, String valuesYaml, String actor) {
        repository.findProfile(tenantId, profileId).orElseThrow();
        validateValues(valuesYaml);
        int revision = repository.nextRevision(profileId);
        ValuesRevision saved = new ValuesRevision(UUID.randomUUID(), profileId, revision,
                secretCrypto.encrypt(valuesYaml), sha256(valuesYaml.getBytes(StandardCharsets.UTF_8)),
                revision == 1 ? null : revision - 1, actor, clock.instant());
        return repository.saveRevision(saved);
    }

    @Transactional(readOnly = true)
    public List<ValuesRevisionSummary> revisions(UUID tenantId, UUID profileId) {
        repository.findProfile(tenantId, profileId).orElseThrow();
        return repository.findRevisions(tenantId, profileId, 100).stream().map(ValuesRevisionSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public String values(UUID tenantId, UUID revisionId) {
        ValuesRevision revision = repository.findRevision(tenantId, revisionId).orElseThrow();
        return secretCrypto.decrypt(revision.encryptedValues());
    }

    private void validateValues(String valuesYaml) {
        if (valuesYaml == null || valuesYaml.isBlank()) throw new IllegalArgumentException("Values YAML is required");
        if (valuesYaml.length() > 1024 * 1024) throw new IllegalArgumentException("Values YAML exceeds 1 MiB");
        try {
            Object parsed = yamlMapper.readValue(valuesYaml, Object.class);
            if (!(parsed instanceof Map<?, ?>)) throw new IllegalArgumentException("Values YAML root must be an object");
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Values YAML is invalid", exception);
        }
    }

    private String metadata(HelmChartArchiveInspector.InspectedArchive inspected) {
        try {
            return objectMapper.writeValueAsString(Map.of("name", inspected.name(), "version", inspected.version(),
                    "fileCount", inspected.fileCount(), "expandedBytes", inspected.expandedBytes()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Chart metadata serialization failed", exception);
        }
    }

    private String sourceReference(String repositoryUrl, String packageName, String version) {
        return repositoryUrl == null ? "upload://" + packageName + ":" + version
                : repositoryUrl + "/" + packageName + ":" + version;
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateSource(ChartSourceType sourceType, String endpoint) {
        if (sourceType != ChartSourceType.HELM_REPOSITORY && sourceType != ChartSourceType.OCI_REGISTRY) {
            throw new IllegalArgumentException("Only Helm repository and OCI registry sources can be configured");
        }
        sourceValidator.requirePublicHttps(endpoint);
    }

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
        static ValuesRevisionSummary from(ValuesRevision value) {
            return new ValuesRevisionSummary(value.id(), value.revision(), value.valuesSha256(), value.parentRevision(),
                    value.createdBy(), value.createdAt());
        }
    }
}
