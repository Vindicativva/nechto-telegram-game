package ru.nechto.dto.ws;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Сообщение подключения к комнате через WebSocket/STOMP (@MessageMapping).
 */
@Schema(description = "Сообщение подключения к комнате по WebSocket/STOMP")
public class JoinRoomMessage {

    @Schema(description = "ID пользователя Telegram", example = "123456789")
    private String telegramId;

    @Schema(description = "Username пользователя Telegram (при наличии)", example = "ivan_ivanov")
    private String username;

    @Schema(description = "Отображаемое имя игрока", example = "Иван")
    private String displayName;

    public JoinRoomMessage() {
    }

    public JoinRoomMessage(String telegramId, String username, String displayName) {
        this.telegramId = telegramId;
        this.username = username;
        this.displayName = displayName;
    }

    public String getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(String telegramId) {
        this.telegramId = telegramId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
