package ru.nechto.exception;

import org.springframework.http.HttpStatus;

public class InvalidMaxPlayersException extends InvalidRoomSettingsException {
    public InvalidMaxPlayersException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_MAX_PLAYERS", "Лимит игроков не может быть меньше текущего числа участников в комнате");
    }

    public InvalidMaxPlayersException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_MAX_PLAYERS", message);
    }
}
