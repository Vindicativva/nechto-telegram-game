package ru.nechto.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends LobbyException {
    public UnauthorizedException() {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Отсутствуют или невалидны данные пользователя");
    }

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
