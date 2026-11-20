package ru.nechto.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import ru.nechto.websocket.StompPrincipalHandshakeHandler;
import ru.nechto.websocket.StompUserChannelInterceptor;

/**
 * Конфигурация Spring WebSocket / STOMP брокера сообщений.
 * Регистрирует эндпоинт /ws-game, привязывает аутентификацию Principal и настраивает префиксы топиков.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketBrokerConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        StompPrincipalHandshakeHandler handshakeHandler = new StompPrincipalHandshakeHandler();

        registry.addEndpoint("/ws-game")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler);

        registry.addEndpoint("/ws-game")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompUserChannelInterceptor());
    }
}
