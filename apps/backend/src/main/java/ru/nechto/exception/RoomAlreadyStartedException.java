package ru.nechto.exception;

import org.springframework.http.HttpStatus;

public class RoomAlreadyStartedException extends LobbyException {
    public RoomAlreadyStartedException() {
        super(HttpStatus.CONFLICT, "ROOM_ALREADY_STARTED", "Игра уже началась, подключение невозможно");
    }

    public RoomAlreadyStartedException(String message) {
        super(HttpStatus.CONFLICT, "ROOM_ALREADY_STARTED", message);
    }
}
