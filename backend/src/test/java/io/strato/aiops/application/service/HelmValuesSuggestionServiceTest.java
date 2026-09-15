package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.ApplicationDeliveryRepositoryPort;
import io.strato.aiops.application.port.out.ChartArchiveInspectionPort;
import io.strato.aiops.application.port.out.HelmValuesSuggestionPort;
import io.strato.aiops.domain.applicationdelivery.ChartSource;
import io.strato.aiops.domain.applicationdelivery.ChartSourceType;
import io.strato.aiops.domain.applicationdelivery.ChartTrustStatus;
import io.strato.aiops.domain.applicationdelivery.ChartVersion;
import io.strato.aiops.domain.applicationdelivery.TenantChart;
import io.strato.aiops.domain.applicationdelivery.ValuesProfile;
import io.strato.aiops.domain.applicationdelivery.ValuesRevision;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelmValuesSuggestionServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID chartId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final TenantChart chart = new TenantChart(chartId, tenantId, "nginx", "NGINX",
            ChartSourceType.ARTIFACT_HUB, "cloudpirates-nginx", "oci://registry.example/nginx", "nginx",
            ChartTrustStatus.CHECKSUMMED, null, "tester", Instant.parse("2026-09-15T00:00:00Z"),
            Instant.parse("2026-09-15T00:00:00Z"));
    private final ChartVersion version = new ChartVersion(versionId, chartId, "0.16.8", "1.31.5",
            "oci://registry.example/nginx:0.16.8", "digest", ChartTrustStatus.CHECKSUMMED, UUID.randomUUID(),
            "{}", "tester", Instant.parse("2026-09-15T00:00:00Z"));

    @Test
    void retriesWithHelmFeedbackAndRestoresMaskedSecrets() {
        List<HelmValuesSuggestionPort.SuggestionRequest> requests = new ArrayList<>();
        HelmValuesSuggestionPort ai = (ignored, request) -> {
            requests.add(request);
            if (requests.size() == 1) return "service:\n  type: ClusterIP\n  ports:\n    - port: 80\n      targetPort: 80\nauth:\n  password: '***REDACTED***'\n";
            return "service:\n  type: ClusterIP\n  ports:\n    - name: http\n      port: 80\n      targetPort: http\nauth:\n  password: '***REDACTED***'\n";
        };
        HelmReleaseCommandRunner helm = validatingRunner();
        HelmValuesSuggestionService service = new HelmValuesSuggestionService(new FakeRepository(), inspector(), ai,
                helm, new ObjectMapper());

        var result = service.suggest(tenantId, versionId, "auth:\n  password: keep-me\n",
                "ClusterIP Service를 사용해줘");

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.validationStatus()).isEqualTo("HELM_TEMPLATE_VALIDATED");
        assertThat(result.valuesYaml()).contains("targetPort: \"http\"", "password: \"keep-me\"");
        assertThat(requests.get(0).currentValuesYaml()).contains("password: \"***REDACTED***\"")
                .doesNotContain("keep-me");
        assertThat(requests.get(0).providerName()).isEqualTo("cloudpirates-nginx");
        assertThat(requests.get(0).chartVersion()).isEqualTo("0.16.8");
        assertThat(requests.get(0).defaultValuesSkeleton()).contains("targetPort: \"http\"");
        assertThat(requests.get(1).validationFeedback()).contains("targetPort must be a named string");
    }

    @Test
    void rejectsManualValuesThatDoNotRenderForTheExactChart() {
        HelmValuesSuggestionService service = new HelmValuesSuggestionService(new FakeRepository(), inspector(),
                (ignored, request) -> "{}", validatingRunner(), new ObjectMapper());

        assertThatThrownBy(() -> service.requireValid(tenantId, versionId,
                "service:\n  ports:\n    - port: 80\n      targetPort: 80\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetPort must be a named string");
    }

    private ChartArchiveInspectionPort inspector() {
        return ignored -> new ChartArchiveInspectionPort.InspectedArchive("nginx", "0.16.8", "1.31.5", "NGINX",
                "name: nginx\nversion: 0.16.8\n", "service:\n  type: ClusterIP\n  ports:\n    - port: 80\n      targetPort: http\n",
                "{\"type\":\"object\",\"properties\":{\"service\":{\"type\":\"object\"}}}", 3, 1024);
    }

    private HelmReleaseCommandRunner validatingRunner() {
        return new HelmReleaseCommandRunner((arguments, timeout) -> null) {
            @Override
            public String render(String releaseName, String namespace, byte[] archive, String values) {
                if (values.contains("targetPort: 80")) {
                    throw new IllegalArgumentException("targetPort must be a named string");
                }
                return "apiVersion: v1\nkind: Service\n";
            }
        };
    }

    private final class FakeRepository implements ApplicationDeliveryRepositoryPort {
        @Override public Optional<TenantChart> findChart(UUID tenant, UUID id) { return match(tenant, tenantId) && id.equals(chartId) ? Optional.of(chart) : Optional.empty(); }
        @Override public Optional<ChartVersion> findVersion(UUID tenant, UUID id) { return match(tenant, tenantId) && id.equals(versionId) ? Optional.of(version) : Optional.empty(); }
        @Override public byte[] loadArtifact(UUID tenant, UUID id) { return new byte[]{1}; }
        @Override public ChartSource saveSource(ChartSource value) { throw unsupported(); }
        @Override public List<ChartSource> findSources(UUID tenant, int limit) { return List.of(); }
        @Override public Optional<ChartSource> findSource(UUID tenant, UUID id) { return Optional.empty(); }
        @Override public void deleteSource(UUID tenant, UUID id) { throw unsupported(); }
        @Override public Artifact saveArtifact(String digest, byte[] payload, Instant now) { throw unsupported(); }
        @Override public Optional<TenantChart> findChartByCoordinate(UUID tenant, ChartSourceType type, String source, String packageName) { return Optional.empty(); }
        @Override public TenantChart saveChart(TenantChart value) { throw unsupported(); }
        @Override public Optional<ChartVersion> findVersionByChartAndVersion(UUID tenant, UUID id, String value) { return Optional.empty(); }
        @Override public ChartVersion saveVersion(ChartVersion value) { throw unsupported(); }
        @Override public List<TenantChart> findCharts(UUID tenant, boolean archived, int limit) { return List.of(); }
        @Override public List<ChartVersion> findVersions(UUID tenant, UUID id, int limit) { return List.of(); }
        @Override public List<ChartVersion> findVersions(UUID tenant, Collection<UUID> ids, int limit) { return List.of(); }
        @Override public ValuesProfile saveProfile(ValuesProfile value) { throw unsupported(); }
        @Override public List<ValuesProfile> findProfiles(UUID tenant, UUID id, int limit) { return List.of(); }
        @Override public Optional<ValuesProfile> findProfile(UUID tenant, UUID id) { return Optional.empty(); }
        @Override public int nextRevision(UUID id) { throw unsupported(); }
        @Override public ValuesRevision saveRevision(ValuesRevision value) { throw unsupported(); }
        @Override public List<ValuesRevision> findRevisions(UUID tenant, UUID id, int limit) { return List.of(); }
        @Override public Optional<ValuesRevision> findRevision(UUID tenant, UUID id) { return Optional.empty(); }
        private UnsupportedOperationException unsupported() { return new UnsupportedOperationException(); }
        private boolean match(UUID left, UUID right) { return left.equals(right); }
    }
}
