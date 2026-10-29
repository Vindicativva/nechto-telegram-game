package ru.nechto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.entity.RoomEntity;
import ru.nechto.entity.RoomParticipantEntity;
import ru.nechto.exception.ActiveRoomExistsException;
import ru.nechto.exception.InvalidMaxPlayersException;
import ru.nechto.exception.InvalidPlayerNameException;
import ru.nechto.exception.InvalidRoomSettingsException;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TelegramUser;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.websocket.RoomWebSocketBroadcaster;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты создания игровой комнаты (RoomService.createRoom).
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceCreateTest {

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
     * Проверяет успешное создание комнаты при всех допустимых значениях maxPlayers (от 4 до 12 включительно).
     *
     * <p><b>Предусловия (Given):</b> Пользователь Telegram без активных комнат отправляет запрос
     * на создание комнаты с корректным именем и допустимым числом игроков.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code createRoom} для каждого значения {@code maxPlayers} от 4 до 12.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Сущность сохраняется в репозитории с указанным {@code maxPlayers}.</li>
     *   <li>Возвращаемый объект {@code RoomResponse} содержит переданный лимит игроков.</li>
     *   <li>Статус комнаты устанавливается в {@code LOBBY}.</li>
     * </ul>
     * </p>
     */
    @ParameterizedTest(name = "Создание комнаты с допустимым лимитом участников: maxPlayers={0}")
    @ValueSource(ints = {4, 5, 6, 7, 8, 9, 10, 11, 12})
    @DisplayName("Успешное создание комнаты при допустимых значениях maxPlayers от 4 до 12")
    void createRoom_ValidMaxPlayers_Success(int maxPlayers) {
        TelegramUser creator = TelegramUser.of("12345", "creator_nick", "Создатель");
        CreateRoomRequest request = new CreateRoomRequest("Партия " + maxPlayers, maxPlayers);

        when(roomJpaRepository.findActiveByCreatorId("12345")).thenReturn(Collections.emptyList());
        when(codeGenerator.generateCode()).thenReturn("ABCD" + maxPlayers);
        when(roomJpaRepository.save(any(RoomEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomResponse response = roomService.createRoom(creator, request);

        assertThat(response).isNotNull();
        assertThat(response.maxPlayers()).isEqualTo(maxPlayers);
        assertThat(response.status()).isEqualTo(RoomStatus.LOBBY);
        assertThat(response.currentPlayersCount()).isEqualTo(1);
    }

    /**
     * Проверяет, что при создании комнаты создатель автоматически регистрируется
     * первым участником в списке {@code players} с ролью хоста, статусом ONLINE и неготовностью лобби к старту.
     *
     * <p><b>Предусловия (Given):</b> Создатель с {@code telegramId = "1001"}, именем {@code "Хост"}.</p>
     *
     * <p><b>Действие (When):</b> Создается комната с {@code maxPlayers = 6}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>В списке участников содержится ровно один игрок с {@code isCreator = true}.</li>
     *   <li>Статус соединения создателя равен {@code ConnectionStatus.ONLINE}.</li>
     *   <li>Поле комнаты {@code creatorId} совпадает с {@code telegramId} создателя.</li>
     *   <li>Флаг {@code isReadyToStart} равен {@code false} (так как для старта требуется минимум 4 участника).</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Создатель добавляется первым игроком с флагами isCreator=true, connectionStatus=ONLINE и isReadyToStart=false")
    void createRoom_CreatorAddedAsHostAndFirstPlayer() {
        TelegramUser creator = TelegramUser.of("1001", "host_nick", "Хост");
        CreateRoomRequest request = new CreateRoomRequest("Тестовая комната", 6);

        when(roomJpaRepository.findActiveByCreatorId("1001")).thenReturn(Collections.emptyList());
        when(codeGenerator.generateCode()).thenReturn("HOST01");
        when(roomJpaRepository.save(any(RoomEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomResponse response = roomService.createRoom(creator, request);

        assertThat(response.creatorId()).isEqualTo("1001");
        assertThat(response.isReadyToStart()).isFalse();
        assertThat(response.players()).hasSize(1);

        var player = response.players().get(0);
        assertThat(player.getTelegramId()).isEqualTo("1001");
        assertThat(player.getDisplayName()).isEqualTo("Хост");
        assertThat(player.isCreator()).isTrue();
        assertThat(player.getConnectionStatus()).isEqualTo(ConnectionStatus.ONLINE);
    }

    /**
     * Проверяет граничные значения длины названия комнаты (1 символ, 32 символа, обрезка пробелов).
     *
     * <p><b>Предусловия (Given):</b> Названия комнат на границах диапазона: 1 символ, 32 символа и с пробелами по краям.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code createRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Комната успешно создается.</li>
     *   <li>Название комнаты сохраняется без лишних пробелов по краям (trim).</li>
     * </ul>
     * </p>
     */
    @ParameterizedTest(name = "Граничное имя комнаты: \"{0}\"")
    @ValueSource(strings = {"A", "1", "12345678901234567890123456789012", "  Партия с пробелами  "})
    @DisplayName("Граничные значения имени комнаты: успешное создание при длине 1, 32 символа и обрезка пробелов")
    void createRoom_RoomNameBoundaries_Success(String roomName) {
        TelegramUser creator = TelegramUser.of("1002", "nick", "Игрок");
        CreateRoomRequest request = new CreateRoomRequest(roomName, 8);

        when(roomJpaRepository.findActiveByCreatorId("1002")).thenReturn(Collections.emptyList());
        when(codeGenerator.generateCode()).thenReturn("NAME01");

        ArgumentCaptor<RoomEntity> captor = ArgumentCaptor.forClass(RoomEntity.class);
        when(roomJpaRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        RoomResponse response = roomService.createRoom(creator, request);

        assertThat(response).isNotNull();
        assertThat(captor.getValue().getName()).isEqualTo(roomName.trim());
    }

    /**
     * Проверяет отказ в создании комнаты при недопустимых значениях maxPlayers (меньше 4 или больше 12).
     *
     * <p><b>Предусловия (Given):</b> Значения {@code maxPlayers} вне диапазона [4..12]: {-1, 0, 1, 2, 3, 13, 14, 100}.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code createRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code InvalidMaxPlayersException}, репозиторий {@code save} не вызывается.
     * </p>
     */
    @ParameterizedTest(name = "Недопустимый maxPlayers={0}")
    @ValueSource(ints = {-1, 0, 1, 2, 3, 13, 14, 100})
    @DisplayName("Ошибка валидации при maxPlayers < 4 или maxPlayers > 12")
    void createRoom_InvalidMaxPlayers_ThrowsException(int invalidMaxPlayers) {
        TelegramUser creator = TelegramUser.of("1003", "nick", "Игрок");
        CreateRoomRequest request = new CreateRoomRequest("Комната", invalidMaxPlayers);

        when(roomJpaRepository.findActiveByCreatorId("1003")).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> roomService.createRoom(creator, request))
                .isInstanceOf(InvalidMaxPlayersException.class);

        verify(roomJpaRepository, never()).save(any());
    }

    /**
     * Проверяет отказ в создании комнаты при пустом названии или превышении лимита 32 символа.
     *
     * <p><b>Предусловия (Given):</b> Пустые строки, пробельные строки или названия длиной более 32 символов.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code createRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code InvalidRoomSettingsException}.
     * </p>
     */
    @ParameterizedTest(name = "Невалидное имя комнаты: \"{0}\"")
    @ValueSource(strings = {"", "   ", "123456789012345678901234567890123", "Too long room name with more than thirty-two characters"})
    @DisplayName("Ошибка валидации при пустом имени комнаты или длине > 32")
    void createRoom_InvalidRoomName_ThrowsException(String invalidName) {
        TelegramUser creator = TelegramUser.of("1004", "nick", "Игрок");
        CreateRoomRequest request = new CreateRoomRequest(invalidName, 6);

        when(roomJpaRepository.findActiveByCreatorId("1004")).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> roomService.createRoom(creator, request))
                .isInstanceOf(InvalidRoomSettingsException.class);

        verify(roomJpaRepository, never()).save(any());
    }

    /**
     * Проверяет отказ в создании комнаты при некорректном имени создателя (пустое, >64 символов или XSS/HTML).
     *
     * <p><b>Предусловия (Given):</b> Имя создателя содержит запрещенные HTML-теги или пустое.</p>
     *
     * <p><b>Действие (When):</b> Вызывается метод {@code createRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code InvalidPlayerNameException}.
     * </p>
     */
    @ParameterizedTest(name = "Невалидное имя создателя: \"{0}\"")
    @ValueSource(strings = {"", "   ", "<script>alert(1)</script>", "<b>User</b>", "<img src=x onerror=alert(1)>"})
    @DisplayName("Ошибка валидации при некорректном displayName создателя")
    void createRoom_InvalidCreatorDisplayName_ThrowsException(String invalidDisplayName) {
        TelegramUser creator = new TelegramUser("1005", "nick", invalidDisplayName);
        CreateRoomRequest request = new CreateRoomRequest("Нормальное имя", 6);

        assertThatThrownBy(() -> roomService.createRoom(creator, request))
                .isInstanceOf(InvalidPlayerNameException.class);

        verify(roomJpaRepository, never()).save(any());
    }

    /**
     * Проверяет отказ в создании комнаты (409 ACTIVE_ROOM_ALREADY_EXISTS), если создатель
     * уже имеет другую активную комнату.
     *
     * <p><b>Предусловия (Given):</b> Репозиторий возвращает активную комнату для {@code telegramId = "1006"}.</p>
     *
     * <p><b>Действие (When):</b> Пользователь отправляет повторный запрос {@code createRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Выбрасывается исключение {@code ActiveRoomExistsException} с кодом ошибки {@code ACTIVE_ROOM_ALREADY_EXISTS}.
     * </p>
     */
    @Test
    @DisplayName("Отказ 409 ACTIVE_ROOM_ALREADY_EXISTS если у пользователя уже есть активная комната")
    void createRoom_CreatorAlreadyHasActiveRoom_ThrowsActiveRoomExistsException() {
        TelegramUser creator = TelegramUser.of("1006", "nick", "Создатель");
        CreateRoomRequest request = new CreateRoomRequest("Вторая комната", 6);

        RoomEntity existingRoom = new RoomEntity(UUID.randomUUID(), "OLD001", "Существующая",
                "1006", RoomStatus.LOBBY, (short) 6, Instant.now(), Instant.now(), Instant.now());

        when(roomJpaRepository.findActiveByCreatorId("1006")).thenReturn(List.of(existingRoom));

        assertThatThrownBy(() -> roomService.createRoom(creator, request))
                .isInstanceOf(ActiveRoomExistsException.class)
                .hasFieldOrPropertyWithValue("code", "ACTIVE_ROOM_ALREADY_EXISTS");

        verify(roomJpaRepository, never()).save(any());
    }
}
