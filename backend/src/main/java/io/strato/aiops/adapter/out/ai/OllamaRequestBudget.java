package io.strato.aiops.adapter.out.ai;

/** Ollama 요청의 입력·출력 토큰 예산과 실행 컨텍스트 크기를 계산한다. */
final class OllamaRequestBudget {
    private static final int MINIMUM_CONTEXT_TOKENS = 4_096;
    private static final int SAFETY_TOKENS = 768;

    private OllamaRequestBudget() { }

    /** 한글·YAML 혼합 입력을 보수적으로 추정하고 2배 단위 컨텍스트를 선택한다. */
    static Budget calculate(String system, String user, int requestedOutputTokens, int maximumContextTokens) {
        int estimatedInputTokens = estimateTokens(system) + estimateTokens(user);
        int safeMaximumContext = Math.max(MINIMUM_CONTEXT_TOKENS, maximumContextTokens);
        if (estimatedInputTokens + 256 + SAFETY_TOKENS > safeMaximumContext)
            throw new IllegalArgumentException("Ollama input exceeds the configured context budget; reduce reference pages or increase the context limit");
        int context = MINIMUM_CONTEXT_TOKENS;
        int desired = estimatedInputTokens + Math.max(256, requestedOutputTokens) + SAFETY_TOKENS;
        while (context < desired && context < safeMaximumContext) context = Math.min(context * 2, safeMaximumContext);
        int availableOutput = Math.max(256, context - estimatedInputTokens - SAFETY_TOKENS);
        int output = Math.max(256, Math.min(requestedOutputTokens, availableOutput));
        return new Budget(context, output, estimatedInputTokens);
    }

    /** 코드 포인트 수를 사용해 영문·한글·YAML이 섞인 입력 토큰 수를 과소평가하지 않는다. */
    private static int estimateTokens(String value) {
        if (value == null || value.isBlank()) return 0;
        long codePoints = value.codePoints().count();
        return Math.toIntExact(Math.max(1, (codePoints + 1) / 2));
    }

    record Budget(int contextTokens, int outputTokens, int estimatedInputTokens) { }
}
