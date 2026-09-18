package io.strato.aiops.application.service;

/** 검증 오류 원문과 분리된 안전한 교정 지시를 제공한다. */
final class HelmValuesValidationFailure extends IllegalArgumentException {
    private final String correction;

    /** 기존 오류 메시지는 유지하되 AI에는 상수로 정의된 교정 지시만 전달한다. */
    HelmValuesValidationFailure(String message, String correction) {
        super(message);
        this.correction = correction;
    }

    /** credential이나 Helm stderr를 포함하지 않는 안전한 교정 설명을 반환한다. */
    static String correctionFor(IllegalArgumentException failure) {
        if (failure instanceof HelmValuesValidationFailure known) return known.correction;
        return "The previous mapping failed structural or Chart validation. Rebuild the complete mapping from verified paths, "
                + "exact reference types and the original request. Do not invent paths, overlap parent/child changes, "
                + "or change PRESERVE requirements. If evidence is insufficient return a specific clarification question.";
    }
}
