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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные тесты REST API для запуска игры через POST /api/rooms/{code}/start.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoomStartGameIntegrationTest {

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
     * Проверяет успешный запуск игры через эндпоинт {@code POST /api/rooms/{code}/start}
     * создателем комнаты при наличии 4 участников.
     *
     * <p><b>Предусловия (Given):</b> В базе создана комната с 4 зарегистрированными игроками.
     * Запрос отправляет создатель ({@code X-Telegram-Id: 1001}).</p>
     *
     * <p><b>Действие (When):</b> Выполняется HTTP-запрос {@code POST /api/rooms/{code}/start}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Сервер возвращает HTTP-статус {@code 200 OK}.</li>
     *   <li>В ответе поле {@code status} равно {@code IN_PROGRESS}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("POST /api/rooms/{code}/start - Успешный старт игры при 4 участниках (200 OK)")
    void startGame_SuccessWith4Players() throws Exception {
        TelegramUser host = TelegramUser.of("1001", "host", "Хост");
        RoomResponse created = roomService.createRoom(host, new CreateRoomRequest("Старт через API", 6));

        roomService.joinRoom(created.code(), TelegramUser.of("1002", "p2", "Игрок 2"));
        roomService.joinRoom(created.code(), TelegramUser.of("1003", "p3", "Игрок 3"));
        roomService.joinRoom(created.code(), TelegramUser.of("1004", "p4", "Игрок 4"));

        mockMvc.perform(post("/api/rooms/" + created.code() + "/start")
                        .header("X-Telegram-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.code").value(created.code()));
    }

    /**
     * Проверяет отказ 409 Conflict с кодом {@code NOT_ENOUGH_PLAYERS} при попытке
     * запуска игры, когда в комнате менее 4 участников.
     *
     * <p><b>Предусловия (Given):</b> В комнате зарегистрированы только 2 игрока.</p>
     *
     * <p><b>Действие (When):</b> Хост отправляет запрос {@code POST /api/rooms/{code}/start}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Сервер возвращает HTTP-статус {@code 409 Conflict}.</li>
     *   <li>В теле ответа содержится код ошибки {@code "NOT_ENOUGH_PLAYERS"}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("POST /api/rooms/{code}/start - Отказ 409 при количестве игроков менее 4")
    void startGame_LessThan4Players_Returns409NotEnoughPlayers() throws Exception {
        TelegramUser host = TelegramUser.of("2001", "host2", "Хост 2");
        RoomResponse created = roomService.createRoom(host, new CreateRoomRequest("Неполное лобби", 6));

        roomService.joinRoom(created.code(), TelegramUser.of("2002", "p2", "Игрок 2"));

        mockMvc.perform(post("/api/rooms/" + created.code() + "/start")
                        .header("X-Telegram-Id", "2001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NOT_ENOUGH_PLAYERS"));
    }

    /**
     * Проверяет отказ 403 Forbidden с кодом {@code FORBIDDEN_NOT_CREATOR} при попытке
     * запуска игры участником, не являющимся создателем комнаты.
     *
     * <p><b>Предусловия (Given):</b> В комнате 4 игрока. Запрос на старт отправляет не хост (ID "3002").</p>
     *
     * <p><b>Действие (When):</b> Игрок отправляет запрос {@code POST /api/rooms/{code}/start}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Сервер возвращает HTTP-статус {@code 403 Forbidden}.</li>
     *   <li>Возвращается унифицированный код ошибки {@code "FORBIDDEN_NOT_CREATOR"}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("POST /api/rooms/{code}/start - Отказ 403 при попытке старта не создателем")
    void startGame_NotHost_Returns403Forbidden() throws Exception {
        TelegramUser host = TelegramUser.of("3001", "host3", "Хост 3");
        RoomResponse created = roomService.createRoom(host, new CreateRoomRequest("Чужой старт", 6));

        roomService.joinRoom(created.code(), TelegramUser.of("3002", "p2", "Игрок 2"));
        roomService.joinRoom(created.code(), TelegramUser.of("3003", "p3", "Игрок 3"));
        roomService.joinRoom(created.code(), TelegramUser.of("3004", "p4", "Игрок 4"));

        mockMvc.perform(post("/api/rooms/" + created.code() + "/start")
                        .header("X-Telegram-Id", "3002")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_NOT_CREATOR"));
    }

    /**
     * Проверяет отказ 401 Unauthorized при отсутствии заголовка аутентификации Telegram.
     *
     * <p><b>Предусловия (Given):</b> Комната существует в статусе LOBBY.</p>
     *
     * <p><b>Действие (When):</b> Запрос на старт отправляется без заголовка {@code X-Telegram-Id}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Сервер возвращает HTTP-статус {@code 401 Unauthorized}.
     * </p>
     */
    @Test
    @DisplayName("POST /api/rooms/{code}/start - Отказ 401 при отсутствии данных пользователя")
    void startGame_MissingTelegramId_Returns401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/rooms/ABC123/start")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
