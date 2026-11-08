package ru.nechto.dto.ws;

import java.time.Instant;

public record WsEvent<T>(
        String event,
        Instant timestamp,
        T data
) {
    public static <T> WsEvent<T> of(String event, T data) {
        return new WsEvent<>(event, Instant.now(), data);
    }
}
