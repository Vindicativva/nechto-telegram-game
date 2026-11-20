package ru.nechto.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.dto.ws.RoomStateEvent;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Броадкастер событий игровой комнаты.
 * Обеспечивает рассылку сообщений как по "сырым" WebSocket-сессиям, так и через STOMP-топики /topic/room/{code}.
 */
@Component
public class RoomWebSocketBroadcaster {
    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketBroadcaster.class);

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, SessionMeta> sessionMetaMap = new ConcurrentHashMap<>();

    public record SessionMeta(String sessionId, String roomCode, String telegramId) {}

    @org.springframework.beans.factory.annotation.Autowired
    public RoomWebSocketBroadcaster(ObjectMapper objectMapper,
                                    @Lazy SimpMessagingTemplate messagingTemplate) {
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    public RoomWebSocketBroadcaster(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    private String normalizeCode(String code) {
        if (code == null) return "";
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public void registerSession(String roomCode, String telegramId, WebSocketSession session) {
        String normalized = normalizeCode(roomCode);
        roomSessions.computeIfAbsent(normalized, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionMetaMap.put(session.getId(), new SessionMeta(session.getId(), normalized, telegramId));
        log.info("WebSocket session registered: sessionId={}, roomCode={}, telegramId={}",
                session.getId(), normalized, telegramId);
    }

    public SessionMeta unregisterSession(WebSocketSession session) {
        SessionMeta meta = sessionMetaMap.remove(session.getId());
        if (meta != null) {
            Set<WebSocketSession> sessions = roomSessions.get(meta.roomCode());
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    roomSessions.remove(meta.roomCode());
                }
            }
            log.info("WebSocket session unregistered: sessionId={}, roomCode={}, telegramId={}",
                    session.getId(), meta.roomCode(), meta.telegramId());
        }
        return meta;
    }

    public boolean hasOtherActiveSessions(String roomCode, String telegramId, String currentSessionId) {
        if (roomCode == null || telegramId == null) return false;
        String normalized = normalizeCode(roomCode);
        Set<WebSocketSession> sessions = roomSessions.get(normalized);
        if (sessions == null) return false;

        return sessions.stream().anyMatch(s -> {
            if (s.getId().equals(currentSessionId) || !s.isOpen()) return false;
            SessionMeta meta = sessionMetaMap.get(s.getId());
            return meta != null && telegramId.equals(meta.telegramId());
        });
    }

    public void sendToSession(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.error("Failed to send message to session {}", session.getId(), e);
        }
    }

    public void broadcastToRoom(String roomCode, Object payload) {
        broadcastToRoomExcept(roomCode, payload, null);
    }

    public void broadcastRoomState(String roomCode, RoomResponse roomResponse) {
        broadcastToRoom(roomCode, RoomStateEvent.of(roomResponse));
    }

    /**
     * Транслирует публичное состояние игры GAME_STATE в топик /topic/room/{code} и сессии комнаты.
     *
     * @param roomCode код комнаты
     * @param payload  публичное событие GameStateEvent
     */
    public void broadcastGameState(String roomCode, Object payload) {
        broadcastToRoom(roomCode, payload);
    }

    /**
     * Отправляет персональное скрытое состояние PLAYER_PRIVATE_STATE игроку в его очередь /user/queue/game.
     *
     * @param telegramId ID пользователя в Telegram
     * @param payload    персональное событие PlayerPrivateStateEvent
     */
    public void sendPrivateState(String telegramId, Object payload) {
        if (telegramId == null || payload == null) return;
        try {
            String json = objectMapper.writeValueAsString(payload);

            // 1. STOMP /user/queue/game
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSendToUser(telegramId, "/queue/game", json);
            }

            // 2. Отправка в прямые WebSocket-сессии пользователя
            for (SessionMeta meta : sessionMetaMap.values()) {
                if (telegramId.equals(meta.telegramId())) {
                    for (Set<WebSocketSession> sessions : roomSessions.values()) {
                        for (WebSocketSession session : sessions) {
                            if (session.getId().equals(meta.sessionId()) && session.isOpen()) {
                                synchronized (session) {
                                    if (session.isOpen()) {
                                        session.sendMessage(new TextMessage(json));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to send private state to user {}", telegramId, e);
        }
    }

    public void broadcastToRoomExcept(String roomCode, Object payload, WebSocketSession excludedSession) {
        String normalized = normalizeCode(roomCode);

        // 1. Broadcast to raw WebSocket sessions
        Set<WebSocketSession> sessions = roomSessions.get(normalized);
        if (sessions != null && !sessions.isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                for (WebSocketSession session : sessions) {
                    if (session.isOpen() && (excludedSession == null || !session.getId().equals(excludedSession.getId()))) {
                        try {
                            synchronized (session) {
                                if (session.isOpen()) {
                                    session.sendMessage(new TextMessage(json));
                                }
                            }
                        } catch (IOException e) {
                            log.error("Failed to broadcast to session {}", session.getId(), e);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to serialize WebSocket message for broadcast to room {}", normalized, e);
            }
        }

        // 2. Broadcast to STOMP message broker topic: /topic/room/{code}
        if (messagingTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                messagingTemplate.convertAndSend("/topic/room/" + normalized, json);
            } catch (Exception e) {
                log.error("Failed to broadcast STOMP message to /topic/room/{}", normalized, e);
            }
        }
    }
}
