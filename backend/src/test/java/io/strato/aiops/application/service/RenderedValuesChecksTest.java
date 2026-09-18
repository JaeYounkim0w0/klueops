package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RenderedValuesChecksTest {
    private final ObjectMapper json = new ObjectMapper();
    private final List<HelmValuesAssistanceService.Requirement> requirements = List.of(
            new HelmValuesAssistanceService.Requirement("r1", "서비스 외부 포트", "CHANGE"));

    /** 여러 Service 중 모든 확인 조건을 만족하는 단 하나만 선택한다. */
    @Test void resolvesUniqueWildcardAndNumericArrayPath() throws Exception {
        var f = new GroundedValuesFixture();
        String checks = """
                [{"requirementId":"r1","kind":"Service","name":"*","path":"/spec/type","value":"NodePort"},
                 {"requirementId":"r1","kind":"Service","name":"*","path":"/spec/ports/0/nodePort","value":30001}]
                """;
        String other = f.manifest.replace("sample", "other").replace("30001", "30002");
        assertThat(RenderedValuesChecks.verify(f.manifest + "---\n" + other, json.readTree(checks), requirements)).hasSize(1);
        assertThatThrownBy(() -> RenderedValuesChecks.verify(f.manifest + "---\n" + other.replace("30002", "30001"),
                json.readTree(checks), requirements)).hasMessageContaining("Ambiguous");
    }

    /** 서로 다른 Service가 각각 일부 조건만 만족해도 성공으로 처리하지 않는다. */
    @Test void rejectsSplitOutcomesAndProvidesSafePaths() throws Exception {
        var f = new GroundedValuesFixture();
        String checks = """
                [{"requirementId":"r1","kind":"Service","name":"*","path":"/spec/type","value":"LoadBalancer"},
                 {"requirementId":"r1","kind":"Service","name":"*","path":"/spec/ports/*/nodePort","value":30001}]
                """;
        String other = f.manifest.replace("sample", "other").replace("NodePort", "LoadBalancer").replace("30001", "30002");
        assertThatThrownBy(() -> RenderedValuesChecks.verify(f.manifest + "---\n" + other, json.readTree(checks), requirements))
                .hasMessageContaining("not satisfied");
        try {
            RenderedValuesChecks.verify(f.manifest, json.readTree(checks.replace("/spec/type", "spec.type")), requirements);
            fail("잘못된 경로가 거부되어야 합니다.");
        } catch (HelmValuesValidationFailure failure) {
            assertThat(HelmValuesValidationFailure.correctionFor(failure)).contains("/spec/ports/*/nodePort", "sample").doesNotContain("30001");
        }
    }
}
