package ru.nechto.dto.game;

import ru.nechto.dto.response.RoomResponse;

import java.util.Map;

/**
 * Результат выполнения серверного сценария старта игровой партии.
 */
public record GameStartResult(
        RoomResponse roomResponse,
        PublicGameState publicGameState,
        Map<String, PlayerPrivateState> playerPrivateStates,
        ActiveGameState activeGameState
) {}
