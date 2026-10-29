package ru.nechto.exception;

import org.springframework.http.HttpStatus;

public class ActiveRoomExistsException extends LobbyException {
    public ActiveRoomExistsException() {
        super(HttpStatus.CONFLICT, "ACTIVE_ROOM_EXISTS", "У пользователя уже есть активная созданная комната");
    }

    public ActiveRoomExistsException(String message) {
        super(HttpStatus.CONFLICT, "ACTIVE_ROOM_EXISTS", message);
    }
}
