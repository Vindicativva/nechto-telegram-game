package ru.nechto.exception;

import org.springframework.http.HttpStatus;

public class InvalidPlayerNameException extends LobbyException {
    public InvalidPlayerNameException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_PLAYER_NAME", message);
    }

    public InvalidPlayerNameException() {
        this("Имя игрока содержит недопустимые символы или HTML-теги");
    }
}
