package ru.nechto.dto.game;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TurnDirection;

import java.util.List;

/**
 * Публичное состояние игровой партии (п. 4.1.5 и п. 4.2.11 ТЗ),
 * транслируемое всем участникам через общий WebSocket-топик /topic/room/{code}.
 * Не содержит скрытых карт или ролей игроков.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Публичное состояние игровой партии")
public record PublicGameState(
        @Schema(description = "Код игровой комнаты", example = "X7K9M2")
        String roomCode,

        @Schema(description = "Статус комнаты", example = "IN_PROGRESS")
        RoomStatus status,

        @Schema(description = "Номер текущего хода (0..)", example = "0")
        int currentTurnIndex,

        @Schema(description = "Telegram ID игрока, чей сейчас ход", example = "123456789")
        String currentTurnPlayerId,

        @Schema(description = "Направление хода за игровым столом", example = "CLOCKWISE")
        TurnDirection turnDirection,

        @Schema(description = "Количество оставшихся карт в колоде добора", example = "39")
        int deckCardsCount,

        @Schema(description = "Количество карт в сбросе", example = "0")
        int discardCardsCount,

        @Schema(description = "Список участников в порядке рассадки за столом")
        List<PublicPlayerInfo> players
) {}
