package ru.nechto.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Запрос на подключение игрока к комнате")
public class JoinRoomRequest {

    @Schema(description = "ID пользователя Telegram (опционально, если передан в заголовке X-Telegram-Id)", example = "987654321")
    private String telegramId;

    @Schema(description = "Username пользователя Telegram (опционально)", example = "petr_petrov")
    private String username;

    @Schema(description = "Отображаемое имя игрока (опционально, если передано в заголовке)", example = "Петр")
    private String displayName;

    public JoinRoomRequest() {}

    public JoinRoomRequest(String telegramId, String username, String displayName) {
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
