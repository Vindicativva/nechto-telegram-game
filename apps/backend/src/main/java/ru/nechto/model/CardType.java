package ru.nechto.model;

/**
 * Перечисление базовых типов игровых карт (Event и Panic) согласно ТЗ.
 * <ul>
 *   <li>{@link #EVENT} — карта события (разыгрывается в свой ход, на защите или удерживается на руке).</li>
 *   <li>{@link #PANIC} — карта паники (разыгрывается немедленно при взятии из общей колоды).</li>
 * </ul>
 */
public enum CardType {
    EVENT,
    PANIC;

    /**
     * Проверяет, является ли карта картой паники.
     *
     * @return {@code true}, если тип карты — {@link #PANIC}
     */
    public boolean isPanic() {
        return this == PANIC;
    }

    /**
     * Проверяет, является ли карта картой события.
     *
     * @return {@code true}, если тип карты — {@link #EVENT}
     */
    public boolean isEvent() {
        return this == EVENT;
    }
}
