package ru.nechto.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.nechto.model.Room;
import ru.nechto.model.RoomParticipant;
import ru.nechto.model.RoomStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String code,
        String inviteLink,
        String name,
        String creatorId,
        RoomStatus status,
        int maxPlayers,
        int currentPlayersCount,
        @JsonProperty("isReadyToStart")
        boolean isReadyToStart,
        List<RoomParticipant> players,
        Instant createdAt
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getCode(),
                room.getInviteLink(),
                room.getName(),
                room.getCreatorId(),
                room.getStatus(),
                room.getMaxPlayers(),
                room.getCurrentPlayersCount(),
                room.isReadyToStart(),
                new ArrayList<>(room.getPlayers()),
                room.getCreatedAt()
        );
    }
}
