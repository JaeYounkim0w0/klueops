package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface CommandTerminalUseCase {
    TerminalSessionTicket create(StartCommandExecutionCommand command);
    void connect(UUID sessionId, String actor, TerminalClient client);
    void input(UUID sessionId, String actor, String data);
    void resize(UUID sessionId, String actor, int columns, int rows);
    void disconnect(UUID sessionId, String actor);
}
