package ru.nechto.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class Room {
    private UUID id;
    private String code;
    private String inviteLink;
    private String name;
    private String creatorId;
    private RoomStatus status;
    private int maxPlayers;
    private List<RoomParticipant> players = new CopyOnWriteArrayList<>();
    private Instant createdAt;
    private Instant lastActivityAt;

    public Room() {
    }

    /**
     * Конструктор доменной модели комнаты со списком участников.
     *
     * @param code       публичный код комнаты
     * @param name       название комнаты
     * @param maxPlayers максимальная вместимость
     * @param players    список участников
     * @param status     статус комнаты
     */
    public Room(String code, String name, int maxPlayers, List<RoomParticipant> players, RoomStatus status) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.maxPlayers = maxPlayers;
        if (players != null) {
            this.players = new CopyOnWriteArrayList<>(players);
        }
        this.status = status;
        this.createdAt = Instant.now();
        this.lastActivityAt = Instant.now();
    }

    /**
     * Конструктор доменной модели комнаты.
     *
     * @param code       публичный код комнаты
     * @param name       название комнаты
     * @param maxPlayers максимальная вместимость
     * @param status     статус комнаты
     */
    public Room(String code, String name, int maxPlayers, RoomStatus status) {
        this(code, name, maxPlayers, null, status);
    }

    public Room(UUID id, String code, String inviteLink, String name, String creatorId,
                RoomStatus status, int maxPlayers, Instant createdAt, Instant lastActivityAt) {
        this.id = id;
        this.code = code;
        this.inviteLink = inviteLink;
        this.name = name;
        this.creatorId = creatorId;
        this.status = status;
        this.maxPlayers = maxPlayers;
        this.createdAt = createdAt;
        this.lastActivityAt = lastActivityAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getInviteLink() {
        return inviteLink;
    }

    public void setInviteLink(String inviteLink) {
        this.inviteLink = inviteLink;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public List<RoomParticipant> getPlayers() {
        return players;
    }

    public void setPlayers(List<RoomParticipant> players) {
        this.players = new CopyOnWriteArrayList<>(players != null ? players : List.of());
    }

    /**
     * Возвращает список игроков в виде доменных моделей {@link Player}.
     *
     * @return список игроков
     */
    public List<Player> getDomainPlayers() {
        if (players == null) return List.of();
        return players.stream().map(Player::from).toList();
    }

    /**
     * Добавляет участника в комнату.
     *
     * @param participant участник
     */
    public void addPlayer(RoomParticipant participant) {
        if (participant != null) {
            this.players.removeIf(p -> p.getTelegramId().equals(participant.getTelegramId()));
            this.players.add(participant);
            this.lastActivityAt = Instant.now();
        }
    }

    /**
     * Добавляет игрока в комнату.
     *
     * @param player доменный игрок
     */
    public void addPlayer(Player player) {
        if (player != null) {
            addPlayer(player.toParticipant());
        }
    }

    /**
     * Удаляет игрока по telegramId.
     *
     * @param telegramId идентификатор Telegram
     */
    public void removePlayer(String telegramId) {
        if (telegramId != null) {
            this.players.removeIf(p -> telegramId.equals(p.getTelegramId()));
            this.lastActivityAt = Instant.now();
        }
    }

    @JsonProperty("currentPlayersCount")
    public int getCurrentPlayersCount() {
        return players != null ? players.size() : 0;
    }

    @JsonProperty("isReadyToStart")
    public boolean isReadyToStart() {
        int count = getCurrentPlayersCount();
        return status == RoomStatus.LOBBY && count >= 4 && count <= maxPlayers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }
}
