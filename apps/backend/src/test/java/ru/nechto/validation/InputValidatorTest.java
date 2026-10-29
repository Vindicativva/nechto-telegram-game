package ru.nechto.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.nechto.exception.InvalidMaxPlayersException;
import ru.nechto.exception.InvalidPlayerNameException;
import ru.nechto.exception.InvalidRoomCodeException;
import ru.nechto.exception.InvalidRoomSettingsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Модульные тесты валидатора входных параметров (InputValidator).
 */
class InputValidatorTest {

    /**
     * Проверяет корректную валидацию и нормализацию кода комнаты к верхнему регистру с обрезкой пробелов.
     *
     * <p><b>Предусловия (Given):</b> Валидные строки кодов в разном регистре и с пробелами.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code validateAndNormalizeRoomCode}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Возвращается нормализованный код в верхнем регистре без пробелов.</p>
     */
    @ParameterizedTest(name = "Вход: \"{0}\", Ожидаемый: \"{1}\"")
    @CsvSource({
            "abcd, ABCD",
            "  X7k9M2  , X7K9M2",
            "123456, 123456",
            "ROOM12345678, ROOM12345678"
    })
    @DisplayName("Успешная валидация и нормализация кода комнаты")
    void validateAndNormalizeRoomCode_ValidInputs_Success(String input, String expected) {
        String result = InputValidator.validateAndNormalizeRoomCode(input);
        assertThat(result).isEqualTo(expected);
    }

    /**
     * Проверяет выброс InvalidRoomCodeException при невалидном формате кода комнаты.
     *
     * <p><b>Предусловия (Given):</b> Невалидные коды (null, пустые, короткие, длинные, со спецсимволами).</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code validateAndNormalizeRoomCode}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Выбрасывается {@code InvalidRoomCodeException}.</p>
     */
    @ParameterizedTest(name = "Невалидный код: \"{0}\"")
    @ValueSource(strings = {"", "   ", "ABC", "12", "TOOLONGCODE12345", "A-B-C", "ROOM!", "кодрус"})
    @DisplayName("Отказ при недопустимом коде комнаты")
    void validateAndNormalizeRoomCode_InvalidInputs_ThrowsException(String input) {
        assertThatThrownBy(() -> InputValidator.validateAndNormalizeRoomCode(input))
                .isInstanceOf(InvalidRoomCodeException.class);
    }

    /**
     * Проверяет отказ при null-коде комнаты.
     */
    @Test
    @DisplayName("Отказ при null-коде комнаты")
    void validateAndNormalizeRoomCode_Null_ThrowsException() {
        assertThatThrownBy(() -> InputValidator.validateAndNormalizeRoomCode(null))
                .isInstanceOf(InvalidRoomCodeException.class);
    }

    /**
     * Проверяет успешную валидацию имени игрока на границах длины (от 1 до 64 символов) и обрезку пробелов.
     *
     * <p><b>Предусловия (Given):</b> Корректные имена игроков длиной 1..64 символов.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code validatePlayerName}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Возвращается очищенное имя без краевых пробелов.</p>
     */
    @ParameterizedTest(name = "Корректное имя: \"{0}\"")
    @ValueSource(strings = {"A", "Игрок", "  Петр Петров  ", "1234567890123456789012345678901234567890123456789012345678901234"})
    @DisplayName("Успешная валидация имени игрока")
    void validatePlayerName_ValidInputs_Success(String input) {
        String result = InputValidator.validatePlayerName(input);
        assertThat(result).isEqualTo(input.trim());
    }

    /**
     * Проверяет отказ при пустом имени игрока или превышении лимита 64 символа.
     *
     * <p><b>Предусловия (Given):</b> Пустые строки или строки длиной > 64 символов.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code validatePlayerName}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Выбрасывается {@code InvalidPlayerNameException}.</p>
     */
    @ParameterizedTest(name = "Невалидное имя: \"{0}\"")
    @ValueSource(strings = {"", "   ", "This name is definitely way too long to be accepted because it exceeds sixty-four chars"})
    @DisplayName("Отказ при пустом имени или длине > 64")
    void validatePlayerName_InvalidLength_ThrowsException(String input) {
        assertThatThrownBy(() -> InputValidator.validatePlayerName(input))
                .isInstanceOf(InvalidPlayerNameException.class);
    }

