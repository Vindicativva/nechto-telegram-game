package ru.nechto.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Информация об участнике игровой комнаты")
public class RoomParticipant {

    @Schema(description = "Уникальный ID пользователя в Telegram", example = "123456789")
    private String telegramId;

    @Schema(description = "Username пользователя в Telegram (при наличии)", example = "ivan_ivanov")
    private String username;

    @Schema(description = "Отображаемое имя игрока в партии", example = "Иван")
    private String displayName;

    @JsonProperty("isCreator")
    @Schema(description = "Является ли данный игрок создателем (хостом) комнаты", example = "true")
    private boolean isCreator;

    @Schema(description = "Статус подключения к серверу (ONLINE, DISCONNECTED)", example = "ONLINE")
    private ConnectionStatus connectionStatus;

    @Schema(description = "Время подключения к комнате (определяет очередность передачи прав хоста)", example = "2026-10-29T12:00:00.000Z")
    private Instant joinedAt;

    @Schema(description = "Время обрыва соединения (если отключен)", example = "2026-10-29T12:05:00.000Z")
    private Instant disconnectedAt;

    public RoomParticipant() {
    }

    public RoomParticipant(String telegramId, String username, String displayName, boolean isCreator,
                           ConnectionStatus connectionStatus, Instant joinedAt, Instant disconnectedAt) {
        this.telegramId = telegramId;
        this.username = username;
        this.displayName = displayName;
        this.isCreator = isCreator;
        this.connectionStatus = connectionStatus;
        this.joinedAt = joinedAt;
        this.disconnectedAt = disconnectedAt;
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

    public boolean isCreator() {
        return isCreator;
    }

    public void setCreator(boolean creator) {
        isCreator = creator;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(ConnectionStatus connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Instant getDisconnectedAt() {
        return disconnectedAt;
    }

    public void setDisconnectedAt(Instant disconnectedAt) {
        this.disconnectedAt = disconnectedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoomParticipant that = (RoomParticipant) o;
        return Objects.equals(telegramId, that.telegramId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(telegramId);
    }
}
