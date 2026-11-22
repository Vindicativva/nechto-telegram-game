package ru.nechto.dto.ws;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Сообщение явного выхода из комнаты через WebSocket/STOMP.
 */
@Schema(description = "Сообщение явного выхода из комнаты по WebSocket/STOMP")
public class LeaveRoomMessage {

    @Schema(description = "ID пользователя Telegram", example = "123456789")
    private String telegramId;

    public LeaveRoomMessage() {
    }

    public LeaveRoomMessage(String telegramId) {
        this.telegramId = telegramId;
    }

    public String getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(String telegramId) {
        this.telegramId = telegramId;
    }
}
