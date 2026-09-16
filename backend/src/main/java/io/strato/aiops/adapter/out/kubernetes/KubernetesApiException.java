package io.strato.aiops.adapter.out.kubernetes;

public class KubernetesApiException extends RuntimeException {

    /** KubernetesApiException 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public KubernetesApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
