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
        this.players = new CopyOnWriteArrayList<>(players);
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
