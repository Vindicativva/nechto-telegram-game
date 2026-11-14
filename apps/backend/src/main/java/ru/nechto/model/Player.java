package ru.nechto.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Доменная модель игрока в игровой комнате.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Доменная модель игрока")
public class Player {

    @Schema(description = "Уникальный идентификатор игрока в системе", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;

    @Schema(description = "Идентификатор пользователя в Telegram", example = "123456789")
    private String telegramId;

    @Schema(description = "Имя пользователя (username или отображаемое имя)", example = "ivan_ivanov")
    private String username;

    @JsonProperty("isHost")
    @Schema(description = "Флаг, является ли данный игрок создателем (хостом) комнаты", example = "true")
    private boolean isHost;

    @Schema(description = "Временная метка подключения игрока к комнате", example = "2026-11-14T12:00:00.000Z")
    private Instant connectedAt;

    /**
     * Конструктор по умолчанию для сериализации/десериализации.
     */
    public Player() {
    }

    /**
     * Полный конструктор со всеми полями доменной модели.
     *
     * @param id          уникальный идентификатор игрока
     * @param telegramId  ID пользователя в Telegram
     * @param username    имя пользователя
     * @param isHost      является ли хостом
     * @param connectedAt время подключения
     */
    public Player(String id, String telegramId, String username, boolean isHost, Instant connectedAt) {
        this.id = id;
        this.telegramId = telegramId;
        this.username = username;
        this.isHost = isHost;
        this.connectedAt = connectedAt;
    }

    /**
     * Удобный конструктор с автоматической генерацией ID и времени подключения.
     *
     * @param telegramId ID пользователя в Telegram
     * @param username   имя пользователя
     * @param isHost     является ли хостом
     */
    public Player(String telegramId, String username, boolean isHost) {
        this(UUID.randomUUID().toString(), telegramId, username, isHost, Instant.now());
    }

    /**
     * Создает доменный объект {@link Player} на основе существующего {@link RoomParticipant}.
     *
     * @param participant участник комнаты
     * @return доменный объект игрока
     */
    public static Player from(RoomParticipant participant) {
        if (participant == null) {
            return null;
        }
        String chosenUsername = participant.getUsername();
        if (chosenUsername == null || chosenUsername.isBlank()) {
            chosenUsername = participant.getDisplayName();
        }
        return new Player(
                participant.getTelegramId(),
                participant.getTelegramId(),
                chosenUsername,
                participant.isCreator(),
                participant.getJoinedAt() != null ? participant.getJoinedAt() : Instant.now()
        );
    }

    /**
     * Преобразует доменный объект {@link Player} в модель {@link RoomParticipant}.
     *
     * @return участник комнаты
     */
    public RoomParticipant toParticipant() {
        return new RoomParticipant(
                this.telegramId,
                this.username,
                this.username != null ? this.username : "Player",
                this.isHost,
                ConnectionStatus.ONLINE,
                this.connectedAt != null ? this.connectedAt : Instant.now(),
                null
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public boolean isHost() {
        return isHost;
    }

    public void setHost(boolean host) {
        isHost = host;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(telegramId, player.telegramId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(telegramId);
    }

    @Override
    public String toString() {
        return "Player{" +
                "id='" + id + '\'' +
                ", telegramId='" + telegramId + '\'' +
                ", username='" + username + '\'' +
                ", isHost=" + isHost +
                ", connectedAt=" + connectedAt +
                '}';
    }
}
