package ru.nechto.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateRoomRequest {
    @NotBlank(message = "Название комнаты обязательно")
    @Size(min = 1, max = 32, message = "Название комнаты должно быть от 1 до 32 символов")
    private String name;

    @NotNull(message = "Количество участников обязательно")
    @Min(value = 4, message = "Минимальное количество участников: 4")
    @Max(value = 12, message = "Максимальное количество участников: 12")
    private Integer maxPlayers;

    private String telegramId;
    private String username;
    private String displayName;

    public CreateRoomRequest() {}

    public CreateRoomRequest(String name, Integer maxPlayers) {
        this.name = name;
        this.maxPlayers = maxPlayers;
    }

    public CreateRoomRequest(String name, Integer maxPlayers, String telegramId, String username, String displayName) {
        this.name = name;
        this.maxPlayers = maxPlayers;
        this.telegramId = telegramId;
        this.username = username;
        this.displayName = displayName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
