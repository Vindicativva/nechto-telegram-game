package ru.nechto.dto.ws;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.nechto.dto.response.RoomResponse;

public record SettingsUpdatedData(
        int maxPlayers,
        @JsonProperty("isReadyToStart")
        boolean isReadyToStart,
        RoomResponse room
) {
}
