package ru.nechto.dto.game;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.nechto.model.ConnectionStatus;

/**
 * Публичная информация об игроке за игровым столом.
 * Строго не содержит информации о роли и картах на руках (п. 4.1.5 и п. 4.2.11 ТЗ).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Публичная информация об игроке за игровым столом (без раскрытия роли и карт)")
public record PublicPlayerInfo(
        @Schema(description = "ID пользователя в Telegram", example = "123456789")
        String telegramId,

        @Schema(description = "Имя пользователя (username) в Telegram", example = "ivan_ivanov")
        String username,

        @Schema(description = "Отображаемое имя игрока", example = "Иван")
        String displayName,

        @Schema(description = "Позиция игрока за столом (0..N-1)", example = "0")
        int position,

        @Schema(description = "Количество карт на руке", example = "4")
        int cardsCount,

        @Schema(description = "Флаг, жив ли игрок", example = "true")
        boolean isAlive,

        @Schema(description = "Количество оставшихся раундов нахождения в карантине", example = "0")
        int quarantineRounds,

        @Schema(description = "Сетевой статус подключения", example = "ONLINE")
        ConnectionStatus connectionStatus
) {}
