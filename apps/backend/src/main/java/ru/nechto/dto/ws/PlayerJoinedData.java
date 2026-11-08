package ru.nechto.dto.ws;

import ru.nechto.dto.response.RoomResponse;
import ru.nechto.model.RoomParticipant;

public record PlayerJoinedData(
        RoomParticipant joinedPlayer,
        RoomResponse room
) {
}
