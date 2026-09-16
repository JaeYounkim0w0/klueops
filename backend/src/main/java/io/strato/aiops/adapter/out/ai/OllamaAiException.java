package io.strato.aiops.adapter.out.ai;

public class OllamaAiException extends RuntimeException {

    /** OllamaAiException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OllamaAiException(String message) {
        super(message);
    }

    /** OllamaAiException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OllamaAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
