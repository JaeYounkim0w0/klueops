package io.strato.aiops.adapter.out.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Chart별 규칙이 아닌 모델 응답 형식만 구조화 출력으로 고정한다. */
final class HelmValuesResponseSchema {
    private static final JsonNode SCHEMA = load();
    private HelmValuesResponseSchema() { }

    /** 호출별 본문에 넣을 독립 schema 복사본을 반환한다. */
    static JsonNode copy() { return SCHEMA.deepCopy(); }

    /** 시작 시 포함된 계약을 읽으며 누락된 계약으로 느슨하게 실행하지 않는다. */
    private static JsonNode load() {
        try (var input = HelmValuesResponseSchema.class.getResourceAsStream("/ai/helm-values-response.schema.json")) {
            if (input == null) throw new IllegalStateException("Values response schema is missing");
            return new ObjectMapper().readTree(input);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Values response schema is invalid", failure); }
    }
}
