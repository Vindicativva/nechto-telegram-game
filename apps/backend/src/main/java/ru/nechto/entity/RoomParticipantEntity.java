package ru.nechto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import ru.nechto.model.ConnectionStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "room_participants", uniqueConstraints = {
        @UniqueConstraint(name = "uq_room_telegram", columnNames = {"room_id", "telegram_id"})
})
public class RoomParticipantEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;

    @Column(name = "telegram_id", nullable = false, length = 64)
    private String telegramId;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @Column(name = "is_creator", nullable = false)
    private boolean isCreator;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 20)
    private ConnectionStatus connectionStatus = ConnectionStatus.ONLINE;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    public RoomParticipantEntity() {
    }

    public RoomParticipantEntity(UUID id, RoomEntity room, String telegramId, String username,
                                 String displayName, boolean isCreator,
                                 ConnectionStatus connectionStatus, Instant joinedAt,
                                 Instant disconnectedAt) {
        this.id = id;
        this.room = room;
        this.telegramId = telegramId;
        this.username = username;
        this.displayName = displayName;
        this.isCreator = isCreator;
        this.connectionStatus = connectionStatus;
        this.joinedAt = joinedAt;
        this.disconnectedAt = disconnectedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RoomEntity getRoom() {
        return room;
    }

    public void setRoom(RoomEntity room) {
        this.room = room;
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
        RoomParticipantEntity that = (RoomParticipantEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
