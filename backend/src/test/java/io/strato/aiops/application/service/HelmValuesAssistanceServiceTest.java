package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HelmValuesAssistanceServiceTest {
    /** 일반 요청은 한 번의 AI 호출로 처리하며 주석·identity와 기존 민감값을 보존한다. */
    @Test void generatesFromOriginalValuesInOneCall() {
        var f = new GroundedValuesFixture();
        var result = f.service(f.plan(30001)).assist(f.tenant, f.versionId, "auth:\n  password: keep-me\nserver:\n  replicaCount: 2\n", "NodePort 30001");
        assertThat(result.validationStatus()).isEqualTo("HELM_TEMPLATE_VALIDATED");
        assertThat(result.valuesYaml()).contains("nodePort: 30001", "replicaCount: 2", "keep-me");
        assertThat(f.requests).hasSize(1);
        assertThat(f.requests.get(0).defaultValuesSkeleton()).contains("# Service exposure configuration");
        assertThat(f.requests.get(0).currentValuesYaml()).doesNotContain("keep-me");
        assertThat(f.requests.get(0).providerName()).isEqualTo("Test Provider");
    }

    /** 빈 편집기는 유효한 override로 처리한다. */
    @Test void acceptsBlankOverrides() {
        var f = new GroundedValuesFixture();
        assertThat(f.service(f.plan(30001)).assist(f.tenant, f.versionId, "  ", "NodePort 30001").validationStatus())
                .isEqualTo("HELM_TEMPLATE_VALIDATED");
    }

    /** 첫 자료로 부족하면 주소로 추가 조회한 뒤 동일한 요청을 처리한다. */
    @Test void readsAdditionalEvidence() {
        var f = new GroundedValuesFixture();
        var result = f.service("{\"referenceIds\":[\"ref0\"]}", f.plan(30001)).assist(f.tenant, f.versionId, "{}", "NodePort 30001");
        assertThat(result.validationStatus()).isEqualTo("HELM_TEMPLATE_VALIDATED");
        assertThat(f.requests).hasSize(2);
    }

    /** 잘못된 제공사 경로는 제한 교정 후 정확한 경로로 바뀌어야 한다. */
    @Test void repairsUnsupportedPaths() {
        var f = new GroundedValuesFixture();
        var result = f.service(f.plan(30001).replace("/server/service/", "/wrong/service/"), f.plan(30001))
                .assist(f.tenant, f.versionId, "{}", "NodePort 30001");
        assertThat(result.validationStatus()).isEqualTo("HELM_TEMPLATE_VALIDATED");
        assertThat(f.requests).hasSize(2);
        assertThat(f.requests.get(1).validationFeedback()).isNotBlank();
    }

    /** Custom Values 단계는 AI checks가 아닌 Helm 렌더 검증까지만 수행한다. */
    @Test void doesNotRequireAiGeneratedDeploymentChecks() {
        var f = new GroundedValuesFixture();
        f.manifest = f.manifest.replace("30001", "30002");
        var result = f.service(f.plan(30001)).assist(f.tenant, f.versionId, "{}", "NodePort 30001");
        assertThat(result.validationStatus()).isEqualTo("HELM_TEMPLATE_VALIDATED");
        assertThat(result.valuesYaml()).contains("nodePort: 30001");
        assertThat(f.requests).hasSize(1);
    }

    /** 사용자 입력이 모호할 때만 질문을 반환하며 Values는 변경하지 않는다. */
    @Test void clarifiesAmbiguity() {
        var f = new GroundedValuesFixture();
        var result = f.service("{\"requirements\":[],\"questions\":[\"서버만 하나인가요?\"]}")
                .assist(f.tenant, f.versionId, "{}", "Pod 하나");
        assertThat(result.validationStatus()).isEqualTo("NEEDS_INPUT");
        assertThat(result.questions()).contains("서버만 하나인가요?");
    }

    /** 민감한 자연어는 모델에 전송하지 않고 Secret 참조를 요청한다. */
    @Test void asksBeforeTransmittingCredential() {
        var f = new GroundedValuesFixture();
        var result = f.service(f.plan(30001)).assist(f.tenant, f.versionId, "{}", "password: private-value");
        assertThat(result.validationStatus()).isEqualTo("NEEDS_INPUT");
        assertThat(f.requests).isEmpty();
    }

    /** 반복 조회와 파싱 실패가 무한 생성으로 이어지지 않는다. */
    @Test void boundsRepeatedReadsAndMalformedResponses() {
        var f = new GroundedValuesFixture();
        assertThat(f.service("{\"referenceIds\":[\"ref0\"]}").assist(f.tenant, f.versionId, "{}", "설정").validationStatus()).isEqualTo("GENERATION_FAILED");
        assertThat(f.requests).hasSize(2);
        var malformed = new GroundedValuesFixture();
        assertThat(malformed.service("not-json").assist(malformed.tenant, malformed.versionId, "{}", "설정").valuesYaml()).isEmpty();
    }

    /** 다른 Tenant의 artifact는 AI 호출 전에 접근이 거부된다. */
    @Test void isolatesTenantAndRejectsDuplicateYaml() {
        var f = new GroundedValuesFixture();
        assertThatThrownBy(() -> f.service(f.plan(30001)).assist(java.util.UUID.randomUUID(), f.versionId, "{}", "설정"))
                .isInstanceOf(java.util.NoSuchElementException.class);
        assertThatThrownBy(() -> f.service(f.plan(30001)).assist(f.tenant, f.versionId, "x: 1\nx: 2", "설정"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
