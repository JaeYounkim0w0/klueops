package io.strato.aiops.adapter.out.ai;

import io.strato.aiops.application.port.out.AiAnalysisPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RoutedAiAnalysisAdapter implements AiAnalysisPort {
    private final AiAnalysisPort localFallback;
    private final ConfiguredAiClient configured;

    public RoutedAiAnalysisAdapter(@Qualifier("ollamaAiAnalysisAdapter") AiAnalysisPort localFallback,
                                   ConfiguredAiClient configured) {
        this.localFallback = localFallback;
        this.configured = configured;
    }

    @Override
    public String analyze(String context) {
        return localFallback.analyze(context);
    }

    @Override
    public String analyzeSection(UUID tenantId, String sectionName, String instruction, String context) {
        String system = """
                You are the KlueOps evidence-guided Kubernetes section analyst.
                Return only one compact JSON object without markdown fences.
                Use only the supplied sanitized context and do not claim that you executed a command.
                Requested section: %s
                Section rules: %s
                """.formatted(sectionName, instruction);
        return configured.complete(tenantId, "ANALYSIS", system, context)
                .map(ConfiguredAiClient.Completion::content)
                .orElseGet(() -> localFallback.analyzeSection(sectionName, instruction, context));
    }
}
