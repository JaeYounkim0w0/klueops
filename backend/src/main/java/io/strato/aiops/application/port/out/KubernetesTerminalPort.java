package io.strato.aiops.application.port.out;

public interface KubernetesTerminalPort {
    /** KubernetesTerminalPort의 open 처리 계약을 정의한다. */
    KubernetesTerminalSession open(KubernetesTerminalRequest request, KubernetesTerminalListener listener);
}
