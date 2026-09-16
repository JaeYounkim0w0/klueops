package io.strato.aiops.application.port.in;

public interface TerminalClient {
    /** TerminalClient의 output 처리 계약을 정의한다. */
    void output(String channel, String text);
    /** TerminalClient의 status 처리 계약을 정의한다. */
    void status(String status, Integer exitCode, String message);
    /** TerminalClient의 close 처리 대상과 관련 상태를 안전하게 정리한다. */
    void close();
}
