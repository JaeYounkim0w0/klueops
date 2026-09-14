package io.strato.aiops.application.port.out;

public interface KubernetesTerminalPort {
    KubernetesTerminalSession open(KubernetesTerminalRequest request, KubernetesTerminalListener listener);
}
