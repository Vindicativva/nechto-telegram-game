package ru.nechto.entity;

import ru.nechto.model.Room;
import ru.nechto.model.RoomParticipant;

import java.util.ArrayList;
import java.util.List;

public final class RoomMapper {

    private RoomMapper() {}

    public static Room toDomain(RoomEntity entity, String botUsername) {
        if (entity == null) return null;

        String inviteLink = "https://t.me/" + botUsername + "/app?startapp=" + entity.getCode();
        Room room = new Room(
                entity.getId(),
                entity.getCode(),
                inviteLink,
                entity.getName(),
                entity.getCreatorTelegramId(),
                entity.getStatus(),
                entity.getMaxPlayers(),
                entity.getCreatedAt(),
                entity.getLastActivityAt()
        );

        if (entity.getParticipants() != null) {
            List<RoomParticipant> participants = entity.getParticipants().stream()
                    .map(RoomMapper::toDomain)
                    .toList();
            room.setPlayers(new ArrayList<>(participants));
        }

        return room;
    }

    public static RoomParticipant toDomain(RoomParticipantEntity entity) {
        if (entity == null) return null;
        return new RoomParticipant(
                entity.getTelegramId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.isCreator(),
                entity.getConnectionStatus(),
                entity.getJoinedAt(),
                entity.getDisconnectedAt()
        );
    }
}
