package ru.nechto.exception;

import org.springframework.http.HttpStatus;

public class RoomFullException extends LobbyException {
    public RoomFullException() {
        super(HttpStatus.CONFLICT, "ROOM_FULL", "Комната заполнена");
    }

    public RoomFullException(String message) {
        super(HttpStatus.CONFLICT, "ROOM_FULL", message);
    }
}
