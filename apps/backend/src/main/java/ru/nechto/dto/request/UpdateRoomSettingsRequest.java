package ru.nechto.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Запрос на обновление настроек комнаты (доступно только создателю)")
public class UpdateRoomSettingsRequest {

    @Schema(description = "Новый лимит количества участников (от 4 до 12)", example = "8", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Количество участников обязательно")
    @Min(value = 4, message = "Минимальное количество участников: 4")
    @Max(value = 12, message = "Максимальное количество участников: 12")
    private Integer maxPlayers;

    @Schema(description = "ID создателя Telegram (опционально, если передан в заголовке X-Telegram-Id)", example = "123456789")
    private String telegramId;

    public UpdateRoomSettingsRequest() {}

    public UpdateRoomSettingsRequest(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public UpdateRoomSettingsRequest(Integer maxPlayers, String telegramId) {
        this.maxPlayers = maxPlayers;
        this.telegramId = telegramId;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(String telegramId) {
        this.telegramId = telegramId;
    }
}
