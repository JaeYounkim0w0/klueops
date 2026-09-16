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
    /** ApplicationDeliveryRepositoryPort의 saveSource 처리에 필요한 데이터를 생성하거나 저장한다. */
    ChartSource saveSource(ChartSource source);
    /** ApplicationDeliveryRepositoryPort의 findSources 처리 결과를 조회해 반환한다. */
    List<ChartSource> findSources(UUID tenantId, int limit);
    /** ApplicationDeliveryRepositoryPort의 findSource 처리 결과를 조회해 반환한다. */
    Optional<ChartSource> findSource(UUID tenantId, UUID sourceId);
    /** ApplicationDeliveryRepositoryPort의 deleteSource 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteSource(UUID tenantId, UUID sourceId);
    /** ApplicationDeliveryRepositoryPort의 saveArtifact 처리에 필요한 데이터를 생성하거나 저장한다. */
    Artifact saveArtifact(String digestSha256, byte[] payload, Instant now);
    /** ApplicationDeliveryRepositoryPort의 findChartByCoordinate 처리 결과를 조회해 반환한다. */
    Optional<TenantChart> findChartByCoordinate(UUID tenantId, ChartSourceType sourceType, String sourceName,
                                                String packageName);
    /** ApplicationDeliveryRepositoryPort의 findChart 처리 결과를 조회해 반환한다. */
    Optional<TenantChart> findChart(UUID tenantId, UUID chartId);
    /** ApplicationDeliveryRepositoryPort의 saveChart 처리에 필요한 데이터를 생성하거나 저장한다. */
    TenantChart saveChart(TenantChart chart);
    /** ApplicationDeliveryRepositoryPort의 updateChartArchiveState 처리 대상의 상태를 갱신한다. */
    TenantChart updateChartArchiveState(TenantChart chart);
    /** ApplicationDeliveryRepositoryPort의 findVersionByChartAndVersion 처리 결과를 조회해 반환한다. */
    Optional<ChartVersion> findVersionByChartAndVersion(UUID tenantId, UUID chartId, String version);
    /** ApplicationDeliveryRepositoryPort의 saveVersion 처리에 필요한 데이터를 생성하거나 저장한다. */
    ChartVersion saveVersion(ChartVersion version);
    /** ApplicationDeliveryRepositoryPort의 findCharts 처리 결과를 조회해 반환한다. */
    List<TenantChart> findCharts(UUID tenantId, boolean includeArchived, int limit);
    /** ApplicationDeliveryRepositoryPort의 findVersions 처리 결과를 조회해 반환한다. */
    List<ChartVersion> findVersions(UUID tenantId, UUID chartId, int limit);
    /** ApplicationDeliveryRepositoryPort의 findVersions 처리 결과를 조회해 반환한다. */
    List<ChartVersion> findVersions(UUID tenantId, Collection<UUID> chartIds, int perChartLimit);
    /** ApplicationDeliveryRepositoryPort의 findVersion 처리 결과를 조회해 반환한다. */
    Optional<ChartVersion> findVersion(UUID tenantId, UUID versionId);
    /** ApplicationDeliveryRepositoryPort의 loadArtifact 처리 결과를 조회해 반환한다. */
    byte[] loadArtifact(UUID tenantId, UUID versionId);
    /** ApplicationDeliveryRepositoryPort의 saveProfile 처리에 필요한 데이터를 생성하거나 저장한다. */
    ValuesProfile saveProfile(ValuesProfile profile);
    /** ApplicationDeliveryRepositoryPort의 findProfiles 처리 결과를 조회해 반환한다. */
    List<ValuesProfile> findProfiles(UUID tenantId, UUID chartVersionId, int limit);
    /** ApplicationDeliveryRepositoryPort의 findProfile 처리 결과를 조회해 반환한다. */
    Optional<ValuesProfile> findProfile(UUID tenantId, UUID profileId);
    /** ApplicationDeliveryRepositoryPort의 nextRevision 처리 계약을 정의한다. */
    int nextRevision(UUID profileId);
    /** ApplicationDeliveryRepositoryPort의 saveRevision 처리에 필요한 데이터를 생성하거나 저장한다. */
    ValuesRevision saveRevision(ValuesRevision revision);
    /** ApplicationDeliveryRepositoryPort의 findRevisions 처리 결과를 조회해 반환한다. */
    List<ValuesRevision> findRevisions(UUID tenantId, UUID profileId, int limit);
    /** ApplicationDeliveryRepositoryPort의 findRevision 처리 결과를 조회해 반환한다. */
    Optional<ValuesRevision> findRevision(UUID tenantId, UUID revisionId);

    record Artifact(UUID id, String digestSha256, long sizeBytes, Instant createdAt) {
    }
}
