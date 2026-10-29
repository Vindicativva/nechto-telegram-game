package ru.nechto.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends LobbyException {
    public ForbiddenException() {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", "Действие доступно только создателю комнаты");
    }

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
