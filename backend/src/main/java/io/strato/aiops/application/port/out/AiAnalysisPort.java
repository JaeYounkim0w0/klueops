package io.strato.aiops.application.port.out;

public interface AiAnalysisPort {

    String analyze(String sanitizedKubernetesContext);

    default String analyzeSection(String sectionName, String sectionInstruction, String sanitizedKubernetesContext) {
        return analyze("""
                analysisSection=%s
                sectionInstruction=%s

                %s
                """.formatted(sectionName, sectionInstruction, sanitizedKubernetesContext));
    }
}
