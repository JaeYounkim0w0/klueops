package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class HelmValuesReferencesTest {
    /** 페이지 경계 이후 들여쓰기가 복귀해도 실제 루트 경로를 안내한다. */
    @Test void indexesExactPathsAcrossDedentsAndAliases() {
        String text = "server:\n  storage:\n    size: 5Gi\n  replicas: 1\n";
        assertThat(ValuesReferencePaths.index("values.yaml", text).values().toString())
                .contains("/server/storage/size", "/server/replicas").doesNotContain("/storage/replicas");
        assertThat(ValuesReferencePaths.index("charts/database/values.yaml", "service:\n  port: 5432\n").values().toString())
                .contains("/database/service/port");
    }
    /** 주석·한글과 credential 뒤의 원문 위치가 마스킹 후에도 보존된다. */
    @Test void keepsCommentsAndMasksSecrets() {
        String text = "# 한글 설명 😀\nservice:\n  type: ClusterIP\nauth:\n  password: real-secret\n  existingSecret: approved\n# password: comment-secret\n";
        String masked = ValuesReferenceText.mask(text);
        assertThat(masked).contains("# 한글 설명 😀", "type: ClusterIP", "existingSecret: approved")
                .doesNotContain("real-secret", "comment-secret");
        ChartValuesEligibility.parse(masked);
    }

    /** 큰 자료의 뒷부분도 색인으로 조회할 수 있고 길이 초과를 파일 없음으로 오인하지 않는다. */
    @Test void retrievesLatePagesWithoutTruncation() {
        var f = new GroundedValuesFixture();
        f.defaults = "# 설명\n" + "# large description\n".repeat(1300) + "service:\n  nodePort: 30001\n";
        var refs = new HelmValuesReferences(f.inspector.inspect(new byte[]{1}));
        assertThat(refs.index()).contains("ref4");
        assertThat(refs.read(refs.initial("nodePort"))).contains("nodePort: 30001");
        assertThat(refs.initialEvidence("서비스 포트를 변경해 주세요")).contains("COMPLETE ROOT DEFAULT VALUES", "\"nodePort\":30001");
        assertThatThrownBy(() -> refs.read(List.of("unknown"))).isInstanceOf(IllegalArgumentException.class);
    }

    /** 기본 Values는 비어 있거나 복수 문서·중복 키이면 편입하지 않는다. */
    @Test void rejectsUnusableDefaultValues() {
        for (String text : Arrays.asList(null, "", "{}", "[]", "x: 1\nx: 2", "x: 1\n---\ny: 2"))
            assertThatThrownBy(() -> ChartValuesEligibility.requireUsable(text)).isInstanceOf(IllegalArgumentException.class);
    }

    /** dependency alias의 기본값을 부모 override 아래에 보완한다. */
    @Test void mergesDependencyValuesWithoutOverwritingParent() {
        var archive = new io.strato.aiops.application.port.out.ChartArchiveInspectionPort.InspectedArchive("sample", "1", "1", "", "",
                "database:\n  enabled: false\n", null, 4, 100, Set.of(), Map.of("charts/database/values.yaml", "enabled: true\nservice:\n  port: 5432\n"));
        var combined = HelmValuesDefaults.combined(archive);
        assertThat(combined.at("/database/enabled").asBoolean()).isFalse();
        assertThat(combined.at("/database/service/port").asInt()).isEqualTo(5432);
    }

    /** 루트 Values가 정상이라면 설정이 없는 하위 library Chart도 함께 사용할 수 있다. */
    @Test void acceptsCommentOnlyLibraryDependencyValues() {
        var archive = new io.strato.aiops.application.port.out.ChartArchiveInspectionPort.InspectedArchive("sample", "1", "1", "", "",
                "replicas: 1\n", null, 4, 100, Set.of(), Map.of("charts/common/values.yaml", "# library chart has no configurable values\n"));
        assertThat(HelmValuesDefaults.combined(archive).at("/replicas").asInt()).isEqualTo(1);
        assertThat(new HelmValuesReferences(archive).read(List.of("ref0"))).contains("replicas: 1");
        assertThatThrownBy(() -> ChartValuesEligibility.requireUsable("# no root values\n")).isInstanceOf(IllegalArgumentException.class);
    }

    /** 기본값에서 빠진 템플릿 전용 옵션도 색인에서 직접 발견할 수 있다. */
    @Test void exposesOptionalTemplatePaths() {
        var archive = new io.strato.aiops.application.port.out.ChartArchiveInspectionPort.InspectedArchive("sample", "1", "1", "", "",
                "server:\n  service:\n    type: ClusterIP\n", null, 3, 100, Set.of("/server/service/nodePort"));
        assertThat(new HelmValuesReferences(archive).index()).contains("/server/service/nodePort", "OMITTED FROM DEFAULT");
    }

    /** 다중 행 credential을 가려도 다음 YAML key와 개행이 유지된다. */
    @Test void preservesNewlineAfterBlockCredential() {
        String masked = ValuesReferenceText.mask("auth:\n  password: |\n    first-secret\n    second-secret\nservice:\n  type: ClusterIP\n");
        assertThat(masked).doesNotContain("first-secret", "second-secret");
        assertThat(ChartValuesEligibility.parse(masked).at("/service/type").asText()).isEqualTo("ClusterIP");
    }

    /** 민감 mapping 뒤의 형제 key 들여쓰기와 개행도 마스킹 과정에서 유지한다. */
    @Test void preservesIndentationAfterSensitiveMapping() {
        String masked = ValuesReferenceText.mask("auth:\n  secretKeys:\n    password: private-data\n  # next property\n  allowed: true\n");
        assertThat(masked).doesNotContain("private-data");
        assertThat(ChartValuesEligibility.parse(masked).at("/auth/allowed").asBoolean()).isTrue();
    }
}
