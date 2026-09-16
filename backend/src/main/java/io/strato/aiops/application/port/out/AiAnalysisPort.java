package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface AiAnalysisPort {

    /** AiAnalysisPort의 analyze 처리의 핵심 작업 흐름을 실행한다. */
    String analyze(String sanitizedKubernetesContext);

    /** AiAnalysisPort의 analyze 처리의 핵심 작업 흐름을 실행한다. */
    default String analyze(UUID tenantId, String sanitizedKubernetesContext) {
        return analyze(sanitizedKubernetesContext);
    }

    /** AiAnalysisPort의 analyzeSection 처리의 핵심 작업 흐름을 실행한다. */
    default String analyzeSection(String sectionName, String sectionInstruction, String sanitizedKubernetesContext) {
        return analyze("""
                analysisSection=%s
                sectionInstruction=%s

                %s
                """.formatted(sectionName, sectionInstruction, sanitizedKubernetesContext));
    }

    /** AiAnalysisPort의 analyzeSection 처리의 핵심 작업 흐름을 실행한다. */
    default String analyzeSection(UUID tenantId, String sectionName, String sectionInstruction,
                                  String sanitizedKubernetesContext) {
        return analyzeSection(sectionName, sectionInstruction, sanitizedKubernetesContext);
    }
}
