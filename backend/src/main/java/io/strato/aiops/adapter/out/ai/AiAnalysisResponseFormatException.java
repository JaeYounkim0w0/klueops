package io.strato.aiops.adapter.out.ai;

import io.strato.aiops.application.port.out.AiAnalysisInvalidResponseException;

public class AiAnalysisResponseFormatException extends AiAnalysisInvalidResponseException {

    public AiAnalysisResponseFormatException(String message) {
        super(message);
    }

    public AiAnalysisResponseFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
