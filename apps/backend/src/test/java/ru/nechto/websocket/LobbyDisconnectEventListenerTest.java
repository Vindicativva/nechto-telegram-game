package ru.nechto.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.service.RoomService;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Модульные тесты обработчика событий SessionDisconnectEvent.
 */
@ExtendWith(MockitoExtension.class)
class LobbyDisconnectEventListenerTest {

    @Mock
    private RoomService roomService;

    /**
     * Проверяет, что при обрыве STOMP-сессии с привязанными атрибутами комнаты и пользователя,
     * сервис обновляет статус игрока на {@code ConnectionStatus.DISCONNECTED}.
     *
     * <p><b>Предусловия (Given):</b> В атрибутах сессии сохранены {@code roomCode = "DISC01"}
     * и {@code telegramId = "1001"}.</p>
     *
     * <p><b>Действие (When):</b> Срабатывает событие {@link SessionDisconnectEvent}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Вызывается метод {@code roomService.updatePlayerConnectionStatus("DISC01", "1001", DISCONNECTED)}.
     * </p>
     */
    @Test
    @DisplayName("Обработка SessionDisconnectEvent: обновление статуса игрока на DISCONNECTED")
    void handleSessionDisconnect_WithAttributes_UpdatesPlayerStatus() {
        LobbyDisconnectEventListener listener = new LobbyDisconnectEventListener(roomService);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("sess-123");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("roomCode", "DISC01");
        attrs.put("telegramId", "1001");
        accessor.setSessionAttributes(attrs);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "sess-123", CloseStatus.NORMAL);

        listener.handleSessionDisconnect(event);

        verify(roomService).updatePlayerConnectionStatus("DISC01", "1001", ConnectionStatus.DISCONNECTED);
    }

    /**
     * Проверяет игнорирование события {@link SessionDisconnectEvent}, если атрибуты сессии отсутствуют.
     *
     * <p><b>Предусловия (Given):</b> Сессия без атрибутов комнаты и Telegram ID.</p>
     *
     * <p><b>Действие (When):</b> Срабатывает событие {@link SessionDisconnectEvent}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Метод {@code roomService.updatePlayerConnectionStatus} не вызывается.
     * </p>
     */
    @Test
    @DisplayName("Игнорирование SessionDisconnectEvent при отсутствии атрибутов сессии")
    void handleSessionDisconnect_WithoutAttributes_DoesNothing() {
        LobbyDisconnectEventListener listener = new LobbyDisconnectEventListener(roomService);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("sess-anonymous");

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "sess-anonymous", CloseStatus.NORMAL);

        listener.handleSessionDisconnect(event);

        verifyNoInteractions(roomService);
    }
}
