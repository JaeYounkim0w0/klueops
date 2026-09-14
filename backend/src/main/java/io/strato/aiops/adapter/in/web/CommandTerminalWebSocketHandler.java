package io.strato.aiops.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.in.CommandTerminalUseCase;
import io.strato.aiops.application.port.in.TerminalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Component
public class CommandTerminalWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(CommandTerminalWebSocketHandler.class);
    private final CommandTerminalUseCase terminals;
    private final ObjectMapper objectMapper;

    public CommandTerminalWebSocketHandler(CommandTerminalUseCase terminals, ObjectMapper objectMapper) {
        this.terminals = terminals;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            terminals.connect(sessionId(session), actor(session), new WebSocketTerminalClient(session, objectMapper));
        } catch (RuntimeException exception) {
            log.warn("Terminal WebSocket session {} was rejected: {}", safeSessionId(session), concise(exception));
            send(session, "error", Map.of("message", concise(exception)));
            session.close(CloseStatus.POLICY_VIOLATION.withReason("terminal session rejected"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            String type = payload.path("type").asText();
            if ("input".equals(type)) terminals.input(sessionId(session), actor(session), payload.path("data").asText(""));
            else if ("resize".equals(type)) terminals.resize(sessionId(session), actor(session),
                    payload.path("columns").asInt(80), payload.path("rows").asInt(24));
            else if (!"ping".equals(type)) send(session, "error", Map.of("message", "unsupported terminal message type"));
        } catch (RuntimeException exception) {
            send(session, "error", Map.of("message", concise(exception)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        terminals.disconnect(sessionId(session), actor(session));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        terminals.disconnect(sessionId(session), actor(session));
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    private UUID sessionId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) throw new IllegalArgumentException("terminal session path is missing");
        String path = uri.getPath();
        return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    }

    private String safeSessionId(WebSocketSession session) {
        try { return sessionId(session).toString(); }
        catch (RuntimeException ignored) { return "unknown"; }
    }

    private String actor(WebSocketSession session) {
        return session.getPrincipal() == null ? "local-operator" : session.getPrincipal().getName();
    }

    private void send(WebSocketSession session, String type, Object data) throws IOException {
        if (!session.isOpen()) return;
        synchronized (session) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", type, "data", data))));
        }
    }

    private String concise(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static final class WebSocketTerminalClient implements TerminalClient {
        private final WebSocketSession session;
        private final ObjectMapper objectMapper;

        private WebSocketTerminalClient(WebSocketSession session, ObjectMapper objectMapper) {
            this.session = session;
            this.objectMapper = objectMapper;
        }

        @Override public void output(String channel, String text) { send("output", Map.of("channel", channel, "text", text)); }
        @Override public void status(String status, Integer exitCode, String message) {
            java.util.HashMap<String, Object> data = new java.util.HashMap<>();
            data.put("status", status);
            if (exitCode != null) data.put("exitCode", exitCode);
            if (message != null) data.put("message", message);
            send("status", data);
        }
        @Override public void close() {
            try { if (session.isOpen()) session.close(CloseStatus.NORMAL); } catch (IOException ignored) { }
        }
        private void send(String type, Object data) {
            if (!session.isOpen()) return;
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", type, "data", data))));
                }
            } catch (IOException ignored) { close(); }
        }
    }
}
