package io.strato.aiops.application.port.in;

import java.util.UUID;

public interface CommandTerminalUseCase {
    /** CommandTerminalUseCase의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    TerminalSessionTicket create(StartCommandExecutionCommand command);
    /** CommandTerminalUseCase의 connect 처리 계약을 정의한다. */
    void connect(UUID sessionId, String actor, TerminalClient client);
    /** CommandTerminalUseCase의 input 처리 계약을 정의한다. */
    void input(UUID sessionId, String actor, String data);
    /** CommandTerminalUseCase의 resize 처리 계약을 정의한다. */
    void resize(UUID sessionId, String actor, int columns, int rows);
    /** CommandTerminalUseCase의 disconnect 처리 계약을 정의한다. */
    void disconnect(UUID sessionId, String actor);
}
