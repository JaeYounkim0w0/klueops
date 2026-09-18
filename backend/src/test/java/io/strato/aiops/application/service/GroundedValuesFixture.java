package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.*;
import io.strato.aiops.domain.applicationdelivery.*;
import java.time.Instant;
import java.util.*;

/** 정확한 Chart 계약과 렌더 결과를 갖는 Values 생성 회귀 fixture이다. */
final class GroundedValuesFixture {
    final UUID tenant = UUID.randomUUID(), chartId = UUID.randomUUID(), versionId = UUID.randomUUID();
    final List<HelmValuesSuggestionPort.SuggestionRequest> requests = new ArrayList<>();
    String defaults = "# Service exposure configuration\nserver:\n  replicaCount: 1\n  service:\n    type: ClusterIP\n    servicePort: 80\n    nodePort: null\n  resources: {}\nauth:\n  password: ''\n  existingSecret: ''\nenv: []\n";
    String manifest = "apiVersion: v1\nkind: Service\nmetadata:\n  name: sample\nspec:\n  type: NodePort\n  ports:\n    - port: 80\n      nodePort: 30001\n";
    final ApplicationDeliveryRepositoryPort repository;
    final ChartArchiveInspectionPort inspector;
    final HelmReleaseCommandRunner runner;
    final ObjectMapper json = new ObjectMapper();

    GroundedValuesFixture() {
        Instant now = Instant.now();
        var chart = new TenantChart(chartId, tenant, "sample", "Sample", ChartSourceType.ARTIFACT_HUB,
                "repo", "Test Provider", "https://example.test", "sample", ChartTrustStatus.CHECKSUMMED, null, "test", now, now);
        var version = new ChartVersion(versionId, chartId, "1.0.0", "2.0.0", "source", "digest",
                ChartTrustStatus.CHECKSUMMED, UUID.randomUUID(), "{}", "test", now);
        repository = (ApplicationDeliveryRepositoryPort) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ApplicationDeliveryRepositoryPort.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findVersion" -> tenant.equals(args[0]) ? Optional.of(version) : Optional.empty();
                    case "findChart" -> tenant.equals(args[0]) ? Optional.of(chart) : Optional.empty();
                    case "loadArtifact" -> new byte[]{1};
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        inspector = ignored -> new ChartArchiveInspectionPort.InspectedArchive("sample", "1.0.0", "2.0.0",
                "Sample", "name: sample\nversion: 1.0.0\n", defaults, null, 3, 100, Set.of());
        runner = new HelmReleaseCommandRunner((args, timeout) -> null) {
            /** 제공사별 키 대신 실제 리소스 결과 검증에 사용할 fixture를 반환한다. */
            @Override public String render(String release, String namespace, byte[] archive, String values) { return manifest; }
        };
    }

    /** 순서대로 응답하는 fake provider를 구성한다. */
    HelmValuesAssistanceService service(String... responses) {
        HelmValuesSuggestionPort ai = (ignored, request) -> {
            requests.add(request); return responses[Math.min(requests.size() - 1, responses.length - 1)];
        };
        return new HelmValuesAssistanceService(repository, inspector, ai,
                new HelmValuesSuggestionService(repository, inspector, ai, runner, json), json);
    }

    /** NodePort 요청의 요구사항·변경·렌더 확인 항목을 구성한다. */
    String plan(int port) {
        return """
                {"requirements":[{"id":"r1","kind":"CHANGE","request":"NodePort 30001"}],"questions":[],
                 "items":[{"requirementId":"r1","status":"MAPPED","path":"/server/service/type","value":"NodePort","explanation":"노출 유형"},
                          {"requirementId":"r1","status":"MAPPED","path":"/server/service/nodePort","value":%d,"explanation":"외부 포트"}],
                 "checks":[{"requirementId":"r1","kind":"Service","name":"sample","path":"/spec/ports/*/nodePort","value":30001}]}
                """.formatted(port);
    }
}
