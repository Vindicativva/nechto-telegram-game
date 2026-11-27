package ru.nechto.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;

/**
 * Перехватчик входящих STOMP-сообщений.
 * Извлекает telegramId из заголовков фрейма CONNECT (telegramId, login, userId, user)
 * и устанавливает Principal пользователя в сессию STOMP для маршрутизации личных очередей /user/queue/*.
 */
public class StompUserChannelInterceptor implements ChannelInterceptor {
    private static final Logger log = LoggerFactory.getLogger(StompUserChannelInterceptor.class);

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String telegramId = accessor.getFirstNativeHeader("telegramId");
            if (telegramId == null || telegramId.isBlank()) {
                telegramId = accessor.getFirstNativeHeader("login");
            }
            if (telegramId == null || telegramId.isBlank()) {
                telegramId = accessor.getFirstNativeHeader("userId");
            }
            if (telegramId == null || telegramId.isBlank()) {
                telegramId = accessor.getFirstNativeHeader("user");
            }

            if (telegramId != null && !telegramId.isBlank()) {
                final String uid = telegramId.trim();
                accessor.setUser(new Principal() {
                    @Override
                    public String getName() {
                        return uid;
                    }

                    @Override
                    public String toString() {
                        return "StompPrincipal[" + uid + "]";
                    }
                });
                log.info("STOMP Principal bound for session {}: {}", accessor.getSessionId(), uid);
            }
        }
        return message;
    }
}
