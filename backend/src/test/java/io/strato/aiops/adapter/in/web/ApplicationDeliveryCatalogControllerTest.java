package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.ApplicationDeliveryCatalogService;
import io.strato.aiops.domain.applicationdelivery.ChartSourceType;
import io.strato.aiops.domain.applicationdelivery.ChartTrustStatus;
import io.strato.aiops.domain.applicationdelivery.TenantChart;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationDeliveryCatalogControllerTest {

    /** ApplicationDeliveryCatalogControllerTest의 mapsProviderSeparatelyFromRepositoryUrl 처리 데이터를 필요한 표현으로 변환한다. */
    @Test
    void mapsProviderSeparatelyFromRepositoryUrl() {
        Instant now = Instant.parse("2026-09-16T00:00:00Z");
        TenantChart chart = new TenantChart(UUID.randomUUID(), UUID.randomUUID(), "nginx", "NGINX",
                ChartSourceType.ARTIFACT_HUB, "cloudpirates-nginx", "CloudPirates",
                "https://example.test/nginx", "nginx", ChartTrustStatus.CHECKSUMMED,
                null, "tester", now, now);

        var response = ApplicationDeliveryCatalogController.LibraryChartResponse.from(
                new ApplicationDeliveryCatalogService.LibraryChart(chart, List.of()));

        assertThat(response.providerName()).isEqualTo("CloudPirates");
        assertThat(response.repositoryUrl()).isEqualTo("https://example.test/nginx");
    }
}
