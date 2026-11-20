package ru.nechto.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * HandshakeHandler для извлечения идентификатора пользователя (telegramId) при установлении WebSocket-соединения.
 * Поддерживает извлечение из query параметров (?telegramId=... или ?userId=...) и HTTP-заголовка X-Telegram-Id.
 */
public class StompPrincipalHandshakeHandler extends DefaultHandshakeHandler {
    private static final Logger log = LoggerFactory.getLogger(StompPrincipalHandshakeHandler.class);

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 1. Попытка извлечь telegramId из query parameters
        URI uri = request.getURI();
        String query = uri.getQuery();
        if (query != null && !query.isBlank()) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    String key = pair[0].trim();
                    String value = pair[1].trim();
                    if (("telegramId".equalsIgnoreCase(key) || "userId".equalsIgnoreCase(key) || "login".equalsIgnoreCase(key))
                            && !value.isBlank()) {
                        log.debug("Determined STOMP Principal from query param {}: {}", key, value);
                        return new StompPrincipal(value);
                    }
                }
            }
        }

        // 2. Попытка извлечь telegramId из HTTP-заголовков
        List<String> headers = request.getHeaders().get("X-Telegram-Id");
        if (headers != null && !headers.isEmpty() && !headers.get(0).isBlank()) {
            String uid = headers.get(0).trim();
            log.debug("Determined STOMP Principal from header X-Telegram-Id: {}", uid);
            return new StompPrincipal(uid);
        }

        // 3. Fallback к стандартному поведению Spring
        return super.determineUser(request, wsHandler, attributes);
    }

    public record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
