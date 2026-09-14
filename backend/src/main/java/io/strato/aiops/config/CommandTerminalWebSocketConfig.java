package io.strato.aiops.config;

import io.strato.aiops.adapter.in.web.CommandTerminalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class CommandTerminalWebSocketConfig implements WebSocketConfigurer {
    private final CommandTerminalWebSocketHandler handler;

    public CommandTerminalWebSocketConfig(CommandTerminalWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/command-sessions/*")
                .setAllowedOriginPatterns("*");
    }
}
