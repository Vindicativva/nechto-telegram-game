package ru.nechto.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.service.RoomService;

import java.util.Map;

/**
 * Обработчик события обрыва STOMP-сессии SessionDisconnectEvent.
 * Согласно спецификации ТЗ 4.2.1, при сетевом сбое хоста его права создателя
 * удерживаются в течение 5 минут до передачи следующему игроку.
 */
@Component
public class LobbyDisconnectEventListener {
    private static final Logger log = LoggerFactory.getLogger(LobbyDisconnectEventListener.class);

    private final RoomService roomService;

    public LobbyDisconnectEventListener(RoomService roomService) {
        this.roomService = roomService;
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

        if (sessionAttributes != null) {
            String roomCode = (String) sessionAttributes.get("roomCode");
            String telegramId = (String) sessionAttributes.get("telegramId");

            if (roomCode != null && telegramId != null) {
                log.info("STOMP session disconnected: sessionId={}, roomCode={}, telegramId={}",
                        accessor.getSessionId(), roomCode, telegramId);
                // Отмечаем статус DISCONNECTED и фиксируем время disconnectedAt.
                // Если отключился создатель, права удерживаются в течение 5 минут.
                roomService.updatePlayerConnectionStatus(roomCode, telegramId, ConnectionStatus.DISCONNECTED);
            }
        }
    }
}
