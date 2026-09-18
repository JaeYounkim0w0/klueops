package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenderedExposureInspectorTest {
    /** RenderedExposureInspectorTest의 detectsChartManagedIngressAndHttpRouteAcrossDocuments 처리에 필요한 업무 로직을 수행한다. */
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

    /** RenderedExposureInspectorTest의 rejectsChartManagedModeWhenValuesDoNotRenderARoute 처리에 필요한 업무 로직을 수행한다. */
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

    /** RenderedExposureInspectorTest의 ignoresIndentedKindTextInsideConfigMapData 처리에 필요한 업무 로직을 수행한다. */
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

    /** RenderedExposureInspectorTest의 extractsServicePortWithoutConfusingTargetPortOrNodePort 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void extractsServicePortWithoutConfusingTargetPortOrNodePort() {
        String manifest = """
                apiVersion: v1
                kind: Service
                metadata:
                  name: redis
                spec:
                  type: NodePort
                  ports:
                    - name: redis
                      port: 80
                      targetPort: 6379
                      nodePort: 30007
                """;

        var services = RenderedExposureInspector.services(manifest, "apps");

        assertThat(services).containsExactly(new RenderedExposureInspector.ServiceOption(
                "apps", "redis", "NodePort", null, "redis", 80, "6379", 30007,
                "TCP", null, "NON_HTTP", "Selected Service port 'redis' (80) is a non-HTTP TCP endpoint. "
                        + "Use port-forward, NodePort, LoadBalancer, or a TCPRoute-capable Gateway"));
        RenderedExposureInspector.requireService(manifest, "apps", "redis", 80);
        assertThatThrownBy(() -> RenderedExposureInspector.requireService(manifest, "apps", "redis", 30007))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rendered Chart");
    }

    /** RenderedExposureInspectorTest의 rejectsPostgresqlTcpPortAsHttpRouteBackend 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsPostgresqlTcpPortAsHttpRouteBackend() {
        String manifest = """
                apiVersion: v1
                kind: Service
                metadata:
                  name: postgres
                  namespace: database
                spec:
                  ports:
                    - name: postgresql
                      port: 5432
                      targetPort: 5432
                """;

        var service = RenderedExposureInspector.services(manifest, "default").get(0);

        assertThat(service.httpRouteCompatibility()).isEqualTo("NON_HTTP");
        assertThatThrownBy(() -> RenderedExposureInspector.requireHttpService(
                manifest, "database", "postgres", 5432))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-HTTP TCP endpoint")
                .hasMessageContaining("TCPRoute");
    }

    /** RenderedExposureInspectorTest의 acceptsDeclaredHttpApplicationProtocolOnACustomPort 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void acceptsDeclaredHttpApplicationProtocolOnACustomPort() {
        String manifest = """
                apiVersion: v1
                kind: Service
                metadata:
                  name: api
                spec:
                  ports:
                    - name: api
                      appProtocol: kubernetes.io/h2c
                      port: 9000
                      targetPort: 9000
                """;

        var service = RenderedExposureInspector.services(manifest, "apps").get(0);

        assertThat(service.httpRouteCompatibility()).isEqualTo("HTTP");
        RenderedExposureInspector.requireHttpService(manifest, "apps", "api", 9000);
    }

    /** Kubernetes API까지 가지 않고 기본 범위를 벗어난 NodePort를 Preview 단계에서 차단한다. */
    @Test
    void rejectsNodePortOutsideTheKubernetesDefaultRange() {
        String manifest = """
                apiVersion: v1
                kind: Service
                metadata:
                  name: postgres
                spec:
                  type: NodePort
                  ports:
                    - name: postgresql
                      port: 5432
                      targetPort: 5432
                      nodePort: 35432
                """;

        assertThatThrownBy(() -> RenderedExposureInspector.requireValidServices(manifest, "database"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postgres")
                .hasMessageContaining("35432")
                .hasMessageContaining("30000-32767");
    }

    /** ClusterIP에 nodePort가 직접 렌더링된 잘못된 Chart도 실행 전에 차단한다. */
    @Test
    void rejectsNodePortRenderedForClusterIpService() {
        String manifest = """
                apiVersion: v1
                kind: Service
                metadata:
                  name: postgres
                spec:
                  type: ClusterIP
                  ports:
                    - port: 5432
                      targetPort: 5432
                      nodePort: 30432
                """;

        assertThatThrownBy(() -> RenderedExposureInspector.requireValidServices(manifest, "database"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Service type is ClusterIP")
                .hasMessageContaining("NodePort or LoadBalancer");
    }

    /** Service type을 clusterIP 주소 필드에 넣은 렌더 결과를 Kubernetes 적용 전에 차단한다. */
    @Test
    void rejectsServiceTypeRenderedAsClusterIpAddress() {
        String manifest = """
                apiVersion: v1
                kind: Service
                metadata:
                  name: prometheus
                spec:
                  clusterIP: NodePort
                  type: NodePort
                  ports:
                    - name: http
                      port: 30001
                      targetPort: 9090
                """;

        assertThatThrownBy(() -> RenderedExposureInspector.requireValidServices(manifest, "monitoring"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prometheus")
                .hasMessageContaining("invalid clusterIP 'NodePort'")
                .hasMessageContaining("ports[].nodePort");
    }

    /** 동적 ClusterIP와 명시 IPv4/IPv6 및 headless None은 유효한 표현으로 허용한다. */
    @Test
    void acceptsValidClusterIpRepresentations() {
        assertThat(RenderedExposureInspector.isValidClusterIpValue(null)).isTrue();
        assertThat(RenderedExposureInspector.isValidClusterIpValue("")).isTrue();
        assertThat(RenderedExposureInspector.isValidClusterIpValue("None")).isTrue();
        assertThat(RenderedExposureInspector.isValidClusterIpValue("10.96.0.10")).isTrue();
        assertThat(RenderedExposureInspector.isValidClusterIpValue("fd00::10")).isTrue();
        assertThat(RenderedExposureInspector.isValidClusterIpValue("NodePort")).isFalse();
        assertThat(RenderedExposureInspector.isValidClusterIpValue("999.1.1.1")).isFalse();
    }
}
