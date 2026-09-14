package io.strato.aiops.application.port.out;

import java.util.concurrent.CompletableFuture;

public interface KubernetesTerminalSession extends AutoCloseable {
    void input(String data);
    void resize(int columns, int rows);
    CompletableFuture<Integer> exitCode();
    @Override void close();
}
