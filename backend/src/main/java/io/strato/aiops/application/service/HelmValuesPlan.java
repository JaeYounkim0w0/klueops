package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** 모델 응답의 형식·요구사항 연결을 검증하고 UI와 YAML 합성용 계획으로 변환한다. */
record HelmValuesPlan(List<HelmValuesAssistanceService.Requirement> requirements,
                      List<HelmValuesMapping.Change> changes, List<String> questions) {
    /** 누락·중복 식별자와 유지 요청의 변경을 거부한다. */
    static HelmValuesPlan parse(JsonNode response) {
        List<HelmValuesAssistanceService.Requirement> requirements = new ArrayList<>();
        Set<String> ids = new HashSet<>(), covered = new HashSet<>(), preserved = new HashSet<>();
        for (JsonNode item : array(response, "requirements", 30)) {
            String id = text(item, "id"), kind = text(item, "kind");
            if (!ids.add(id) || !Set.of("CHANGE", "PRESERVE").contains(kind)) throw new IllegalArgumentException("Invalid requirement identity");
            requirements.add(new HelmValuesAssistanceService.Requirement(id, text(item, "request"), kind));
            if (kind.equals("PRESERVE")) { preserved.add(id); covered.add(id); }
        }
        List<String> questions = new ArrayList<>();
        for (JsonNode question : array(response, "questions", 20)) {
            if (!question.isTextual() || question.asText().isBlank() || question.asText().length() > 1000)
                throw new IllegalArgumentException("Invalid clarification question");
            questions.add(question.asText());
        }
        if (!questions.isEmpty()) return new HelmValuesPlan(requirements, List.of(), questions);
        if (requirements.isEmpty()) throw new IllegalArgumentException("No requirements returned");
        List<HelmValuesMapping.Change> changes = new ArrayList<>();
        for (JsonNode item : array(response, "items", 80)) {
            String id = text(item, "requirementId"), status = text(item, "status");
            if (!ids.contains(id)) throw new IllegalArgumentException("Unknown requirement id");
            if (status.equals("UNCHANGED") && preserved.contains(id)) continue;
            if (!status.equals("MAPPED") || preserved.contains(id)) throw new IllegalArgumentException("Invalid requirement mapping");
            changes.add(new HelmValuesMapping.Change(id, text(item, "path"), item.get("value"), text(item, "explanation")));
            covered.add(id);
        }
        if (!covered.containsAll(ids)) throw new IllegalArgumentException("Some requirements have no mapping");
        return new HelmValuesPlan(List.copyOf(requirements), List.copyOf(changes), List.of());
    }

    /** 응답 크기와 JSON 중복 키를 제한하고 코드블록만 제거한다. */
    static JsonNode decode(ObjectMapper json, String value) throws java.io.IOException {
        if (value == null || value.length() > 48000) throw new IllegalArgumentException("Invalid AI response size");
        String text = value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        try (var parser = json.createParser(text)) {
            parser.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            JsonNode node = json.readTree(parser);
            if (node == null || !node.isObject() || parser.nextToken() != null) throw new IllegalArgumentException("JSON object required");
            return node;
        }
    }

    /** 필수 목록과 길이를 확인한다. */
    private static JsonNode array(JsonNode node, String key, int maximum) {
        JsonNode value = node.path(key);
        if (!value.isArray() || value.size() > maximum) throw new IllegalArgumentException("Invalid array: " + key);
        return value;
    }

    /** 모델의 표시 문자열 길이와 타입을 제한한다. */
    private static String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 1000)
            throw new IllegalArgumentException("Invalid field: " + key);
        return value.asText();
    }
}
