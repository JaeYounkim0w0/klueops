package io.strato.aiops.application.port.out;

@FunctionalInterface
public interface KubectlOutputListener {
    void onOutput(String channel, String text);
}

