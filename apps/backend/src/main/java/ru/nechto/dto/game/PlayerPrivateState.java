package ru.nechto.dto.game;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.nechto.model.Card;
import ru.nechto.model.PlayerRole;

import java.util.List;

/**
 * Приватное состояние игрока, передаваемое строго персонально в /user/queue/game (п. 4.1.5 и п. 4.2.11 ТЗ).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Приватное состояние игрока")
public record PlayerPrivateState(
        @Schema(description = "Код игровой комнаты", example = "X7K9M2")
        String roomCode,

        @Schema(description = "Telegram ID игрока", example = "123456789")
        String telegramId,

        @Schema(description = "Секретная роль игрока (THE_THING или HUMAN)", example = "THE_THING")
        PlayerRole role,

        @Schema(description = "Список карт на руке игрока")
        List<Card> cards,

        @Schema(description = "Позиция игрока за столом (0..N-1)", example = "0")
        int position,

        @Schema(description = "Флаг, чей сейчас ход", example = "true")
        boolean isMyTurn,

        @Schema(description = "Список доступных игровых действий", example = "[\"DRAW_CARD\"]")
        List<String> availableActions
) {}
