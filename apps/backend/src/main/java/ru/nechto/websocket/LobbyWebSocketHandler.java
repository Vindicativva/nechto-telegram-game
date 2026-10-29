package ru.nechto.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.dto.ws.WsEvent;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.model.Room;
import ru.nechto.model.RoomParticipant;
import ru.nechto.model.TelegramUser;
import ru.nechto.service.RoomService;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class LobbyWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(LobbyWebSocketHandler.class);

    private final RoomService roomService;
    private final RoomWebSocketBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public LobbyWebSocketHandler(RoomService roomService,
                                 RoomWebSocketBroadcaster broadcaster,
                                 ObjectMapper objectMapper) {
        this.roomService = roomService;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: sessionId={}", session.getId());
        URI uri = session.getUri();
        if (uri == null) return;

        Map<String, String> queryParams = parseQueryParams(uri.getQuery());
        String code = extractRoomCodeFromUri(uri);
        if (code == null) {
            code = queryParams.get("code");
        }

        String telegramId = queryParams.get("telegramId");
        String username = queryParams.get("username");
        String displayName = queryParams.get("displayName");

        if (code != null && !code.isBlank() && telegramId != null && !telegramId.isBlank()) {
            handleSubscription(session, code, telegramId, username, displayName);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            JsonNode root = objectMapper.readTree(payload);

            if (root.has("event")) {
                String event = root.get("event").asString();
                if ("lobby:subscribe".equals(event)) {
                    JsonNode data = root.path("data");
                    String code = data.hasNonNull("code") ? data.get("code").asString() : null;
                    String telegramId = data.hasNonNull("telegramId") ? data.get("telegramId").asString() : null;
                    String username = data.hasNonNull("username") ? data.get("username").asString() : null;
                    String displayName = data.hasNonNull("displayName") ? data.get("displayName").asString() : null;

                    if (code != null && telegramId != null) {
                        handleSubscription(session, code, telegramId, username, displayName);
                    }
                } else if ("ping".equalsIgnoreCase(event)) {
                    broadcaster.sendToSession(session, Map.of("event", "pong"));
                }
            }
        } catch (Exception e) {
            log.error("Failed to process WebSocket message from session {}", session.getId(), e);
        }
    }

    private void handleSubscription(WebSocketSession session, String code, String telegramId,
                                    String username, String displayName) {
        try {
            String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
            Room room;
            try {
                room = roomService.findRawRoomByCode(normalizedCode);
            } catch (Exception e) {
                log.warn("Room {} not found for WebSocket subscription from {}", normalizedCode, telegramId);
                return;
            }

            broadcaster.registerSession(normalizedCode, telegramId, session);

            Optional<RoomParticipant> participantOpt = room.getPlayers().stream()
                    .filter(p -> telegramId.equals(p.getTelegramId()))
                    .findFirst();

            if (participantOpt.isPresent()) {
                roomService.updatePlayerConnectionStatus(normalizedCode, telegramId, ConnectionStatus.ONLINE);
            } else {
                // Try to auto-join if room is in LOBBY and not full
                try {
                    TelegramUser user = TelegramUser.of(telegramId, username, displayName);
                    roomService.joinRoom(normalizedCode, user);
                } catch (Exception e) {
                    log.debug("Auto-join failed for user {} in room {}: {}", telegramId, normalizedCode, e.getMessage());
                }
            }

            RoomResponse response = roomService.getRoomByCode(normalizedCode);
            broadcaster.sendToSession(session, WsEvent.of("room:state_sync", response));
        } catch (Exception e) {
            log.error("Error during WebSocket subscription for session {}", session.getId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: sessionId={}, status={}", session.getId(), status);
        RoomWebSocketBroadcaster.SessionMeta meta = broadcaster.unregisterSession(session);
        if (meta != null && meta.roomCode() != null && meta.telegramId() != null) {
            if (!broadcaster.hasOtherActiveSessions(meta.roomCode(), meta.telegramId(), session.getId())) {
                roomService.updatePlayerConnectionStatus(meta.roomCode(), meta.telegramId(), ConnectionStatus.DISCONNECTED);
            }
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = (idx < pair.length() - 1) ?
                        URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) : "";
                map.put(key, val);
            }
        }
        return map;
    }

    private String extractRoomCodeFromUri(URI uri) {
        if (uri == null) return null;
        String path = uri.getPath();
        if (path != null && path.contains("/ws/rooms/")) {
            String[] parts = path.split("/ws/rooms/");
            if (parts.length > 1) {
                String candidate = parts[1];
                int slashIdx = candidate.indexOf("/");
                if (slashIdx >= 0) {
                    candidate = candidate.substring(0, slashIdx);
                }
                if (!candidate.isBlank()) {
                    return candidate.trim().toUpperCase(Locale.ROOT);
                }
            }
        }
        return null;
    }
}
