package ru.nechto.exception;

import org.springframework.http.HttpStatus;

/**
 * Исключение, выбрасываемое при попытке запуска игры с избыточным числом игроков (более 12).
 */
public class TooManyPlayersException extends LobbyException {

    public TooManyPlayersException(String message) {
        super(HttpStatus.CONFLICT, "TOO_MANY_PLAYERS", message);
    }

    public TooManyPlayersException() {
        this("Максимальное количество участников для партии составляет 12 человек");
    }
}
