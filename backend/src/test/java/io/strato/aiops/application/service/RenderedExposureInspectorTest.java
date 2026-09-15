package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenderedExposureInspectorTest {
    @Test
    void detectsChartManagedIngressAndHttpRouteAcrossDocuments() {
        String manifest = """
                apiVersion: networking.k8s.io/v1
                kind: Ingress
                ---
                apiVersion: gateway.networking.k8s.io/v1
                kind: HTTPRoute
                """;

        var result = RenderedExposureInspector.detect(manifest);

        assertThat(result.ingress()).isTrue();
        assertThat(result.httpRoute()).isTrue();
        assertThat(result.present()).isTrue();
    }

    @Test
    void rejectsChartManagedModeWhenValuesDoNotRenderARoute() {
        String manifest = """
                apiVersion: v1
                kind: Service
                metadata:
                  name: sample
                """;

        assertThatThrownBy(() -> RenderedExposureInspector.requireChartManagedExposure(manifest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ingress or HTTPRoute");
    }

    @Test
    void ignoresIndentedKindTextInsideConfigMapData() {
        String manifest = """
                apiVersion: v1
                kind: ConfigMap
                data:
                  example: |
                    kind: Ingress
                """;

        assertThat(RenderedExposureInspector.detect(manifest).present()).isFalse();
    }
}
