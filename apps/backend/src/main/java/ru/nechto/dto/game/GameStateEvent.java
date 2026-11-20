package ru.nechto.dto.game;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TurnDirection;

import java.time.Instant;
import java.util.List;

/**
 * WebSocket-событие GAME_STATE, рассылаемое всем клиентам в топик /topic/room/{code}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Событие синхронизации публичного состояния игры GAME_STATE")
public class GameStateEvent {

    @Schema(description = "Тип события", example = "GAME_STATE")
    private String event = "GAME_STATE";

    @Schema(description = "Алиас типа события", example = "GAME_STATE")
    private String type = "GAME_STATE";

    @Schema(description = "Временная метка события", example = "2026-11-20T12:00:00Z")
    private Instant timestamp = Instant.now();

    @Schema(description = "Данные публичного состояния игры")
    private PublicGameState data;

    public GameStateEvent() {
    }

    public GameStateEvent(PublicGameState data) {
        this.data = data;
        this.timestamp = Instant.now();
    }

    public static GameStateEvent of(PublicGameState data) {
        return new GameStateEvent(data);
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

    public PublicGameState getData() {
        return data;
    }

    public void setData(PublicGameState data) {
        this.data = data;
    }

    @JsonProperty("roomCode")
    public String getRoomCode() {
        return data != null ? data.roomCode() : null;
    }

    @JsonProperty("status")
    public RoomStatus getStatus() {
        return data != null ? data.status() : null;
    }

    @JsonProperty("currentTurnIndex")
    public int getCurrentTurnIndex() {
        return data != null ? data.currentTurnIndex() : 0;
    }

    @JsonProperty("currentTurnPlayerId")
    public String getCurrentTurnPlayerId() {
        return data != null ? data.currentTurnPlayerId() : null;
    }

    @JsonProperty("turnDirection")
    public TurnDirection getTurnDirection() {
        return data != null ? data.turnDirection() : null;
    }

    @JsonProperty("deckCardsCount")
    public int getDeckCardsCount() {
        return data != null ? data.deckCardsCount() : 0;
    }

    @JsonProperty("discardCardsCount")
    public int getDiscardCardsCount() {
        return data != null ? data.discardCardsCount() : 0;
    }

    @JsonProperty("players")
    public List<PublicPlayerInfo> getPlayers() {
        return data != null ? data.players() : List.of();
    }
}
