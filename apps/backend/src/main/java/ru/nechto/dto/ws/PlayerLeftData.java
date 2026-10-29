package ru.nechto.dto.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import ru.nechto.dto.response.RoomResponse;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerLeftData(
        String leftPlayerId,
        String newCreatorId,
        RoomResponse room
) {
}
