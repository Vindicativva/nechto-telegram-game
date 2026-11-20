package ru.nechto.dto.ws;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Сообщение запуска игры через WebSocket/STOMP.
 */
@Schema(description = "Сообщение команды запуска игры по WebSocket/STOMP")
public class StartGameMessage {

    @Schema(description = "ID пользователя Telegram (создателя комнаты)", example = "123456789")
    private String telegramId;

    public StartGameMessage() {
    }

    public StartGameMessage(String telegramId) {
        this.telegramId = telegramId;
    }

    public String getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(String telegramId) {
        this.telegramId = telegramId;
    }
}
