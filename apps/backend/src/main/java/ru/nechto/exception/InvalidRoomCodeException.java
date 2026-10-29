package ru.nechto.exception;

import org.springframework.http.HttpStatus;

public class InvalidRoomCodeException extends LobbyException {
    public InvalidRoomCodeException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_ROOM_CODE", message);
    }

    public InvalidRoomCodeException() {
        this("Некорректный формат кода комнаты (ожидается 4-12 символов A-Z, 0-9)");
    }
}
