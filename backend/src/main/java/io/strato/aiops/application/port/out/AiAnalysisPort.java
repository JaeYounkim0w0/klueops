package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface AiAnalysisPort {

    String analyze(String sanitizedKubernetesContext);

    default String analyze(UUID tenantId, String sanitizedKubernetesContext) {
        return analyze(sanitizedKubernetesContext);
    }

    default String analyzeSection(String sectionName, String sectionInstruction, String sanitizedKubernetesContext) {
        return analyze("""
                analysisSection=%s
                sectionInstruction=%s

                %s
                """.formatted(sectionName, sectionInstruction, sanitizedKubernetesContext));
    }

    default String analyzeSection(UUID tenantId, String sectionName, String sectionInstruction,
                                  String sanitizedKubernetesContext) {
        return analyzeSection(sectionName, sectionInstruction, sanitizedKubernetesContext);
    }
}
