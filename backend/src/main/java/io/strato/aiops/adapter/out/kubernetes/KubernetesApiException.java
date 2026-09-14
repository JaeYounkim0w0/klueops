package io.strato.aiops.adapter.out.kubernetes;

public class KubernetesApiException extends RuntimeException {

    public KubernetesApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
