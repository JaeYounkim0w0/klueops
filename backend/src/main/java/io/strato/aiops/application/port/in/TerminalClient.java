package io.strato.aiops.application.port.in;

public interface TerminalClient {
    void output(String channel, String text);
    void status(String status, Integer exitCode, String message);
    void close();
}
