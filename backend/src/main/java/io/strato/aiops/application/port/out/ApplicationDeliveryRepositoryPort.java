package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.applicationdelivery.ChartSourceType;
import io.strato.aiops.domain.applicationdelivery.ChartSource;
import io.strato.aiops.domain.applicationdelivery.ChartVersion;
import io.strato.aiops.domain.applicationdelivery.TenantChart;
import io.strato.aiops.domain.applicationdelivery.ValuesProfile;
import io.strato.aiops.domain.applicationdelivery.ValuesRevision;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationDeliveryRepositoryPort {
    ChartSource saveSource(ChartSource source);
    List<ChartSource> findSources(UUID tenantId, int limit);
    Optional<ChartSource> findSource(UUID tenantId, UUID sourceId);
    void deleteSource(UUID tenantId, UUID sourceId);
    Artifact saveArtifact(String digestSha256, byte[] payload, Instant now);
    Optional<TenantChart> findChartByCoordinate(UUID tenantId, ChartSourceType sourceType, String sourceName,
                                                String packageName);
    TenantChart saveChart(TenantChart chart);
    Optional<ChartVersion> findVersionByChartAndVersion(UUID tenantId, UUID chartId, String version);
    ChartVersion saveVersion(ChartVersion version);
    List<TenantChart> findCharts(UUID tenantId, boolean includeArchived, int limit);
    List<ChartVersion> findVersions(UUID tenantId, UUID chartId, int limit);
    List<ChartVersion> findVersions(UUID tenantId, Collection<UUID> chartIds, int perChartLimit);
    Optional<ChartVersion> findVersion(UUID tenantId, UUID versionId);
    byte[] loadArtifact(UUID tenantId, UUID versionId);
    ValuesProfile saveProfile(ValuesProfile profile);
    List<ValuesProfile> findProfiles(UUID tenantId, UUID chartVersionId, int limit);
    Optional<ValuesProfile> findProfile(UUID tenantId, UUID profileId);
    int nextRevision(UUID profileId);
    ValuesRevision saveRevision(ValuesRevision revision);
    List<ValuesRevision> findRevisions(UUID tenantId, UUID profileId, int limit);
    Optional<ValuesRevision> findRevision(UUID tenantId, UUID revisionId);

    record Artifact(UUID id, String digestSha256, long sizeBytes, Instant createdAt) {
    }
}
