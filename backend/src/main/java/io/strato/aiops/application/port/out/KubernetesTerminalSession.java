package io.strato.aiops.application.port.out;

import java.util.concurrent.CompletableFuture;

public interface KubernetesTerminalSession extends AutoCloseable {
    /** KubernetesTerminalSession의 input 처리 계약을 정의한다. */
    void input(String data);
    /** KubernetesTerminalSession의 resize 처리 계약을 정의한다. */
    void resize(int columns, int rows);
    /** KubernetesTerminalSession의 exitCode 처리 계약을 정의한다. */
    CompletableFuture<Integer> exitCode();
    /** KubernetesTerminalSession의 close 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override void close();
}
