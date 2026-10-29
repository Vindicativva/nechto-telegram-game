package ru.nechto.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends LobbyException {
    public ForbiddenException() {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN_NOT_CREATOR", "Действие доступно только создателю комнаты");
    }

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN_NOT_CREATOR", message);
    }
}
