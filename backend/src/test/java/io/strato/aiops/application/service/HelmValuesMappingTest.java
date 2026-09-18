package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class HelmValuesMappingTest {
    private final ObjectMapper json = new ObjectMapper();

    /** 여러 잘못된 상위 경로를 한 번에 안내하되 후보를 임의 적용하지 않는다. */
    @Test void returnsAllUnknownPathsWithActualFieldCandidates() throws Exception {
        var contract = new HelmValuesMapping(json, json.readTree("{\"server\":{\"replicas\":1,\"retention\":\"7d\"}}"), json.createObjectNode(), Set.of());
        var failure = catchThrowableOfType(() -> contract.apply(json.createObjectNode(), List.of(
                new HelmValuesMapping.Change("r1", "/server/storage/replicas", json.getNodeFactory().numberNode(1), ""),
                new HelmValuesMapping.Change("r2", "/server/storage/retention", json.getNodeFactory().textNode("7d"), ""))),
                HelmValuesValidationFailure.class);
        assertThat(failure).hasMessageContaining("/server/storage/replicas", "/server/storage/retention");
        assertThat(HelmValuesValidationFailure.correctionFor(failure)).contains("/server/storage/replicas", "/server/storage/retention", "/server/replicas", "/server/retention");
    }

    /** Chart의 열린 mapping 하위만 확장하고 기존 무관한 리소스 설정을 유지한다. */
    @Test void extendsOpenObjectWithoutReplacingExistingValues() throws Exception {
        var contract = new HelmValuesMapping(json, json.readTree("{\"resources\":{},\"service\":{\"type\":\"ClusterIP\"}}"), json.createObjectNode(), Set.of());
        var result = contract.apply(json.readTree("{\"resources\":{\"requests\":{\"memory\":\"256Mi\"}}}"), List.of(
                new HelmValuesMapping.Change("cpu", "/resources/requests/cpu", json.getNodeFactory().textNode("100m"), "CPU 요청")));
        assertThat(result.at("/resources/requests/memory").asText()).isEqualTo("256Mi");
        assertThat(result.at("/resources/requests/cpu").asText()).isEqualTo("100m");
        assertThatThrownBy(() -> contract.apply(json.createObjectNode(), List.of(
                new HelmValuesMapping.Change("bad", "/service/imaginary", json.getNodeFactory().numberNode(1), "미지원"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 동일 경로와 부모·자식 변경 충돌은 덮어쓰지 않고 거부한다. */
    @Test void rejectsOverlappingChanges() throws Exception {
        var contract = new HelmValuesMapping(json, json.readTree("{\"resources\":{}}"), json.createObjectNode(), Set.of());
        assertThatThrownBy(() -> contract.apply(json.createObjectNode(), List.of(
                new HelmValuesMapping.Change("a", "/resources", json.createObjectNode(), "전체"),
                new HelmValuesMapping.Change("b", "/resources/cpu", json.getNodeFactory().numberNode(1), "하위"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** object 단위 제안에서도 요청에 없는 기존 하위 field를 삭제하지 않는다. */
    @Test void mergesObjectPatchWithoutDroppingSiblingFields() throws Exception {
        var contract = new HelmValuesMapping(json, json.readTree("{\"resources\":{}}"), json.createObjectNode(), Set.of());
        var current = json.readTree("{\"resources\":{\"requests\":{\"memory\":\"256Mi\"}}}");
        var patch = json.readTree("{\"requests\":{\"cpu\":\"100m\"}}");
        var result = contract.apply(current, List.of(new HelmValuesMapping.Change("cpu", "/resources", patch, "CPU 요청")));
        assertThat(result.at("/resources/requests/memory").asText()).isEqualTo("256Mi");
        assertThat(result.at("/resources/requests/cpu").asText()).isEqualTo("100m");
        assertThat(current.at("/resources/requests/cpu").isMissingNode()).isTrue();
    }
}
