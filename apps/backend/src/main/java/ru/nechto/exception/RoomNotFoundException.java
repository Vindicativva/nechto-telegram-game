package ru.nechto.exception;

import org.springframework.http.HttpStatus;

public class RoomNotFoundException extends LobbyException {
    public RoomNotFoundException() {
        super(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Комната не найдена или недоступна");
    }

    public RoomNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", message);
    }
}
