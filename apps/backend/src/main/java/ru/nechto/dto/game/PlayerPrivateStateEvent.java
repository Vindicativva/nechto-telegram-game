package ru.nechto.dto.game;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.nechto.model.Card;
import ru.nechto.model.PlayerRole;

import java.time.Instant;
import java.util.List;

/**
 * WebSocket-событие PLAYER_PRIVATE_STATE, отправляемое в персональную очередь игрока /user/queue/game.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Событие отправки приватного состояния игрока PLAYER_PRIVATE_STATE")
public class PlayerPrivateStateEvent {

    @Schema(description = "Тип события", example = "PLAYER_PRIVATE_STATE")
    private String event = "PLAYER_PRIVATE_STATE";

    @Schema(description = "Алиас типа события", example = "PLAYER_PRIVATE_STATE")
    private String type = "PLAYER_PRIVATE_STATE";

    @Schema(description = "Временная метка события", example = "2026-11-20T12:00:00Z")
    private Instant timestamp = Instant.now();

    @Schema(description = "Приватное состояние игрока")
    private PlayerPrivateState data;

    public PlayerPrivateStateEvent() {
    }

    public PlayerPrivateStateEvent(PlayerPrivateState data) {
        this.data = data;
        this.timestamp = Instant.now();
    }

    public static PlayerPrivateStateEvent of(PlayerPrivateState data) {
        return new PlayerPrivateStateEvent(data);
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public PlayerPrivateState getData() {
        return data;
    }

    public void setData(PlayerPrivateState data) {
        this.data = data;
    }

    @JsonProperty("roomCode")
    public String getRoomCode() {
        return data != null ? data.roomCode() : null;
    }

    @JsonProperty("telegramId")
    public String getTelegramId() {
        return data != null ? data.telegramId() : null;
    }

    @JsonProperty("role")
    public PlayerRole getRole() {
        return data != null ? data.role() : null;
    }

    @JsonProperty("cards")
    public List<Card> getCards() {
        return data != null ? data.cards() : List.of();
    }

    @JsonProperty("position")
    public int getPosition() {
        return data != null ? data.position() : 0;
    }

    @JsonProperty("isMyTurn")
    public boolean isMyTurn() {
        return data != null && data.isMyTurn();
    }

    @JsonProperty("availableActions")
    public List<String> getAvailableActions() {
        return data != null ? data.availableActions() : List.of();
    }
}
