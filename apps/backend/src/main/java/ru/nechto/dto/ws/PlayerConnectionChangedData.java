package ru.nechto.dto.ws;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.nechto.model.ConnectionStatus;

public record PlayerConnectionChangedData(
        String telegramId,
        ConnectionStatus connectionStatus
) {
    @JsonProperty("status")
    public String getStatus() {
        return connectionStatus != null ? connectionStatus.name() : null;
    }
}
