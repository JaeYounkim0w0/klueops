package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.ApplicationDeliveryRepositoryPort;
import io.strato.aiops.application.port.out.ChartCatalogPort;
import io.strato.aiops.domain.applicationdelivery.ChartSource;
import io.strato.aiops.domain.applicationdelivery.ChartSourceType;
import io.strato.aiops.domain.applicationdelivery.ChartTrustStatus;
import io.strato.aiops.domain.applicationdelivery.ChartVersion;
import io.strato.aiops.domain.applicationdelivery.TenantChart;
import io.strato.aiops.domain.applicationdelivery.ValuesProfile;
import io.strato.aiops.domain.applicationdelivery.ValuesRevision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationDeliveryCatalogServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");
    private final UUID tenantId = UUID.randomUUID();
    private final UUID chartId = UUID.randomUUID();
    private FakeRepository repository;
    private ApplicationDeliveryCatalogService service;

    /** ApplicationDeliveryCatalogServiceTest의 setUp 처리 대상의 상태를 갱신한다. */
    @BeforeEach
    void setUp() {
        TenantChart chart = new TenantChart(chartId, tenantId, "postgres", "PostgreSQL",
                ChartSourceType.ARTIFACT_HUB, "cloudpirates-postgres", "CloudPirates", "https://example.test/postgres", "postgres",
                ChartTrustStatus.CHECKSUMMED, null, "tester", NOW.minusSeconds(60), NOW.minusSeconds(60));
        repository = new FakeRepository(chart);
        service = new ApplicationDeliveryCatalogService(null, null, null, repository, null, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), null, null, null);
    }

    /** ApplicationDeliveryCatalogServiceTest의 archivesChartAfterExactConfirmationWithoutDeletingReferencedHistory 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void archivesChartAfterExactConfirmationWithoutDeletingReferencedHistory() {
        service.removeChart(tenantId, chartId, "cloudpirates-postgres/postgres");

        assertThat(repository.updatedChart).isNotNull();
        assertThat(repository.updatedChart.archivedAt()).isEqualTo(NOW);
    }

    /** ApplicationDeliveryCatalogServiceTest의 rejectsRemovalWhenConfirmationDoesNotMatchTheStoredChart 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsRemovalWhenConfirmationDoesNotMatchTheStoredChart() {
        assertThatThrownBy(() -> service.removeChart(tenantId, chartId, "postgres"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cloudpirates-postgres/postgres");
        assertThat(repository.updatedChart).isNull();
    }

    /** ApplicationDeliveryCatalogServiceTest의 storesArtifactHubDisplayNameAsChartProvider 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Test
    void storesArtifactHubDisplayNameAsChartProvider() {
        ChartCatalogPort catalog = new ChartCatalogPort() {
            /** 익명 구현체의 search 처리에 필요한 업무 로직을 수행한다. */
            @Override public List<CatalogPackage> search(String query, int limit) { return List.of(); }
            /** 익명 구현체의 details 처리에 필요한 업무 로직을 수행한다. */
            @Override public CatalogPackage details(String repositoryName, String packageName, String version) {
                return new CatalogPackage("package-id", repositoryName, "CloudPirates",
                        "https://example.test/charts", packageName, "NGINX", version, "1.31.5",
                        "https://example.test/nginx.tgz", false, true, List.of(version));
            }
        };
        ApplicationDeliveryCatalogService importingService = new ApplicationDeliveryCatalogService(
                catalog, request -> "chart-payload".getBytes(), payload ->
                new io.strato.aiops.application.port.out.ChartArchiveInspectionPort.InspectedArchive(
                        "nginx", "0.16.8", "1.31.5", "NGINX", "name: nginx", "{}", null, 3, 1024),
                repository, null, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), null, null, null);

        var imported = importingService.importFromCatalog(
                tenantId, "cloudpirates-nginx", "nginx", "0.16.8", "tester");

        assertThat(imported.chart().providerName()).isEqualTo("CloudPirates");
        assertThat(imported.chart().sourceName()).isEqualTo("cloudpirates-nginx");
    }

    private static final class FakeRepository implements ApplicationDeliveryRepositoryPort {
        private TenantChart chart;
        private TenantChart updatedChart;

        /** FakeRepository 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
        private FakeRepository(TenantChart chart) { this.chart = chart; }

        /** FakeRepository의 findChart 처리 결과를 조회해 반환한다. */
        @Override public Optional<TenantChart> findChart(UUID tenantId, UUID chartId) {
            return chart.tenantId().equals(tenantId) && chart.id().equals(chartId) ? Optional.of(chart) : Optional.empty();
        }
        /** FakeRepository의 updateChartArchiveState 처리 대상의 상태를 갱신한다. */
        @Override public TenantChart updateChartArchiveState(TenantChart value) { updatedChart = value; return value; }
        /** FakeRepository의 saveSource 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public ChartSource saveSource(ChartSource value) { throw unsupported(); }
        /** FakeRepository의 findSources 처리 결과를 조회해 반환한다. */
        @Override public List<ChartSource> findSources(UUID tenantId, int limit) { return List.of(); }
        /** FakeRepository의 findSource 처리 결과를 조회해 반환한다. */
        @Override public Optional<ChartSource> findSource(UUID tenantId, UUID sourceId) { return Optional.empty(); }
        /** FakeRepository의 deleteSource 처리 대상과 관련 상태를 안전하게 정리한다. */
        @Override public void deleteSource(UUID tenantId, UUID sourceId) { throw unsupported(); }
        /** FakeRepository의 saveArtifact 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public Artifact saveArtifact(String digestSha256, byte[] payload, Instant now) {
            return new Artifact(UUID.randomUUID(), digestSha256, payload.length, now);
        }
        /** FakeRepository의 findChartByCoordinate 처리 결과를 조회해 반환한다. */
        @Override public Optional<TenantChart> findChartByCoordinate(UUID tenantId, ChartSourceType sourceType,
                                                                     String sourceName, String packageName) { return Optional.empty(); }
        /** FakeRepository의 saveChart 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public TenantChart saveChart(TenantChart value) { chart = value; return value; }
        /** FakeRepository의 findVersionByChartAndVersion 처리 결과를 조회해 반환한다. */
        @Override public Optional<ChartVersion> findVersionByChartAndVersion(UUID tenantId, UUID chartId,
                                                                             String version) { return Optional.empty(); }
        /** FakeRepository의 saveVersion 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public ChartVersion saveVersion(ChartVersion value) { return value; }
        /** FakeRepository의 findCharts 처리 결과를 조회해 반환한다. */
        @Override public List<TenantChart> findCharts(UUID tenantId, boolean includeArchived, int limit) { return List.of(); }
        /** FakeRepository의 findVersions 처리 결과를 조회해 반환한다. */
        @Override public List<ChartVersion> findVersions(UUID tenantId, UUID chartId, int limit) { return List.of(); }
        /** FakeRepository의 findVersions 처리 결과를 조회해 반환한다. */
        @Override public List<ChartVersion> findVersions(UUID tenantId, Collection<UUID> chartIds,
                                                         int perChartLimit) { return List.of(); }
        /** FakeRepository의 findVersion 처리 결과를 조회해 반환한다. */
        @Override public Optional<ChartVersion> findVersion(UUID tenantId, UUID versionId) { return Optional.empty(); }
        /** FakeRepository의 loadArtifact 처리 결과를 조회해 반환한다. */
        @Override public byte[] loadArtifact(UUID tenantId, UUID versionId) { throw unsupported(); }
        /** FakeRepository의 saveProfile 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public ValuesProfile saveProfile(ValuesProfile value) { throw unsupported(); }
        /** FakeRepository의 findProfiles 처리 결과를 조회해 반환한다. */
        @Override public List<ValuesProfile> findProfiles(UUID tenantId, UUID chartVersionId, int limit) { return List.of(); }
        /** FakeRepository의 findProfile 처리 결과를 조회해 반환한다. */
        @Override public Optional<ValuesProfile> findProfile(UUID tenantId, UUID profileId) { return Optional.empty(); }
        /** FakeRepository의 nextRevision 처리에 필요한 업무 로직을 수행한다. */
        @Override public int nextRevision(UUID profileId) { throw unsupported(); }
        /** FakeRepository의 saveRevision 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public ValuesRevision saveRevision(ValuesRevision value) { throw unsupported(); }
        /** FakeRepository의 findRevisions 처리 결과를 조회해 반환한다. */
        @Override public List<ValuesRevision> findRevisions(UUID tenantId, UUID profileId, int limit) { return List.of(); }
        /** FakeRepository의 findRevision 처리 결과를 조회해 반환한다. */
        @Override public Optional<ValuesRevision> findRevision(UUID tenantId, UUID revisionId) { return Optional.empty(); }

        /** FakeRepository의 unsupported 처리에 필요한 업무 로직을 수행한다. */
        private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException(); }
    }
}
