package ru.nechto.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import ru.nechto.websocket.LobbyWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final LobbyWebSocketHandler lobbyWebSocketHandler;

    public WebSocketConfig(LobbyWebSocketHandler lobbyWebSocketHandler) {
        this.lobbyWebSocketHandler = lobbyWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(lobbyWebSocketHandler, "/ws", "/ws/rooms/{code}")
                .setAllowedOrigins("*");
    }
}
