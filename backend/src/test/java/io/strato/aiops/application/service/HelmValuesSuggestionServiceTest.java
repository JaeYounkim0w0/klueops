package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HelmValuesSuggestionServiceTest {
    /** 수동 저장도 생성과 같은 미지원 경로 검증을 적용한다. */
    @Test void rejectsUnsupportedManualKeys() {
        var f = new GroundedValuesFixture();
        var service = validator(f);
        assertThatThrownBy(() -> service.requireValid(f.tenant, f.versionId, "wrong:\n  replicas: 1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported Values path");
    }

    /** 실제 렌더된 Service의 포트 오류를 저장 전에 차단한다. */
    @Test void rejectsInvalidRenderedService() {
        var f = new GroundedValuesFixture();
        f.manifest = f.manifest.replace("30001", "35432");
        assertThatThrownBy(() -> validator(f).requireValid(f.tenant, f.versionId, "{}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("35432");
    }

    /** Values와 Kubernetes Manifest를 혼동한 입력은 거부한다. */
    @Test void rejectsManifestAndMissingDefaults() {
        var f = new GroundedValuesFixture();
        assertThatThrownBy(() -> validator(f).requireValid(f.tenant, f.versionId, "apiVersion: v1\nkind: Service\nmetadata: {}"))
                .isInstanceOf(IllegalArgumentException.class);
        f.defaults = "{}";
        assertThatThrownBy(() -> validator(f).requireValid(f.tenant, f.versionId, "{}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("비어");
    }

    /** 생성 credential과 사용자에게 근거가 없는 Secret 참조를 차단한다. */
    @Test void rejectsGeneratedSecrets() {
        var protection = new ValuesSecretProtection();
        assertThatThrownBy(() -> protection.validatePlannedCandidate("env:\n- name: ADMIN_PASSWORD\n  value: generated", "설정", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> protection.validatePlannedCandidate("auth:\n  existingSecret: invented", "설정", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(protection.validatePlannedCandidate("auth:\n  existingSecret: approved", "approved Secret 사용", "{}"))
                .contains("approved");
    }

    /** 기존 Secret과 순서가 바뀐 환경변수의 민감값을 이름으로 복원한다. */
    @Test void preservesNamedEnvironmentSecrets() {
        var protection = new ValuesSecretProtection();
        String current = "env:\n- name: APP_PASSWORD\n  value: keep-me\n- name: MODE\n  value: dev\n";
        String candidate = "env:\n- name: MODE\n  value: dev\n- name: APP_PASSWORD\n  value: '***REDACTED***'\n";
        assertThat(protection.compactAndMaskYaml(current)).doesNotContain("keep-me");
        assertThat(protection.validatePlannedCandidate(candidate, "유지", current)).contains("keep-me").doesNotContain("REDACTED");
    }

    /** fake Helm과 정확한 Chart를 공유하는 검증기를 만든다. */
    private HelmValuesSuggestionService validator(GroundedValuesFixture f) {
        return new HelmValuesSuggestionService(f.repository, f.inspector, (tenant, request) -> "{}", f.runner, f.json);
    }
}
