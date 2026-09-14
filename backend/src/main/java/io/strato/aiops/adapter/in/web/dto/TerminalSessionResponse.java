package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.TerminalSessionTicket;

import java.time.Instant;
import java.util.UUID;

public record TerminalSessionResponse(UUID sessionId, UUID executionId, String websocketPath, Instant expiresAt) {
    public static TerminalSessionResponse from(TerminalSessionTicket ticket) {
        return new TerminalSessionResponse(ticket.sessionId(), ticket.executionId(), ticket.websocketPath(), ticket.expiresAt());
    }
}
