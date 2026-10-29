package ru.nechto.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomParticipant {
    private String telegramId;
    private String username;
    private String displayName;
    @JsonProperty("isCreator")
    private boolean isCreator;
    private ConnectionStatus connectionStatus;
    private Instant joinedAt;
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

    @JsonProperty("isCreator")
    public boolean isCreator() {
        return isCreator;
    }

    @JsonProperty("isCreator")
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
        return Objects.hashCode(telegramId);
    }
}
