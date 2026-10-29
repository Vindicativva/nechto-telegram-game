package ru.nechto.validation;

import org.springframework.http.HttpStatus;
import ru.nechto.exception.InvalidPlayerNameException;
import ru.nechto.exception.InvalidRoomCodeException;
import ru.nechto.exception.InvalidRoomSettingsException;

import java.util.Locale;
import java.util.regex.Pattern;

public final class InputValidator {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{4,12}$");
    private static final Pattern HTML_PATTERN = Pattern.compile("[<>]");
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");

    private InputValidator() {}

    public static String validateAndNormalizeRoomCode(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidRoomCodeException("Код комнаты обязателен");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new InvalidRoomCodeException("Код комнаты должен содержать от 4 до 12 латинских букв или цифр");
        }
        return normalized;
    }

    public static String validatePlayerName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new InvalidPlayerNameException("Имя игрока обязательно");
        }
        String trimmed = displayName.trim();
        if (trimmed.length() < 1 || trimmed.length() > 64) {
            throw new InvalidPlayerNameException("Длина имени игрока должна составлять от 1 до 64 символов");
        }
        if (HTML_PATTERN.matcher(trimmed).find()) {
            throw new InvalidPlayerNameException("Имя игрока не должно содержать HTML-теги");
        }
        if (CONTROL_CHARS_PATTERN.matcher(trimmed).find()) {
            throw new InvalidPlayerNameException("Имя игрока не должно содержать управляющие символы");
        }
        return trimmed;
    }

    public static String validateRoomName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRoomSettingsException("Название комнаты обязательно");
        }
        String trimmed = name.trim();
        if (trimmed.length() < 1 || trimmed.length() > 32) {
            throw new InvalidRoomSettingsException("Название комнаты должно содержать от 1 до 32 символов");
        }
        return trimmed;
    }

    public static void validateMaxPlayers(Integer maxPlayers, int currentPlayersCount) {
        if (maxPlayers == null || maxPlayers < 4 || maxPlayers > 12) {
            throw new InvalidRoomSettingsException(HttpStatus.BAD_REQUEST, "INVALID_MAX_PLAYERS",
                    "Количество игроков должно быть от 4 до 12");
        }
        if (maxPlayers < currentPlayersCount) {
            throw new InvalidRoomSettingsException(HttpStatus.BAD_REQUEST, "INVALID_MAX_PLAYERS",
                    "Максимальное число игроков не может быть меньше текущего количества участников (" + currentPlayersCount + ")");
        }
    }
}
