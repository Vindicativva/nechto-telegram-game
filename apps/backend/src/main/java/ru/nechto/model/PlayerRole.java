package ru.nechto.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Роли участников в игровой партии (п. 4.2.4 ТЗ):
 * <ul>
 *     <li>THE_THING — «Нечто», начальный заразитель, получает карту «Нечто» при старте партии;</li>
 *     <li>HUMAN — «Человек», обычный выживший, цель — выжить и уничтожить Нечто;</li>
 *     <li>INFECTED — «Зараженный», человек, получивший карту «Заражение» от Нечто в процессе игры.</li>
 * </ul>
 */
@Schema(description = "Роль участника в игровой партии")
public enum PlayerRole {
    @Schema(description = "Нечто — начальный заразитель (получает карту «Нечто» №1)")
    THE_THING,

    @Schema(description = "Человек — выживший исследователь")
    HUMAN,

    @Schema(description = "Зараженный — человек, зараженный картой «Заражение» от Нечто")
    INFECTED
}
