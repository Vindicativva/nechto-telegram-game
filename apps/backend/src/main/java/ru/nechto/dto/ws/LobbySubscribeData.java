package ru.nechto.dto.ws;

public record LobbySubscribeData(
        String code,
        String telegramId,
        String username,
        String displayName
) {
}