    /**
     * Проверяет отказ при передаче null имени игрока.
     */
    @Test
    @DisplayName("Отказ при null имени игрока")
    void validatePlayerName_Null_ThrowsException() {
        assertThatThrownBy(() -> InputValidator.validatePlayerName(null))
                .isInstanceOf(InvalidPlayerNameException.class);
    }

    /**
     * Проверяет отказ при наличии HTML-тегов или управляющих символов в имени игрока.
     *
     * <p><b>Предусловия (Given):</b> Имена со спецсимволами XSS-атак ({@code <script>}, {@code <img>}, {@code <b>}) или управляющими символами ASCII.</p>
     *
     * <p><b>Действие (When):</b> Вызывается {@code validatePlayerName}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Выбрасывается {@code InvalidPlayerNameException}.</p>
     */
    @ParameterizedTest(name = "XSS/Control char имя: \"{0}\"")
    @ValueSource(strings = {"<script>", "<script>alert(1)</script>", "<b>User</b>", "User\u0000", "User\u0007"})
    @DisplayName("Отказ при наличии XSS/HTML или управляющих символов в имени игрока")
    void validatePlayerName_XssAndControlChars_ThrowsException(String input) {
        assertThatThrownBy(() -> InputValidator.validatePlayerName(input))
                .isInstanceOf(InvalidPlayerNameException.class);
    }

    /**
     * Проверяет успешную валидацию названия комнаты (1..32 символа).
     */
    @ParameterizedTest(name = "Валидное название: \"{0}\"")
    @ValueSource(strings = {"A", "Партия в пятницу", "12345678901234567890123456789012", "  Комната  "})
    @DisplayName("Успешная валидация названия комнаты")
    void validateRoomName_ValidInputs_Success(String input) {
        String result = InputValidator.validateRoomName(input);
        assertThat(result).isEqualTo(input.trim());
    }

    /**
     * Проверяет отказ при пустом названии комнаты или длине > 32 символов.
     */
    @ParameterizedTest(name = "Невалидное название: \"{0}\"")
    @ValueSource(strings = {"", "   ", "123456789012345678901234567890123", "Too long room name over thirty-two characters"})
    @DisplayName("Отказ при невалидном названии комнаты")
    void validateRoomName_InvalidInputs_ThrowsException(String input) {
        assertThatThrownBy(() -> InputValidator.validateRoomName(input))
                .isInstanceOf(InvalidRoomSettingsException.class);
    }

    /**
     * Проверяет валидацию maxPlayers: допустимы значения 4..12 при условии maxPlayers >= currentCount.
     */
    @ParameterizedTest(name = "maxPlayers={0}, currentCount={1}")
    @CsvSource({
            "4, 1",
            "6, 4",
            "12, 12",
            "8, 8"
    })
    @DisplayName("Успешная валидация maxPlayers")
    void validateMaxPlayers_ValidInputs_Success(int maxPlayers, int currentCount) {
        InputValidator.validateMaxPlayers(maxPlayers, currentCount);
    }

    /**
     * Проверяет отказ, если maxPlayers < 4 или maxPlayers > 12.
     */
    @ParameterizedTest(name = "Недопустимый maxPlayers={0}")
    @ValueSource(ints = {-1, 0, 1, 2, 3, 13, 20})
    @DisplayName("Отказ при maxPlayers вне диапазона 4..12")
    void validateMaxPlayers_OutOfRange_ThrowsException(int maxPlayers) {
        assertThatThrownBy(() -> InputValidator.validateMaxPlayers(maxPlayers, 1))
                .isInstanceOf(InvalidMaxPlayersException.class);
    }

    /**
     * Проверяет отказ, если maxPlayers меньше текущего количества участников.
     */
    @Test
    @DisplayName("Отказ при попытке установить maxPlayers меньше числа участников в комнате")
    void validateMaxPlayers_LessThanCurrentCount_ThrowsException() {
        assertThatThrownBy(() -> InputValidator.validateMaxPlayers(5, 6))
                .isInstanceOf(InvalidMaxPlayersException.class);
    }
}
