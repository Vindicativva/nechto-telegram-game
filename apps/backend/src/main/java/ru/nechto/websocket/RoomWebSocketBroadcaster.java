package ru.nechto.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomWebSocketBroadcaster {
    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketBroadcaster.class);

    private final ObjectMapper objectMapper;
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, SessionMeta> sessionMetaMap = new ConcurrentHashMap<>();

    public record SessionMeta(String sessionId, String roomCode, String telegramId) {}

    public RoomWebSocketBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

    public void broadcastToRoomExcept(String roomCode, Object payload, WebSocketSession excludedSession) {
        String normalized = normalizeCode(roomCode);
        Set<WebSocketSession> sessions = roomSessions.get(normalized);
        if (sessions == null || sessions.isEmpty()) return;

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
}
