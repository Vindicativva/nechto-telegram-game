package ru.nechto.exception;

import org.springframework.http.HttpStatus;

public class InvalidRoomSettingsException extends LobbyException {
    public InvalidRoomSettingsException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_ROOM_SETTINGS", message);
    }

    public InvalidRoomSettingsException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
