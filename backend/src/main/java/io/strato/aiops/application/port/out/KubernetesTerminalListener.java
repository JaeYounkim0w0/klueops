package io.strato.aiops.application.port.out;

public interface KubernetesTerminalListener {
    void onOutput(String channel, String text);
}
