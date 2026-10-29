package ru.nechto.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.entity.RoomEntity;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TelegramUser;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.service.RoomService;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты REST контроллера RoomController.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoomControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RoomJpaRepository roomJpaRepository;

    @Autowired
    private RoomService roomService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        roomJpaRepository.deleteAll();
    }

    /**
     * Проверяет успешное создание комнаты через POST /api/v1/rooms с заголовками Telegram.
     */
    @Test
    @DisplayName("POST /api/v1/rooms - Успешное создание комнаты (201 Created)")
    void createRoom_Success() throws Exception {
        String requestJson = """
                {
                    "name": "Вечерняя партия",
                    "maxPlayers": 8
                }
                """;

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "123456789")
                        .header("X-Telegram-Username", "ivan_player")
                        .header("X-Telegram-Display-Name", "Иван")
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.inviteLink").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Вечерняя партия"))
                .andExpect(jsonPath("$.creatorId").value("123456789"))
                .andExpect(jsonPath("$.status").value("LOBBY"))
                .andExpect(jsonPath("$.maxPlayers").value(8))
                .andExpect(jsonPath("$.currentPlayersCount").value(1))
                .andExpect(jsonPath("$.isReadyToStart").value(false))
                .andExpect(jsonPath("$.players", hasSize(1)))
                .andExpect(jsonPath("$.players[0].telegramId").value("123456789"))
                .andExpect(jsonPath("$.players[0].username").value("ivan_player"))
                .andExpect(jsonPath("$.players[0].displayName").value("Иван"))
                .andExpect(jsonPath("$.players[0].isCreator").value(true))
                .andExpect(jsonPath("$.players[0].connectionStatus").value("ONLINE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    /**
     * Проверяет отказ 400 при передаче maxPlayers вне диапазона 4..12.
     */
    @Test
    @DisplayName("POST /api/v1/rooms - Ошибка 400 Bad Request при maxPlayers вне диапазона 4..12")
    void createRoom_InvalidMaxPlayers_Returns400() throws Exception {
        String requestJsonTooFew = """
                {
                    "name": "Комната",
                    "maxPlayers": 3
                }
                """;

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "123456789")
                        .content(requestJsonTooFew))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        String requestJsonTooMany = """
                {
                    "name": "Комната",
                    "maxPlayers": 13
                }
                """;

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "123456789")
                        .content(requestJsonTooMany))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /**
     * Проверяет отказ 400 при пустом названии или превышении лимита 32 символа.
     */
    @Test
    @DisplayName("POST /api/v1/rooms - Ошибка 400 Bad Request при пустом имени или длине > 32")
    void createRoom_InvalidName_Returns400() throws Exception {
        String requestJsonEmpty = """
                {
                    "name": "",
                    "maxPlayers": 8
                }
                """;

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "123456789")
                        .content(requestJsonEmpty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        String requestJsonTooLong = String.format("""
                {
                    "name": "%s",
                    "maxPlayers": 8
                }
                """, "A".repeat(33));

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "123456789")
                        .content(requestJsonTooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /**
     * Проверяет отказ 401 при отсутствии данных Telegram пользователя.
     */
    @Test
    @DisplayName("POST /api/v1/rooms - Ошибка 401 Unauthorized если данные пользователя отсутствуют")
    void createRoom_MissingTelegramUser_Returns401() throws Exception {
        String requestJson = """
                {
                    "name": "Вечерняя партия",
                    "maxPlayers": 8
                }
                """;

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    /**
     * Проверяет отказ 409 ACTIVE_ROOM_ALREADY_EXISTS, если у создателя уже есть активная комната.
     */
    @Test
    @DisplayName("POST /api/v1/rooms - Ошибка 409 Conflict если у пользователя уже есть активная комната")
    void createRoom_ActiveRoomExists_Returns409() throws Exception {
        String requestJson = """
                {
                    "name": "Партия 1",
                    "maxPlayers": 8
                }
                """;

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "111")
                        .content(requestJson))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "111")
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_ROOM_ALREADY_EXISTS"));
    }

    /**
     * Проверяет получение состояния комнаты по коду.
     */
    @Test
    @DisplayName("GET /api/v1/rooms/{code} - Успешное получение информации о комнате")
    void getRoom_Success() throws Exception {
        RoomResponse created = roomService.createRoom(
                TelegramUser.of("123", "ivan", "Иван"),
                new CreateRoomRequest("Тест", 6)
        );

        mockMvc.perform(get("/api/v1/rooms/" + created.code()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(created.code()))
                .andExpect(jsonPath("$.name").value("Тест"))
                .andExpect(jsonPath("$.currentPlayersCount").value(1));
    }

    /**
     * Проверяет 404 ROOM_NOT_FOUND при запросе несуществующей комнаты.
     */
    @Test
    @DisplayName("GET /api/v1/rooms/{code} - 404 Not Found при запросе несуществующей комнаты")
    void getRoom_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Комната не найдена или недоступна"));
    }

    /**
     * Проверяет подключение нового игрока к комнате (200 OK).
     */
    @Test
    @DisplayName("POST /api/v1/rooms/{code}/join - Подключение нового игрока (200 OK)")
    void joinRoom_Success() throws Exception {
        RoomResponse created = roomService.createRoom(
                TelegramUser.of("123", "ivan", "Иван"),
                new CreateRoomRequest("Тест", 6)
        );

        String joinBody = """
                {
                    "telegramId": "987654321",
                    "username": "alex_g",
                    "displayName": "Алексей"
                }
                """;

        mockMvc.perform(post("/api/v1/rooms/" + created.code() + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlayersCount").value(2))
                .andExpect(jsonPath("$.players", hasSize(2)))
                .andExpect(jsonPath("$.players[1].telegramId").value("987654321"))
                .andExpect(jsonPath("$.players[1].displayName").value("Алексей"))
                .andExpect(jsonPath("$.players[1].isCreator").value(false));
    }

    /**
     * Проверяет идемпотентность повторного подключения игрока.
     */
    @Test
    @DisplayName("POST /api/v1/rooms/{code}/join - Повторное подключение идемпотентно (200 OK)")
    void joinRoom_Idempotent_Returns200WithoutDuplicates() throws Exception {
        RoomResponse created = roomService.createRoom(
                TelegramUser.of("123", "ivan", "Иван"),
                new CreateRoomRequest("Тест", 6)
        );

        String joinBody = """
                {
                    "telegramId": "987654321",
                    "username": "alex_g",
                    "displayName": "Алексей"
                }
                """;

        // First join
        mockMvc.perform(post("/api/v1/rooms/" + created.code() + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlayersCount").value(2));

        // Reconnect
        mockMvc.perform(post("/api/v1/rooms/" + created.code() + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlayersCount").value(2))
                .andExpect(jsonPath("$.players", hasSize(2)));
    }

    /**
     * Проверяет отказ 409 ROOM_FULL при входе в заполненную комнату.
     */
    @Test
    @DisplayName("POST /api/v1/rooms/{code}/join - 409 Conflict ROOM_FULL при заполненной комнате")
    void joinRoom_Full_Returns409() throws Exception {
        RoomResponse created = roomService.createRoom(
                TelegramUser.of("1", "u1", "P1"),
                new CreateRoomRequest("Тест", 4)
        );
        roomService.joinRoom(created.code(), TelegramUser.of("2", "u2", "P2"));
        roomService.joinRoom(created.code(), TelegramUser.of("3", "u3", "P3"));
        roomService.joinRoom(created.code(), TelegramUser.of("4", "u4", "P4"));

        String joinBody = """
                {
                    "telegramId": "5",
                    "displayName": "P5"
                }
                """;

        mockMvc.perform(post("/api/v1/rooms/" + created.code() + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ROOM_FULL"))
                .andExpect(jsonPath("$.error.message").value("Комната заполнена"));
    }

    /**
     * Проверяет отказ 409 ROOM_ALREADY_STARTED, если игра уже началась.
     */
    @Test
    @DisplayName("POST /api/v1/rooms/{code}/join - 409 Conflict ROOM_ALREADY_STARTED если игра уже началась")
    void joinRoom_AlreadyStarted_Returns409() throws Exception {
        RoomResponse created = roomService.createRoom(
                TelegramUser.of("1", "u1", "P1"),
                new CreateRoomRequest("Тест", 6)
        );

        RoomEntity entity = roomJpaRepository.findByCode(created.code()).orElseThrow();
        entity.setStatus(RoomStatus.IN_PROGRESS);
        roomJpaRepository.save(entity);

        String joinBody = """
                {
                    "telegramId": "2",
                    "displayName": "P2"
                }
                """;

        mockMvc.perform(post("/api/v1/rooms/" + created.code() + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ROOM_ALREADY_STARTED"))
                .andExpect(jsonPath("$.error.message").value("Игра уже началась, подключение невозможно"));
    }

    /**
     * Проверяет 404 ROOM_NOT_FOUND при входе по неверному коду.
     */
    @Test
    @DisplayName("POST /api/v1/rooms/{code}/join - 404 ROOM_NOT_FOUND при неверном коде")
    void joinRoom_NotFound_Returns404() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/BADCODE/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_FOUND"));
    }

    /**
     * Проверяет выход создателя и автоматическую смену хоста.
     */
    @Test
    @DisplayName("POST /api/v1/rooms/{code}/leave - Выход и смена хоста")
    void leaveRoom_Success() throws Exception {
        RoomResponse created = roomService.createRoom(
                TelegramUser.of("1", "u1", "P1"),
                new CreateRoomRequest("Тест", 6)
        );
        roomService.joinRoom(created.code(), TelegramUser.of("2", "u2", "P2"));

        mockMvc.perform(post("/api/v1/rooms/" + created.code() + "/leave")
                        .header("X-Telegram-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlayersCount").value(1))
                .andExpect(jsonPath("$.creatorId").value("2"))
                .andExpect(jsonPath("$.players[0].telegramId").value("2"))
                .andExpect(jsonPath("$.players[0].isCreator").value(true));
    }

    /**
     * Проверяет изменение настроек создателем комнаты.
     */
    @Test
    @DisplayName("PATCH /api/v1/rooms/{code}/settings - Изменение настроек создателем")
    void updateSettings_Success() throws Exception {
        RoomResponse created = roomService.createRoom(
                TelegramUser.of("1", "u1", "P1"),
                new CreateRoomRequest("Тест", 6)
        );

        String settingsJson = """
                {
                    "maxPlayers": 10
                }
                """;

        mockMvc.perform(patch("/api/v1/rooms/" + created.code() + "/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "1")
                        .content(settingsJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPlayers").value(10));
    }

    /**
     * Проверяет отказ 403 FORBIDDEN_NOT_CREATOR, если настройки изменяет не создатель.
     */
    @Test
    @DisplayName("PATCH /api/v1/rooms/{code}/settings - 403 Forbidden если изменяет не создатель")
    void updateSettings_NotCreator_Returns403() throws Exception {
        RoomResponse created = roomService.createRoom(
                TelegramUser.of("1", "u1", "P1"),
                new CreateRoomRequest("Тест", 6)
        );
        roomService.joinRoom(created.code(), TelegramUser.of("2", "u2", "P2"));

        String settingsJson = """
                {
                    "maxPlayers": 10
                }
                """;

        mockMvc.perform(patch("/api/v1/rooms/" + created.code() + "/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "2")
                        .content(settingsJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_NOT_CREATOR"));
    }
}
