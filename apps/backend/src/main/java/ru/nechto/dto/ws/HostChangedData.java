package ru.nechto.dto.ws;

import ru.nechto.model.RoomParticipant;
import java.util.List;

public record HostChangedData(
        String previousCreatorId,
        String newCreatorId,
        List<RoomParticipant> players
) {
}
