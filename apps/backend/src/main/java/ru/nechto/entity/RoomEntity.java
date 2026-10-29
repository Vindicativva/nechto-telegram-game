package ru.nechto.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import ru.nechto.model.RoomStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "rooms")
public class RoomEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 12)
    private String code;

    @Column(name = "name", nullable = false, length = 32)
    private String name;

    @Column(name = "creator_telegram_id", nullable = false, length = 64)
    private String creatorTelegramId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoomStatus status = RoomStatus.LOBBY;

    @Column(name = "max_players", nullable = false)
    private short maxPlayers;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt = Instant.now();

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("joinedAt ASC")
    private List<RoomParticipantEntity> participants = new ArrayList<>();

    public RoomEntity() {
    }

    public RoomEntity(UUID id, String code, String name, String creatorTelegramId,
                      RoomStatus status, int maxPlayers, Instant createdAt,
                      Instant updatedAt, Instant lastActivityAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.creatorTelegramId = creatorTelegramId;
        this.status = status;
        this.maxPlayers = (short) maxPlayers;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreatorTelegramId() {
        return creatorTelegramId;
    }

    public void setCreatorTelegramId(String creatorTelegramId) {
        this.creatorTelegramId = creatorTelegramId;
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
        this.maxPlayers = (short) maxPlayers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public List<RoomParticipantEntity> getParticipants() {
        return participants;
    }

    public void setParticipants(List<RoomParticipantEntity> participants) {
        this.participants = participants;
    }

    public void addParticipant(RoomParticipantEntity participant) {
        participants.add(participant);
        participant.setRoom(this);
    }

    public void removeParticipant(RoomParticipantEntity participant) {
        participants.remove(participant);
        participant.setRoom(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoomEntity that = (RoomEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
