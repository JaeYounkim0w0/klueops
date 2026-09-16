package io.strato.aiops.application.port.out;

public class AiAnalysisInvalidResponseException extends RuntimeException {

    /** AiAnalysisInvalidResponseException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AiAnalysisInvalidResponseException(String message) {
        super(message);
    }

    /** AiAnalysisInvalidResponseException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AiAnalysisInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
