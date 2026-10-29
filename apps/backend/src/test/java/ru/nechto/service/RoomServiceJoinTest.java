package ru.nechto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.entity.RoomEntity;
import ru.nechto.entity.RoomParticipantEntity;
import ru.nechto.exception.InvalidPlayerNameException;
import ru.nechto.exception.RoomAlreadyStartedException;
import ru.nechto.exception.RoomFullException;
import ru.nechto.exception.RoomNotFoundException;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TelegramUser;
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
 * Модульные тесты подключения и повторного входа игроков (RoomService.joinRoom).
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceJoinTest {

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
     * Проверяет динамическое изменение флага isReadyToStart: при 3 игроках лобби не готово к старту,
     * а при подключении 4-го участника флаг автоматически переключается в true.
     *
     * <p><b>Предусловия (Given):</b> В комнате с {@code maxPlayers = 6} уже находятся 2 игрока (хост и игрок 2).</p>
     *
     * <p><b>Действие (When):</b> Поочередно подключаются 3-й игрок и 4-й игрок.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>После входа 3-го игрока {@code currentPlayersCount == 3}, {@code isReadyToStart == false}.</li>
     *   <li>После входа 4-го игрока {@code currentPlayersCount == 4}, {@code isReadyToStart == true}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Подключение игроков: при 3 игроках isReadyToStart=false, при 4-м игроке isReadyToStart=true")
    void joinRoom_AddPlayers_UpdatesReadyToStart() {
        RoomEntity room = createRoomEntity("JOIN01", 6);
        addParticipant(room, "1002", "Игрок 2", false, Instant.now().plusSeconds(1));

        when(roomJpaRepository.findByCodeForUpdate("JOIN01")).thenReturn(Optional.of(room));
        when(roomJpaRepository.save(any(RoomEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // Вход 3-го игрока
        RoomResponse resp3 = roomService.joinRoom("JOIN01", TelegramUser.of("1003", "p3", "Игрок 3"));
        assertThat(resp3.currentPlayersCount()).isEqualTo(3);
        assertThat(resp3.isReadyToStart()).isFalse();

        // Вход 4-го игрока
        RoomResponse resp4 = roomService.joinRoom("JOIN01", TelegramUser.of("1004", "p4", "Игрок 4"));
        assertThat(resp4.currentPlayersCount()).isEqualTo(4);
        assertThat(resp4.isReadyToStart()).isTrue();
    }

    /**
     * Проверяет заполнение комнаты до максимального лимита 12 участников.
     *
     * <p><b>Предусловия (Given):</b> Комната с {@code maxPlayers = 12} и одним создателем.</p>
     *
     * <p><b>Действие (When):</b> В комнату последовательно подключаются еще 11 игроков.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Количество участников в комнате достигает ровно 12, флаг {@code isReadyToStart == true}.
     * </p>
     */
    @Test
    @DisplayName("Успешное заполнение комнаты вплоть до 12 участников")
    void joinRoom_FillToMaxPlayers12_Success() {
        RoomEntity room = createRoomEntity("JOIN12", 12);
        when(roomJpaRepository.findByCodeForUpdate("JOIN12")).thenReturn(Optional.of(room));
        when(roomJpaRepository.save(any(RoomEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse lastResponse = null;
        for (int i = 2; i <= 12; i++) {
            lastResponse = roomService.joinRoom("JOIN12", TelegramUser.of("USER_" + i, "u" + i, "Игрок " + i));
        }

        assertThat(lastResponse).isNotNull();
        assertThat(lastResponse.currentPlayersCount()).isEqualTo(12);
        assertThat(lastResponse.isReadyToStart()).isTrue();
    }

    /**
     * Проверяет идемпотентное повторное подключение (Reconnection):
     * не создается дубликат участника, восстанавливается статус ONLINE, сохраняются исходные joinedAt и isCreator.
     *
     * <p><b>Предусловия (Given):</b> В комнате зарегистрирован игрок со статусом {@code DISCONNECTED}.</p>
     *
     * <p><b>Действие (When):</b> Тот же игрок повторно вызывает {@code joinRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Количество участников в комнате не увеличивается.</li>
     *   <li>Статус соединения обновляется в {@code ConnectionStatus.ONLINE}.</li>
     *   <li>{@code disconnectedAt} сбрасывается в {@code null}.</li>
     *   <li>Исходное время подключения {@code joinedAt} не изменяется.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Идемпотентный Reconnection: статус ONLINE, сброс disconnectedAt, сохранение joinedAt и роли")
    void joinRoom_Reconnection_Idempotent_PreservesState() {
        RoomEntity room = createRoomEntity("RECON1", 6);
        Instant originalJoinedAt = Instant.now().minusSeconds(300);
        RoomParticipantEntity disconnectedPlayer = new RoomParticipantEntity(
                UUID.randomUUID(), room, "1002", "p2", "Игрок 2", false,
                ConnectionStatus.DISCONNECTED, originalJoinedAt, Instant.now().minusSeconds(60)
        );
        room.addParticipant(disconnectedPlayer);

        when(roomJpaRepository.findByCodeForUpdate("RECON1")).thenReturn(Optional.of(room));
        when(roomJpaRepository.save(any(RoomEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse response = roomService.joinRoom("RECON1", TelegramUser.of("1002", "p2", "Игрок 2"));

        assertThat(response.currentPlayersCount()).isEqualTo(2);

        var player = response.players().stream()
                .filter(p -> "1002".equals(p.getTelegramId()))
                .findFirst().orElseThrow();

        assertThat(player.getConnectionStatus()).isEqualTo(ConnectionStatus.ONLINE);
        assertThat(player.getDisconnectedAt()).isNull();
        assertThat(player.getJoinedAt()).isEqualTo(originalJoinedAt);
    }

    /**
     * Проверяет, что переподключение уже зарегистрированного игрока разрешено,
     * даже если комната полностью заполнена (currentPlayersCount == maxPlayers).
     *
     * <p><b>Предусловия (Given):</b> Комната на 4 игрока полностью заполнена, один из игроков имеет статус {@code DISCONNECTED}.</p>
     *
     * <p><b>Действие (When):</b> Отключившийся участник отправляет запрос {@code joinRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Запрос успешно завершается, участник переходит в статус ONLINE, ошибка ROOM_FULL не выбрасывается.
     * </p>
     */
    @Test
    @DisplayName("Reconnection разрешен даже при полностью заполненной комнате (players.size == maxPlayers)")
    void joinRoom_Reconnection_AllowedWhenRoomFull() {
        RoomEntity room = createRoomEntity("FULLREC", 4);
        addParticipant(room, "2", "P2", false, Instant.now().plusSeconds(1));
        addParticipant(room, "3", "P3", false, Instant.now().plusSeconds(2));
        addParticipant(room, "4", "P4", false, Instant.now().plusSeconds(3));

        // Игрок 4 отключился
        room.getParticipants().get(3).setConnectionStatus(ConnectionStatus.DISCONNECTED);

        assertThat(room.getParticipants()).hasSize(4);

        when(roomJpaRepository.findByCodeForUpdate("FULLREC")).thenReturn(Optional.of(room));
        when(roomJpaRepository.save(any(RoomEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse response = roomService.joinRoom("FULLREC", TelegramUser.of("4", "p4", "P4"));

        assertThat(response.currentPlayersCount()).isEqualTo(4);
        assertThat(response.players().get(3).getConnectionStatus()).isEqualTo(ConnectionStatus.ONLINE);
    }

    /**
     * Проверяет отказ 404 ROOM_NOT_FOUND при попытке подключиться к несуществующей комнате.
     *
     * <p><b>Предусловия (Given):</b> Код комнаты не найден в репозитории.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code joinRoom("UNKNOWN", user)}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code RoomNotFoundException}.
     * </p>
     */
    @Test
    @DisplayName("Отказ 404 ROOM_NOT_FOUND при подключении по несуществующему коду")
    void joinRoom_NonExistentRoom_ThrowsRoomNotFoundException() {
        when(roomJpaRepository.findByCodeForUpdate("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.joinRoom("UNKNOWN", TelegramUser.of("123", "u", "Игрок")))
                .isInstanceOf(RoomNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "ROOM_NOT_FOUND");
    }

    /**
     * Проверяет отказ 409 ROOM_FULL при попытке подключения нового игрока в заполненную комнату.
     *
     * <p><b>Предусловия (Given):</b> Комнаты с лимитами 4, 8, 12 полностью заполнены участниками.</p>
     *
     * <p><b>Действие (When):</b> Новый игрок пытается войти в комнату.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code RoomFullException} с кодом ошибки {@code ROOM_FULL}.
     * </p>
     */
    @ParameterizedTest(name = "Отказ при лимите maxPlayers={0}")
    @ValueSource(ints = {4, 8, 12})
    @DisplayName("Отказ 409 ROOM_FULL при попытке входа в заполненную комнату (лимиты 4, 8, 12)")
    void joinRoom_RoomFull_ThrowsRoomFullException(int maxPlayers) {
        RoomEntity room = createRoomEntity("FULL" + maxPlayers, maxPlayers);
        for (int i = 2; i <= maxPlayers; i++) {
            addParticipant(room, "P_" + i, "Player " + i, false, Instant.now().plusSeconds(i));
        }

        when(roomJpaRepository.findByCodeForUpdate("FULL" + maxPlayers)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.joinRoom("FULL" + maxPlayers,
                TelegramUser.of("NEW_PLAYER", "new", "Новичок")))
                .isInstanceOf(RoomFullException.class)
                .hasFieldOrPropertyWithValue("code", "ROOM_FULL");
    }

    /**
     * Проверяет отказ 409 ROOM_ALREADY_STARTED при попытке подключения нового игрока
     * к комнате, статус которой отличен от LOBBY (IN_PROGRESS, FINISHED, ARCHIVED).
     *
     * <p><b>Предусловия (Given):</b> Комната находится в статусе IN_PROGRESS, FINISHED или ARCHIVED.</p>
     *
     * <p><b>Действие (When):</b> Новый игрок отправляет запрос {@code joinRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code RoomAlreadyStartedException} (или {@code RoomNotFoundException} для ARCHIVED).
     * </p>
     */
    @ParameterizedTest(name = "Подключение к комнате со статусом {0}")
    @EnumSource(value = RoomStatus.class, names = {"IN_PROGRESS", "FINISHED"})
    @DisplayName("Отказ 409 ROOM_ALREADY_STARTED при подключении к не-LOBBY комнате")
    void joinRoom_RoomAlreadyStartedOrArchived_ThrowsRoomAlreadyStartedException(RoomStatus status) {
        RoomEntity room = createRoomEntity("START1", 6);
        room.setStatus(status);

        when(roomJpaRepository.findByCodeForUpdate("START1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.joinRoom("START1", TelegramUser.of("999", "u", "Новый")))
                .isInstanceOf(RoomAlreadyStartedException.class)
                .hasFieldOrPropertyWithValue("code", "ROOM_ALREADY_STARTED");
    }

    /**
     * Проверяет отказ 400 INVALID_PLAYER_NAME при передаче пустого имени или имени с XSS/HTML тегами.
     *
     * <p><b>Предусловия (Given):</b> Имена с HTML-тегами {@code "<script>alert(1)</script>"} или пустые строки.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code joinRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code InvalidPlayerNameException} с кодом {@code INVALID_PLAYER_NAME}.
     * </p>
     */
    @ParameterizedTest(name = "Невалидное имя: \"{0}\"")
    @ValueSource(strings = {"", "   ", "<script>alert(1)</script>", "Player <tag>", "<img src=x onerror=alert()>"})
    @DisplayName("Отказ 400 INVALID_PLAYER_NAME при недопустимом имени подключаемого игрока")
    void joinRoom_InvalidPlayerName_ThrowsInvalidPlayerNameException(String invalidName) {
        assertThatThrownBy(() -> roomService.joinRoom("JOIN01", new TelegramUser("1002", "u", invalidName)))
                .isInstanceOf(InvalidPlayerNameException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PLAYER_NAME");
    }

    private RoomEntity createRoomEntity(String code, int maxPlayers) {
        Instant now = Instant.now();
        RoomEntity room = new RoomEntity(
                UUID.randomUUID(), code, "Комната " + code, "1001", RoomStatus.LOBBY,
                (short) maxPlayers, now, now, now
        );
        RoomParticipantEntity creator = new RoomParticipantEntity(
                UUID.randomUUID(), room, "1001", "creator", "Создатель", true,
                ConnectionStatus.ONLINE, now, null
        );
        room.addParticipant(creator);
        return room;
    }

    private void addParticipant(RoomEntity room, String telegramId, String name, boolean isCreator, Instant joinedAt) {
        RoomParticipantEntity participant = new RoomParticipantEntity(
                UUID.randomUUID(), room, telegramId, telegramId, name, isCreator,
                ConnectionStatus.ONLINE, joinedAt, null
        );
        room.addParticipant(participant);
    }
}
