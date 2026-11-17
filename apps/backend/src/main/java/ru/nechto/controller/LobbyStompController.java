package ru.nechto.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import tools.jackson.databind.ObjectMapper;
import ru.nechto.dto.response.ErrorResponse;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.dto.ws.JoinRoomMessage;
import ru.nechto.dto.ws.LeaveRoomMessage;
import ru.nechto.dto.ws.StartGameMessage;
import ru.nechto.exception.LobbyException;
import ru.nechto.exception.UnauthorizedException;
import ru.nechto.model.TelegramUser;
import ru.nechto.service.RoomService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Контроллер обработки WebSocket/STOMP сообщений для лобби игровой комнаты.
 */
@Controller
public class LobbyStompController {
    private static final Logger log = LoggerFactory.getLogger(LobbyStompController.class);

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public LobbyStompController(RoomService roomService,
                                SimpMessagingTemplate messagingTemplate,
                                ObjectMapper objectMapper) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Обработка подключения к комнате через STOMP: @MessageMapping("/room/{code}/join").
     * Проверяет заполненность комнаты, статус LOBBY и отсутствие дубликатов участников.
     */
    @MessageMapping("/room/{code}/join")
    public void joinRoom(@DestinationVariable String code,
                         @Payload Object rawPayload,
                         SimpMessageHeaderAccessor headerAccessor) {
        JoinRoomMessage message = parsePayload(rawPayload, JoinRoomMessage.class);
        if (message == null || message.getTelegramId() == null || message.getTelegramId().isBlank()) {
            throw new UnauthorizedException("Отсутствуют данные пользователя Telegram");
        }

        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        TelegramUser user = TelegramUser.of(
                message.getTelegramId(),
                message.getUsername(),
                message.getDisplayName()
        );

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            sessionAttributes.put("roomCode", normalizedCode);
            sessionAttributes.put("telegramId", message.getTelegramId());
        }

        RoomResponse response = roomService.joinRoom(normalizedCode, user);
        log.info("STOMP Player joined room {}: telegramId={}", normalizedCode, message.getTelegramId());
    }

    /**
     * Обработка явного выхода игрока из комнаты через STOMP: @MessageMapping("/room/{code}/leave").
     * При выходе хоста права передаются участнику с самым ранним joinedAt.
     */
    @MessageMapping("/room/{code}/leave")
    public void leaveRoom(@DestinationVariable String code,
                          @Payload(required = false) Object rawPayload,
                          SimpMessageHeaderAccessor headerAccessor) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        if (headerAccessor.getSessionAttributes() != null) {
            headerAccessor.getSessionAttributes().put("roomCode", normalizedCode);
        }

        LeaveRoomMessage message = parsePayload(rawPayload, LeaveRoomMessage.class);
        String telegramId = message != null && message.getTelegramId() != null ?
                message.getTelegramId().trim() : null;

        if (telegramId == null && headerAccessor.getSessionAttributes() != null) {
            telegramId = (String) headerAccessor.getSessionAttributes().get("telegramId");
        }

        if (telegramId == null || telegramId.isBlank()) {
            throw new UnauthorizedException("Отсутствуют данные пользователя Telegram");
        }

        roomService.leaveRoom(normalizedCode, telegramId);

        if (headerAccessor.getSessionAttributes() != null) {
            headerAccessor.getSessionAttributes().remove("roomCode");
            headerAccessor.getSessionAttributes().remove("telegramId");
        }
        log.info("STOMP Player left room {}: telegramId={}", normalizedCode, telegramId);
    }

    /**
     * Команда запуска игры создателем комнаты: @MessageMapping("/room/{code}/start").
     * Выполняет валидацию числа участников (от 4 до 12 человек).
     */
    @MessageMapping("/room/{code}/start")
    public void startGame(@DestinationVariable String code,
                          @Payload(required = false) Object rawPayload,
                          SimpMessageHeaderAccessor headerAccessor) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        if (headerAccessor.getSessionAttributes() != null) {
            headerAccessor.getSessionAttributes().put("roomCode", normalizedCode);
        }

        StartGameMessage message = parsePayload(rawPayload, StartGameMessage.class);
        String telegramId = message != null && message.getTelegramId() != null ?
                message.getTelegramId().trim() : null;

        if (telegramId == null && headerAccessor.getSessionAttributes() != null) {
            telegramId = (String) headerAccessor.getSessionAttributes().get("telegramId");
        }

        if (telegramId == null || telegramId.isBlank()) {
            throw new UnauthorizedException("Отсутствуют данные пользователя Telegram");
        }

        roomService.startGame(normalizedCode, telegramId);
        log.info("STOMP Game started in room {}: by telegramId={}", normalizedCode, telegramId);
    }

    /**
     * Обработка ошибок в STOMP контроллере.
     */
    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorResponse handleLobbyException(LobbyException ex, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.warn("STOMP Exception [{}] for session {}: {}", ex.getCode(), sessionId, ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getStatus().value(),
                "/app/room",
                ex.getCode(),
                ex.getMessage(),
                List.of()
        );

        String roomCode = (headerAccessor.getSessionAttributes() != null) ?
                (String) headerAccessor.getSessionAttributes().get("roomCode") : null;

        if (roomCode == null) {
            String dest = headerAccessor.getDestination();
            if (dest != null && dest.contains("/room/")) {
                String[] parts = dest.split("/room/");
                if (parts.length > 1) {
                    roomCode = parts[1].split("/")[0].toUpperCase(Locale.ROOT);
                }
            }
        }

        if (roomCode != null) {
            try {
                String errorJson = objectMapper.writeValueAsString(errorResponse);
                messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/errors", errorJson);
            } catch (Exception e) {
                log.error("Failed to serialize error response for STOMP broadcast", e);
            }
        }
        return errorResponse;
    }

    private <T> T parsePayload(Object rawPayload, Class<T> targetClass) {
        if (rawPayload == null) return null;
        if (targetClass.isInstance(rawPayload)) {
            return targetClass.cast(rawPayload);
        }
        try {
            if (rawPayload instanceof byte[] bytes) {
                return objectMapper.readValue(new String(bytes, StandardCharsets.UTF_8), targetClass);
            }
            return objectMapper.readValue(rawPayload.toString(), targetClass);
        } catch (Exception e) {
            log.warn("Failed to parse {} from raw payload: {}", targetClass.getSimpleName(), rawPayload);
            return null;
        }
    }
}
