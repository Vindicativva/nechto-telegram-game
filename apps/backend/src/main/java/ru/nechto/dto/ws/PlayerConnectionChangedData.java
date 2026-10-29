package ru.nechto.dto.ws;

import ru.nechto.model.ConnectionStatus;

public record PlayerConnectionChangedData(
        String telegramId,
        ConnectionStatus connectionStatus
) {
}
