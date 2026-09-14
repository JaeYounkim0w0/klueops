package io.strato.aiops.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record TerminalSessionTicket(UUID sessionId, UUID executionId, String websocketPath, Instant expiresAt) {
}
