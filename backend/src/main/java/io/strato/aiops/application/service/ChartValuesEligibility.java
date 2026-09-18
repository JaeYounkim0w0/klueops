package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.StreamReadFeature;

/** Library 편입과 AI 생성에 사용할 기본 Values의 최소 품질을 확인한다. */
public final class ChartValuesEligibility {
    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    private ChartValuesEligibility() { }

    /** 기존 Library 항목은 삭제하지 않고 편집 화면에 사유를 안내한다. */
    public static String problem(String source) {
        try { requireUsable(source); return null; }
        catch (IllegalArgumentException failure) { return failure.getMessage(); }
    }

    /** 기본 Values가 없거나 비어 있거나 중복 키를 포함하면 명확한 사유로 거부한다. */
    public static JsonNode requireUsable(String source) {
        if (source == null) throw new IllegalArgumentException("Chart에 values.yaml이 없어 Library로 가져올 수 없습니다.");
        JsonNode values = parse(source);
        if (values.isEmpty()) throw new IllegalArgumentException("Chart의 values.yaml이 비어 있어 설정 근거로 사용할 수 없습니다.");
        return values;
    }

    /** 단일 YAML mapping만 허용하고 parser 예외에 포함된 원문은 노출하지 않는다. */
    public static JsonNode parse(String source) {
        return parse(source, false);
    }

    /** 하위 library Chart의 주석뿐인 Values는 설정이 없는 빈 mapping으로 취급한다. */
    static JsonNode parseDependency(String source) { return parse(source, true); }

    /** 루트 설정과 선택적인 하위 설정의 빈 문서 정책만 구분하며 문법 검증은 공유한다. */
    private static JsonNode parse(String source, boolean allowEmpty) {
        if (source == null || source.length() > 1024 * 1024)
            throw new IllegalArgumentException("Values는 최대 1 MiB의 YAML object여야 합니다.");
        try (var parser = YAML.createParser(source)) {
            JsonNode value = YAML.readTree(parser);
            if (allowEmpty && (value == null || value.isMissingNode())) return YAML.createObjectNode();
            if (value == null || !value.isObject() || parser.nextToken() != null)
                throw new IllegalArgumentException("Values는 하나의 YAML object여야 합니다.");
            return value;
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("Values YAML 문법 또는 중복 키를 확인해 주세요.");
        }
    }
}
