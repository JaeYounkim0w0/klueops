package io.strato.aiops.adapter.out.ai;

public class OllamaAiException extends RuntimeException {

    public OllamaAiException(String message) {
        super(message);
    }

    public OllamaAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
