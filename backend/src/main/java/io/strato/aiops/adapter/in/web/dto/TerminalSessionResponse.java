package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.TerminalSessionTicket;

import java.time.Instant;
import java.util.UUID;

public record TerminalSessionResponse(UUID sessionId, UUID executionId, String websocketPath, Instant expiresAt) {
    /** TerminalSessionResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static TerminalSessionResponse from(TerminalSessionTicket ticket) {
        return new TerminalSessionResponse(ticket.sessionId(), ticket.executionId(), ticket.websocketPath(), ticket.expiresAt());
    }
}
