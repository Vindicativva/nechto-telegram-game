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
import ru.nechto.model.TelegramUser;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.service.RoomService;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты для HTTP-эндпоинтов POST /api/rooms и GET /api/rooms/{code}.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoomControllerApiRoomsTest {

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
     * Проверяет успешное создание комнаты через HTTP-эндпоинт {@code POST /api/rooms}
     * с валидными параметрами имени (1–32 символа) и вместимости (4–12 человек).
     *
     * <p><b>Предусловия (Given):</b> В заголовках переданы валидные данные хоста
     * ({@code X-Telegram-Id: 9001}), а в теле запроса — название {@code "Партия в Нечто"}
     * и лимит игроков {@code maxPlayers = 6}.</p>
     *
     * <p><b>Действие (When):</b> Выполняется HTTP-запрос {@code POST /api/rooms}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Возвращается HTTP-статус {@code 201 Created}.</li>
     *   <li>В теле ответа возвращается сгенерированный 6-значный код комнаты.</li>
     *   <li>Лимит игроков равен 6, статус равен {@code LOBBY}.</li>
     *   <li>Хост зарегистрирован в списке участников.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("POST /api/rooms - Успешное создание комнаты с кодом 201 Created")
    void createRoom_ValidParams_Returns201Created() throws Exception {
        String requestBody = """
                {
                    "name": "Партия в Нечто",
                    "maxPlayers": 6
                }
                """;

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "9001")
                        .header("X-Telegram-Username", "host_player")
                        .header("X-Telegram-Display-Name", "Хост")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Партия в Нечто"))
                .andExpect(jsonPath("$.maxPlayers").value(6))
                .andExpect(jsonPath("$.status").value("LOBBY"))
                .andExpect(jsonPath("$.currentPlayersCount").value(1))
                .andExpect(jsonPath("$.players", hasSize(1)))
                .andExpect(jsonPath("$.players[0].telegramId").value("9001"))
                .andExpect(jsonPath("$.players[0].isCreator").value(true));
    }

    /**
     * Проверяет серверную валидацию имени комнаты: отказ с кодом 400 при пустом имени.
     *
     * <p><b>Предусловия (Given):</b> В теле запроса передано пустое имя комнаты {@code " "}.</p>
     *
     * <p><b>Действие (When):</b> Выполняется HTTP-запрос {@code POST /api/rooms}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Возвращается HTTP-статус {@code 400 Bad Request}.</li>
     *   <li>Возвращается унифицированный код ошибки {@code "VALIDATION_ERROR"}.</li>
     *   <li>В деталях ошибки указано поле {@code name}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("POST /api/rooms - Отказ 400 при пустом имени комнаты")
    void createRoom_EmptyName_Returns400ValidationError() throws Exception {
        String requestBody = """
                {
                    "name": "   ",
                    "maxPlayers": 6
                }
                """;

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "9002")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/rooms"));
    }

    /**
     * Проверяет серверную валидацию длины имени комнаты: отказ с кодом 400 при длине более 32 символов.
     *
     * <p><b>Предусловия (Given):</b> В теле запроса передано имя длиной 33 символа
     * ({@code "123456789012345678901234567890123"}).</p>
     *
     * <p><b>Действие (When):</b> Выполняется HTTP-запрос {@code POST /api/rooms}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Возвращается HTTP-статус {@code 400 Bad Request}.</li>
     *   <li>Возвращается унифицированная ошибка {@code "VALIDATION_ERROR"}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("POST /api/rooms - Отказ 400 при длине имени более 32 символов")
    void createRoom_NameExceeds32Chars_Returns400ValidationError() throws Exception {
        String requestBody = """
                {
                    "name": "123456789012345678901234567890123",
                    "maxPlayers": 6
                }
                """;

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "9003")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /**
     * Проверяет серверную валидацию вместимости комнаты: отказ с кодом 400 при {@code maxPlayers < 4}.
     *
     * <p><b>Предусловия (Given):</b> В теле запроса передано {@code maxPlayers = 3} (ниже минимума).</p>
     *
     * <p><b>Действие (When):</b> Выполняется HTTP-запрос {@code POST /api/rooms}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Возвращается HTTP-статус {@code 400 Bad Request}.</li>
     *   <li>Возвращается код ошибки {@code "VALIDATION_ERROR"}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("POST /api/rooms - Отказ 400 при maxPlayers меньше 4")
    void createRoom_MaxPlayersLessThan4_Returns400ValidationError() throws Exception {
        String requestBody = """
                {
                    "name": "Мало игроков",
                    "maxPlayers": 3
                }
                """;

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "9004")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /**
     * Проверяет серверную валидацию вместимости комнаты: отказ с кодом 400 при {@code maxPlayers > 12}.
     *
     * <p><b>Предусловия (Given):</b> В теле запроса передано {@code maxPlayers = 13} (выше максимума).</p>
     *
     * <p><b>Действие (When):</b> Выполняется HTTP-запрос {@code POST /api/rooms}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Возвращается HTTP-статус {@code 400 Bad Request}.</li>
     *   <li>Возвращается код ошибки {@code "VALIDATION_ERROR"}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("POST /api/rooms - Отказ 400 при maxPlayers больше 12")
    void createRoom_MaxPlayersGreaterThan12_Returns400ValidationError() throws Exception {
        String requestBody = """
                {
                    "name": "Много игроков",
                    "maxPlayers": 13
                }
                """;

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "9005")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /**
     * Проверяет отказ 401 UNAUTHORIZED при отсутствии идентификатора пользователя Telegram.
     *
     * <p><b>Предусловия (Given):</b> Запрос на создание комнаты отправлен без заголовков
     * и без полей аутентификации Telegram в теле.</p>
     *
     * <p><b>Действие (When):</b> Выполняется HTTP-запрос {@code POST /api/rooms}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Возвращается HTTP-статус {@code 401 Unauthorized}.</li>
     *   <li>Возвращается код ошибки {@code "UNAUTHORIZED"}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("POST /api/rooms - Отказ 401 при отсутствии данных Telegram пользователя")
    void createRoom_MissingTelegramUser_Returns401Unauthorized() throws Exception {
        String requestBody = """
                {
                    "name": "Анонимная комната",
                    "maxPlayers": 6
                }
                """;

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    /**
     * Проверяет получение информации о существующей комнате и её доступности
     * через эндпоинт {@code GET /api/rooms/{code}}.
     *
     * <p><b>Предусловия (Given):</b> В системе создана активная комната с кодом {@code "EXST01"}.</p>
     *
     * <p><b>Действие (When):</b> Выполняется HTTP-запрос {@code GET /api/rooms/EXST01}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Возвращается HTTP-статус {@code 200 OK}.</li>
     *   <li>В ответе содержатся данные комнаты: код, название, статус {@code LOBBY},
     *       текущее количество игроков и флаг {@code isReadyToStart}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("GET /api/rooms/{code} - Успешное получение информации о существующей комнате")
    void getRoom_ExistingCode_Returns200OK() throws Exception {
        TelegramUser host = TelegramUser.of("9010", "host_nick", "ХостИгрок");
        CreateRoomRequest request = new CreateRoomRequest("Комната для проверки", 8);
        RoomResponse created = roomService.createRoom(host, request);

        mockMvc.perform(get("/api/rooms/" + created.code()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(created.code()))
                .andExpect(jsonPath("$.name").value("Комната для проверки"))
                .andExpect(jsonPath("$.maxPlayers").value(8))
                .andExpect(jsonPath("$.status").value("LOBBY"))
                .andExpect(jsonPath("$.currentPlayersCount").value(1))
                .andExpect(jsonPath("$.isReadyToStart").value(false));
    }

    /**
     * Проверяет возврат ошибки 404 NOT FOUND при запросе несуществующей комнаты
     * через эндпоинт {@code GET /api/rooms/{code}}.
     *
     * <p><b>Предусловия (Given):</b> В системе отсутствует комната с кодом {@code "NOTF01"}.</p>
     *
     * <p><b>Действие (When):</b> Выполняется HTTP-запрос {@code GET /api/rooms/NOTF01}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Возвращается HTTP-статус {@code 404 Not Found}.</li>
     *   <li>Возвращается унифицированный код ошибки {@code "ROOM_NOT_FOUND"}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("GET /api/rooms/{code} - Отказ 404 при запросе несуществующей комнаты")
    void getRoom_NonExistingCode_Returns404NotFound() throws Exception {
        mockMvc.perform(get("/api/rooms/NOTF01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/rooms/NOTF01"));
    }

    /**
     * Проверяет возврат ошибки 400 BAD REQUEST при передаче некорректного формата кода
     * через эндпоинт {@code GET /api/rooms/{code}}.
     *
     * <p><b>Предусловия (Given):</b> Передан заведомо некорректный код длиной более 12 символов.</p>
     *
     * <p><b>Действие (When):</b> Выполняется HTTP-запрос {@code GET /api/rooms/TOOLONGROOMCODE123}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Возвращается HTTP-статус {@code 400 Bad Request}.</li>
     *   <li>Возвращается унифицированный код ошибки {@code "INVALID_ROOM_CODE"}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("GET /api/rooms/{code} - Отказ 400 при некорректном формате кода комнаты")
    void getRoom_InvalidCodeFormat_Returns400BadRequest() throws Exception {
        mockMvc.perform(get("/api/rooms/TOOLONGROOMCODE123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ROOM_CODE"));
    }

    /**
     * Проверяет конфликт 409 ACTIVE_ROOM_ALREADY_EXISTS при попытке пользователя создать
     * вторую активную комнату.
     *
     * <p><b>Предусловия (Given):</b> Пользователь с Telegram ID {@code "9020"} уже создал
     * одну активную комнату.</p>
     *
     * <p><b>Действие (When):</b> Тот же пользователь отправляет повторный запрос {@code POST /api/rooms}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Возвращается HTTP-статус {@code 409 Conflict}.</li>
     *   <li>Возвращается унифицированный код ошибки {@code "ACTIVE_ROOM_ALREADY_EXISTS"}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("POST /api/rooms - Отказ 409 при наличии уже активной комнаты у создателя")
    void createRoom_ActiveRoomAlreadyExists_Returns409Conflict() throws Exception {
        TelegramUser host = TelegramUser.of("9020", "user20", "User 20");
        roomService.createRoom(host, new CreateRoomRequest("Первая комната", 6));

        String requestBody = """
                {
                    "name": "Вторая комната",
                    "maxPlayers": 6
                }
                """;

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "9020")
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_ROOM_ALREADY_EXISTS"));
    }
}
