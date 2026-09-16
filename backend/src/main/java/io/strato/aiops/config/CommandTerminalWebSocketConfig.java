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

    /** CommandTerminalWebSocketConfig 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public CommandTerminalWebSocketConfig(CommandTerminalWebSocketHandler handler) {
        this.handler = handler;
    }

    /** CommandTerminalWebSocketConfig의 registerWebSocketHandlers 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/command-sessions/*")
                .setAllowedOriginPatterns("*");
    }
}
