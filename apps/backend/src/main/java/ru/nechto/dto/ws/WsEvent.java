package ru.nechto.dto.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record WsEvent<T>(
        String event,
        Instant timestamp,
        T data
) {
    @JsonProperty("type")
    public String type() {
        return event;
    }

    public static <T> WsEvent<T> of(String event, T data) {
        return new WsEvent<>(event, Instant.now(), data);
    }
}
