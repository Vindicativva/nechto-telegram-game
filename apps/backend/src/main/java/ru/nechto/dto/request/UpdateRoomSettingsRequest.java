package ru.nechto.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateRoomSettingsRequest {
    @NotNull(message = "Количество участников обязательно")
    @Min(value = 4, message = "Минимальное количество участников: 4")
    @Max(value = 12, message = "Максимальное количество участников: 12")
    private Integer maxPlayers;

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
