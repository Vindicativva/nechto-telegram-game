package ru.nechto.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Направление передачи хода за игровым столом.
 */
@Schema(description = "Направление передачи хода за игровым столом")
public enum TurnDirection {
    @Schema(description = "По часовой стрелке (стандартное направление)")
    CLOCKWISE,

    @Schema(description = "Против часовой стрелки")
    COUNTER_CLOCKWISE
}
