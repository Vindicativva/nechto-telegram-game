package ru.nechto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.entity.RoomEntity;
import ru.nechto.entity.RoomParticipantEntity;
import ru.nechto.exception.ForbiddenException;
import ru.nechto.exception.InvalidMaxPlayersException;
import ru.nechto.exception.RoomAlreadyStartedException;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.model.RoomStatus;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.websocket.RoomWebSocketBroadcaster;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты изменения настроек комнаты (RoomService.updateSettings).
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceSettingsTest {

    @Mock
    private RoomJpaRepository roomJpaRepository;

    @Mock
    private RoomWebSocketBroadcaster broadcaster;

    @Mock
    private RoomCodeGenerator codeGenerator;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomJpaRepository, broadcaster, codeGenerator);
    }

    /**
     * Проверяет успешное изменение лимита игроков maxPlayers создателем комнаты в статусе LOBBY.
     *
     * <p><b>Предусловия (Given):</b> В комнате со статусом LOBBY находится создатель (telegramId = "1001"), текущий maxPlayers = 6.</p>
     *
     * <p><b>Действие (When):</b> Создатель вызывает {@code updateSettings("SETT01", "1001", 10)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Настройки успешно обновляются в БД.</li>
     *   <li>Возвращаемый {@code RoomResponse} содержит новый лимит игроков {@code maxPlayers = 10}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Успешное изменение maxPlayers создателем комнаты в статусе LOBBY")
    void updateSettings_CreatorInLobby_Success() {
        RoomEntity room = createRoomWithHost("SETT01", 6);

        when(roomJpaRepository.findByCodeForUpdate("SETT01")).thenReturn(Optional.of(room));
        when(roomJpaRepository.save(any(RoomEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse response = roomService.updateSettings("SETT01", "1001", 10);

        assertThat(response.maxPlayers()).isEqualTo(10);
    }

    /**
     * Проверяет отказ 403 FORBIDDEN_NOT_CREATOR, если настройки пытается изменить не создатель комнаты.
     *
     * <p><b>Предусловия (Given):</b> Создатель комнаты — игрок "1001". Игрок "1002" является обычным участником.</p>
     *
     * <p><b>Действие (When):</b> Игрок "1002" пытается изменить {@code maxPlayers}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code ForbiddenException} с кодом ошибки {@code FORBIDDEN_NOT_CREATOR}.
     * </p>
     */
    @Test
    @DisplayName("Отказ 403 FORBIDDEN_NOT_CREATOR при попытке изменения настроек не создателем")
    void updateSettings_NonCreator_ThrowsForbiddenException() {
        RoomEntity room = createRoomWithHost("SETT02", 6);
        addParticipant(room, "1002", "Player 2", false);

        when(roomJpaRepository.findByCodeForUpdate("SETT02")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updateSettings("SETT02", "1002", 8))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN_NOT_CREATOR");
    }

    /**
     * Проверяет отказ 400 INVALID_MAX_PLAYERS, если создатель пытается уменьшить maxPlayers
     * ниже текущего количества уже подключенных участников.
     *
     * <p><b>Предусловия (Given):</b> В комнате уже находятся 6 участников при maxPlayers = 8.</p>
     *
     * <p><b>Действие (When):</b> Создатель пытается установить {@code maxPlayers = 5}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code InvalidMaxPlayersException} с кодом ошибки {@code INVALID_MAX_PLAYERS}.
     * </p>
     */
    @Test
    @DisplayName("Отказ 400 INVALID_MAX_PLAYERS при попытке установить maxPlayers меньше текущего числа игроков")
    void updateSettings_MaxPlayersLessThanCurrentPlayers_ThrowsInvalidMaxPlayersException() {
        RoomEntity room = createRoomWithHost("SETT03", 8);
        for (int i = 2; i <= 6; i++) {
            addParticipant(room, "P_" + i, "Player " + i, false);
        }

        assertThat(room.getParticipants()).hasSize(6);

        when(roomJpaRepository.findByCodeForUpdate("SETT03")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updateSettings("SETT03", "1001", 5))
                .isInstanceOf(InvalidMaxPlayersException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_MAX_PLAYERS");
    }

    /**
     * Проверяет отказ 409 ROOM_ALREADY_STARTED при попытке изменения настроек комнаты,
     * статус которой отличен от LOBBY (например, игра уже началась).
     *
     * <p><b>Предусловия (Given):</b> Комната переведена в статус IN_PROGRESS или FINISHED.</p>
     *
     * <p><b>Действие (When):</b> Создатель пытается изменить настройки.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code RoomAlreadyStartedException} с кодом ошибки {@code ROOM_ALREADY_STARTED}.
     * </p>
     */
    @ParameterizedTest(name = "Изменение настроек в статусе {0}")
    @EnumSource(value = RoomStatus.class, names = {"IN_PROGRESS", "FINISHED"})
    @DisplayName("Отказ 409 ROOM_ALREADY_STARTED при попытке изменения настроек после старта игры")
    void updateSettings_RoomNotLobby_ThrowsRoomAlreadyStartedException(RoomStatus status) {
        RoomEntity room = createRoomWithHost("SETT04", 8);
        room.setStatus(status);

        when(roomJpaRepository.findByCodeForUpdate("SETT04")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updateSettings("SETT04", "1001", 10))
                .isInstanceOf(RoomAlreadyStartedException.class)
                .hasFieldOrPropertyWithValue("code", "ROOM_ALREADY_STARTED");
    }

    private RoomEntity createRoomWithHost(String code, int maxPlayers) {
        Instant now = Instant.now();
        RoomEntity room = new RoomEntity(
                UUID.randomUUID(), code, "Комната " + code, "1001", RoomStatus.LOBBY,
                (short) maxPlayers, now, now, now
        );
        RoomParticipantEntity host = new RoomParticipantEntity(
                UUID.randomUUID(), room, "1001", "creator", "Хост", true, ConnectionStatus.ONLINE, now, null
        );
        room.addParticipant(host);
        return room;
    }

    private void addParticipant(RoomEntity room, String telegramId, String name, boolean isCreator) {
        RoomParticipantEntity participant = new RoomParticipantEntity(
                UUID.randomUUID(), room, telegramId, telegramId, name, isCreator,
                ConnectionStatus.ONLINE, Instant.now(), null
        );
        room.addParticipant(participant);
    }
}
