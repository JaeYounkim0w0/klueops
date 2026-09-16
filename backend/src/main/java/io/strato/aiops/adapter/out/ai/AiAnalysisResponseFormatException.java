package io.strato.aiops.adapter.out.ai;

import io.strato.aiops.application.port.out.AiAnalysisInvalidResponseException;

public class AiAnalysisResponseFormatException extends AiAnalysisInvalidResponseException {

    /** AiAnalysisResponseFormatException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AiAnalysisResponseFormatException(String message) {
        super(message);
    }

    /** AiAnalysisResponseFormatException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AiAnalysisResponseFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
