package io.strato.aiops.adapter.in.web;

public final class RequestAttributes {

    public static final String REQUEST_ID = "requestId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    /** RequestAttributes 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private RequestAttributes() {
    }
}

