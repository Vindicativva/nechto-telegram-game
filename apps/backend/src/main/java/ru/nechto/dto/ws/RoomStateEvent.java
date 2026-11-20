package ru.nechto.dto.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.model.RoomParticipant;
import ru.nechto.model.RoomStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Событие обновления состояния комнаты (ROOM_STATE), рассылаемое всем подключенным клиентам в топик /topic/room/{code}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Событие синхронизации актуального состояния лобби ROOM_STATE")
public class RoomStateEvent {

    @Schema(description = "Тип события", example = "ROOM_STATE")
    private String event = "ROOM_STATE";

    @Schema(description = "Алиас типа события для совместимости", example = "ROOM_STATE")
    private String type = "ROOM_STATE";

    @Schema(description = "Временная метка генерации события", example = "2026-11-17T18:00:00Z")
    private Instant timestamp = Instant.now();

    @Schema(description = "Актуальное состояние комнаты")
    private RoomResponse data;

    public RoomStateEvent() {
    }

    public RoomStateEvent(RoomResponse data) {
        this.data = data;
        this.timestamp = Instant.now();
    }

    public static RoomStateEvent of(RoomResponse data) {
        return new RoomStateEvent(data);
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

    public RoomResponse getData() {
        return data;
    }

    public void setData(RoomResponse data) {
        this.data = data;
    }

    @JsonProperty("room")
    public RoomResponse getRoom() {
        return data;
    }

    @JsonProperty("code")
    public String getCode() {
        return data != null ? data.code() : null;
    }

    @JsonProperty("status")
    public RoomStatus getStatus() {
        return data != null ? data.status() : null;
    }

    @JsonProperty("players")
    public List<RoomParticipant> getPlayers() {
        return data != null ? data.players() : List.of();
    }

    @JsonProperty("currentPlayersCount")
    public int getCurrentPlayersCount() {
        return data != null ? data.currentPlayersCount() : 0;
    }

    @JsonProperty("maxPlayers")
    public int getMaxPlayers() {
        return data != null ? data.maxPlayers() : 0;
    }

    @JsonProperty("isReadyToStart")
    public boolean isReadyToStart() {
        return data != null && data.isReadyToStart();
    }

    @JsonProperty("creatorId")
    public String getCreatorId() {
        return data != null ? data.creatorId() : null;
    }
}
