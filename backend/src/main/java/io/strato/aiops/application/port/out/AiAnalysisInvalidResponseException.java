package io.strato.aiops.application.port.out;

public class AiAnalysisInvalidResponseException extends RuntimeException {

    public AiAnalysisInvalidResponseException(String message) {
        super(message);
    }

    public AiAnalysisInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
