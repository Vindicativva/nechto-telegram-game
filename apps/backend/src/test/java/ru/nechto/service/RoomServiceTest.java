package ru.nechto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.entity.RoomEntity;
import ru.nechto.exception.ActiveRoomExistsException;
import ru.nechto.exception.ForbiddenException;
import ru.nechto.exception.InvalidRoomCodeException;
import ru.nechto.exception.InvalidRoomSettingsException;
import ru.nechto.exception.RoomAlreadyStartedException;
import ru.nechto.exception.RoomFullException;
import ru.nechto.exception.RoomNotFoundException;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.model.RoomParticipant;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TelegramUser;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.websocket.RoomWebSocketBroadcaster;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RoomServiceTest {

    @Autowired
    private RoomJpaRepository roomJpaRepository;

    @Autowired
    private RoomService roomService;

    @MockitoBean
    private RoomWebSocketBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        roomJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Успешное создание комнаты с валидными параметрами")
    void createRoom_Success() {
        TelegramUser creator = TelegramUser.of("1001", "creator_nick", "Creator Name");
        CreateRoomRequest request = new CreateRoomRequest("Вечерняя партия", 8);

        RoomResponse response = roomService.createRoom(creator, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.code()).isNotNull().hasSize(6);
        assertThat(response.inviteLink()).isEqualTo("https://t.me/StayAwayGameBot/app?startapp=" + response.code());
        assertThat(response.name()).isEqualTo("Вечерняя партия");
        assertThat(response.creatorId()).isEqualTo("1001");
        assertThat(response.status()).isEqualTo(RoomStatus.LOBBY);
        assertThat(response.maxPlayers()).isEqualTo(8);
        assertThat(response.currentPlayersCount()).isEqualTo(1);
        assertThat(response.isReadyToStart()).isFalse();
        assertThat(response.players()).hasSize(1);

        RoomParticipant creatorParticipant = response.players().get(0);
        assertThat(creatorParticipant.getTelegramId()).isEqualTo("1001");
        assertThat(creatorParticipant.getUsername()).isEqualTo("creator_nick");
        assertThat(creatorParticipant.getDisplayName()).isEqualTo("Creator Name");
        assertThat(creatorParticipant.isCreator()).isTrue();
        assertThat(creatorParticipant.getConnectionStatus()).isEqualTo(ConnectionStatus.ONLINE);
        assertThat(creatorParticipant.getJoinedAt()).isNotNull();
        assertThat(creatorParticipant.getDisconnectedAt()).isNull();

        Optional<RoomEntity> stored = roomJpaRepository.findByCodeWithParticipants(response.code());
        assertThat(stored).isPresent();
    }

    @Test
    @DisplayName("Ошибка при создании комнаты, если у создателя уже есть активная комната")
    void createRoom_WhenActiveRoomExists_ThrowsException() {
        TelegramUser creator = TelegramUser.of("1001", "creator_nick", "Creator Name");
        CreateRoomRequest request1 = new CreateRoomRequest("Комната 1", 6);
        roomService.createRoom(creator, request1);

        CreateRoomRequest request2 = new CreateRoomRequest("Комната 2", 8);
        assertThatThrownBy(() -> roomService.createRoom(creator, request2))
                .isInstanceOf(ActiveRoomExistsException.class);
    }

    @Test
    @DisplayName("Ошибка при создании комнаты с недопустимой длиной названия")
    void createRoom_InvalidName_ThrowsException() {
        TelegramUser creator = TelegramUser.of("1001", "creator_nick", "Creator Name");

        CreateRoomRequest emptyNameRequest = new CreateRoomRequest("   ", 6);
        assertThatThrownBy(() -> roomService.createRoom(creator, emptyNameRequest))
                .isInstanceOf(InvalidRoomSettingsException.class);

        CreateRoomRequest tooLongNameRequest = new CreateRoomRequest("A".repeat(33), 6);
        assertThatThrownBy(() -> roomService.createRoom(creator, tooLongNameRequest))
                .isInstanceOf(InvalidRoomSettingsException.class);
    }

    @Test
    @DisplayName("Ошибка при создании комнаты с недопустимым maxPlayers (< 4 или > 12)")
    void createRoom_InvalidMaxPlayers_ThrowsException() {
        TelegramUser creator = TelegramUser.of("1001", "creator_nick", "Creator Name");

        CreateRoomRequest tooFewPlayers = new CreateRoomRequest("Партия", 3);
        assertThatThrownBy(() -> roomService.createRoom(creator, tooFewPlayers))
                .isInstanceOf(InvalidRoomSettingsException.class);

        CreateRoomRequest tooManyPlayers = new CreateRoomRequest("Партия", 13);
        assertThatThrownBy(() -> roomService.createRoom(creator, tooManyPlayers))
                .isInstanceOf(InvalidRoomSettingsException.class);
    }

    @Test
    @DisplayName("Получение состояния комнаты по коду (регистронезависимо)")
    void getRoomByCode_Success() {
        TelegramUser creator = TelegramUser.of("1001", "creator_nick", "Creator");
        RoomResponse created = roomService.createRoom(creator, new CreateRoomRequest("Тест", 6));

        RoomResponse fetched = roomService.getRoomByCode(created.code().toLowerCase());
        assertThat(fetched).isNotNull();
        assertThat(fetched.code()).isEqualTo(created.code());
        assertThat(fetched.name()).isEqualTo("Тест");
    }

    @Test
    @DisplayName("Получение комнаты по несуществующему коду выбрасывает RoomNotFoundException (404)")
    void getRoomByCode_NotFound_ThrowsException() {
        assertThatThrownBy(() -> roomService.getRoomByCode("NONEXIST"))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    @DisplayName("Подключение новых игроков пересчитывает currentPlayersCount и выставляет isReadyToStart при 4 игроках")
    void joinRoom_Success_AndReadyToStartAt4Players() {
        TelegramUser creator = TelegramUser.of("1001", "creator", "Creator");
        RoomResponse room = roomService.createRoom(creator, new CreateRoomRequest("Партия", 6));

        assertThat(room.isReadyToStart()).isFalse();

        // 2nd player joins
        TelegramUser player2 = TelegramUser.of("1002", "player2", "Player Two");
        RoomResponse r2 = roomService.joinRoom(room.code(), player2);
        assertThat(r2.currentPlayersCount()).isEqualTo(2);
        assertThat(r2.isReadyToStart()).isFalse();

        // 3rd player joins
        TelegramUser player3 = TelegramUser.of("1003", "player3", "Player Three");
        RoomResponse r3 = roomService.joinRoom(room.code(), player3);
        assertThat(r3.currentPlayersCount()).isEqualTo(3);
        assertThat(r3.isReadyToStart()).isFalse();

        // 4th player joins -> isReadyToStart becomes true!
        TelegramUser player4 = TelegramUser.of("1004", "player4", "Player Four");
        RoomResponse r4 = roomService.joinRoom(room.code(), player4);
        assertThat(r4.currentPlayersCount()).isEqualTo(4);
        assertThat(r4.isReadyToStart()).isTrue();
    }

    @Test
    @DisplayName("Повторное подключение игрока идемпотентно, не создает дубликатов")
    void joinRoom_IdempotentReconnection_DoesNotDuplicate() {
        TelegramUser creator = TelegramUser.of("1001", "creator", "Creator");
        RoomResponse room = roomService.createRoom(creator, new CreateRoomRequest("Партия", 6));

        TelegramUser player2 = TelegramUser.of("1002", "player2", "Player Two");
        roomService.joinRoom(room.code(), player2);

        // Reconnect player2
        RoomResponse reconnected = roomService.joinRoom(room.code(), player2);
        assertThat(reconnected.currentPlayersCount()).isEqualTo(2);
        assertThat(reconnected.players()).hasSize(2);

        // Reconnect creator
        RoomResponse reconnectedCreator = roomService.joinRoom(room.code(), creator);
        assertThat(reconnectedCreator.currentPlayersCount()).isEqualTo(2);
        assertThat(reconnectedCreator.players()).hasSize(2);
    }

    @Test
    @DisplayName("Отказ в подключении к заполненной комнате (ROOM_FULL)")
    void joinRoom_WhenFull_ThrowsRoomFullException() {
        TelegramUser creator = TelegramUser.of("1001", "creator", "Creator");
        RoomResponse room = roomService.createRoom(creator, new CreateRoomRequest("Партия", 4));

        roomService.joinRoom(room.code(), TelegramUser.of("1002", "p2", "P2"));
        roomService.joinRoom(room.code(), TelegramUser.of("1003", "p3", "P3"));
        roomService.joinRoom(room.code(), TelegramUser.of("1004", "p4", "P4"));

        // 5th player tries to join room with maxPlayers = 4
        TelegramUser player5 = TelegramUser.of("1005", "p5", "P5");
        assertThatThrownBy(() -> roomService.joinRoom(room.code(), player5))
                .isInstanceOf(RoomFullException.class);
    }

    @Test
    @DisplayName("Отказ в подключении к комнате, если игра уже началась (ROOM_ALREADY_STARTED)")
    void joinRoom_WhenAlreadyStarted_ThrowsException() {
        TelegramUser creator = TelegramUser.of("1001", "creator", "Creator");
        RoomResponse room = roomService.createRoom(creator, new CreateRoomRequest("Партия", 6));

        // Mark room as IN_PROGRESS
        RoomEntity rawRoom = roomJpaRepository.findByCode(room.code()).orElseThrow();
        rawRoom.setStatus(RoomStatus.IN_PROGRESS);
        roomJpaRepository.save(rawRoom);

        TelegramUser player2 = TelegramUser.of("1002", "p2", "P2");
        assertThatThrownBy(() -> roomService.joinRoom(room.code(), player2))
                .isInstanceOf(RoomAlreadyStartedException.class);
    }

    @Test
    @DisplayName("При выходе обычного участника он удаляется из комнаты")
    void leaveRoom_RegularParticipant() {
        TelegramUser creator = TelegramUser.of("1001", "creator", "Creator");
        RoomResponse room = roomService.createRoom(creator, new CreateRoomRequest("Партия", 6));
        roomService.joinRoom(room.code(), TelegramUser.of("1002", "p2", "P2"));

        RoomResponse afterLeave = roomService.leaveRoom(room.code(), "1002");
        assertThat(afterLeave.currentPlayersCount()).isEqualTo(1);
        assertThat(afterLeave.players()).extracting(RoomParticipant::getTelegramId).containsExactly("1001");
        assertThat(afterLeave.creatorId()).isEqualTo("1001");
    }

    @Test
    @DisplayName("При выходе создателя права переходят участнику с самым ранним joinedAt")
    void leaveRoom_CreatorLeaves_HostMigratedToEarliestJoined() throws Exception {
        TelegramUser creator = TelegramUser.of("1001", "creator", "Creator");
        RoomResponse room = roomService.createRoom(creator, new CreateRoomRequest("Партия", 6));

        Thread.sleep(10);
        TelegramUser player2 = TelegramUser.of("1002", "p2", "P2");
        roomService.joinRoom(room.code(), player2);

        Thread.sleep(10);
        TelegramUser player3 = TelegramUser.of("1003", "p3", "P3");
        roomService.joinRoom(room.code(), player3);

        // Creator leaves
        RoomResponse afterCreatorLeaves = roomService.leaveRoom(room.code(), "1001");

        assertThat(afterCreatorLeaves.currentPlayersCount()).isEqualTo(2);
        assertThat(afterCreatorLeaves.creatorId()).isEqualTo("1002");

        RoomParticipant newHost = afterCreatorLeaves.players().stream()
                .filter(p -> "1002".equals(p.getTelegramId()))
                .findFirst().orElseThrow();
        assertThat(newHost.isCreator()).isTrue();

        RoomParticipant otherPlayer = afterCreatorLeaves.players().stream()
                .filter(p -> "1003".equals(p.getTelegramId()))
                .findFirst().orElseThrow();
        assertThat(otherPlayer.isCreator()).isFalse();
    }

    @Test
    @DisplayName("Обновление настроек создателем комнаты")
    void updateSettings_Success() {
        TelegramUser creator = TelegramUser.of("1001", "creator", "Creator");
        RoomResponse room = roomService.createRoom(creator, new CreateRoomRequest("Партия", 6));

        RoomResponse updated = roomService.updateSettings(room.code(), "1001", 10);
        assertThat(updated.maxPlayers()).isEqualTo(10);
    }

    @Test
    @DisplayName("Попытка изменения настроек не создателем вызывает ForbiddenException (403)")
    void updateSettings_ByNonCreator_ThrowsForbidden() {
        TelegramUser creator = TelegramUser.of("1001", "creator", "Creator");
        RoomResponse room = roomService.createRoom(creator, new CreateRoomRequest("Партия", 6));
        roomService.joinRoom(room.code(), TelegramUser.of("1002", "p2", "P2"));

        assertThatThrownBy(() -> roomService.updateSettings(room.code(), "1002", 8))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Попытка уменьшить maxPlayers ниже текущего числа участников вызывает ошибку")
    void updateSettings_LessThanCurrentPlayers_ThrowsException() {
        TelegramUser creator = TelegramUser.of("1001", "creator", "Creator");
        RoomResponse room = roomService.createRoom(creator, new CreateRoomRequest("Партия", 6));
        roomService.joinRoom(room.code(), TelegramUser.of("1002", "p2", "P2"));
        roomService.joinRoom(room.code(), TelegramUser.of("1003", "p3", "P3"));
        roomService.joinRoom(room.code(), TelegramUser.of("1004", "p4", "P4"));
        roomService.joinRoom(room.code(), TelegramUser.of("1005", "p5", "P5")); // 5 players

        assertThatThrownBy(() -> roomService.updateSettings(room.code(), "1001", 4))
                .isInstanceOf(InvalidRoomSettingsException.class);
    }

    @Test
    @DisplayName("Невалидный код комнаты выбрасывает InvalidRoomCodeException")
    void invalidRoomCode_ThrowsException() {
        assertThatThrownBy(() -> roomService.getRoomByCode("inv!@#"))
                .isInstanceOf(InvalidRoomCodeException.class);
    }
}
