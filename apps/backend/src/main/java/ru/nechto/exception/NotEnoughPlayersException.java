package ru.nechto.exception;

import org.springframework.http.HttpStatus;

/**
 * Исключение, выбрасываемое при попытке запуска игры с недостаточным числом игроков (менее 4).
 */
public class NotEnoughPlayersException extends LobbyException {

    public NotEnoughPlayersException(String message) {
        super(HttpStatus.CONFLICT, "NOT_ENOUGH_PLAYERS", message);
    }

    public NotEnoughPlayersException() {
        this("Для старта игры необходимо минимум 4 участника");
    }
}
