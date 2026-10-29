package ru.nechto.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.nechto.model.Room;
import ru.nechto.model.RoomParticipant;
import ru.nechto.model.RoomStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Schema(description = "Состояние игровой комнаты и список её участников")
public record RoomResponse(
        @Schema(description = "Уникальный внутренний идентификатор комнаты (UUID)", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,

        @Schema(description = "Уникальный короткий код комнаты", example = "X7K9M2")
        String code,

        @Schema(description = "Ссылка-приглашение для запуска Telegram Mini App", example = "https://t.me/StayAwayGameBot/play?startapp=X7K9M2")
        String inviteLink,

        @Schema(description = "Название комнаты", example = "Вечерняя партия")
        String name,

        @Schema(description = "Telegram ID текущего создателя (хоста) комнаты", example = "123456789")
        String creatorId,

        @Schema(description = "Текущий статус комнаты (LOBBY, IN_PROGRESS, FINISHED, ARCHIVED)", example = "LOBBY")
        RoomStatus status,

        @Schema(description = "Максимально допустимое количество игроков (от 4 до 12)", example = "6")
        int maxPlayers,

        @Schema(description = "Текущее количество участников в комнате", example = "3")
        int currentPlayersCount,

        @JsonProperty("isReadyToStart")
        @Schema(description = "Флаг готовности комнаты к старту (true, если участников от 4 до maxPlayers)", example = "false")
        boolean isReadyToStart,

        @Schema(description = "Список участников комнаты")
        List<RoomParticipant> players,

        @Schema(description = "Временная метка создания комнаты", example = "2026-10-29T12:00:00.000Z")
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
