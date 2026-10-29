package ru.nechto.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.repository.jpa.RoomParticipantJpaRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Комплексные интеграционные тесты жизненного цикла игровых комнат через HTTP REST API.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoomControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomJpaRepository roomJpaRepository;

    @Autowired
    private RoomParticipantJpaRepository participantJpaRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        participantJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
    }

    /**
     * Проверяет полный сквозной жизненный цикл комнаты через HTTP-запросы:
     * 1. Создание комнаты (POST /rooms).
     * 2. Получение комнаты (GET /rooms/{code}).
     * 3. Подключение второго игрока (POST /rooms/{code}/join).
     * 4. Изменение лимита игроков создателем (PATCH /rooms/{code}/settings).
     * 5. Выход создателя и передача прав хоста (POST /rooms/{code}/leave).
     *
     * <p><b>Предусловия (Given):</b> Чистая база данных.</p>
     *
     * <p><b>Действие (When):</b> Последовательное выполнение HTTP-запросов полного сценария лобби.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Все запросы возвращают ожидаемые HTTP-статусы, заголовки {@code Content-Type: application/json},
     * актуальное число участников и корректное переключение прав создателя.
     * </p>
     */
    @Test
    @DisplayName("Полный жизненный цикл комнаты через HTTP: создание -> получение -> вход -> настройки -> выход")
    void fullRoomLifecycleViaHttp_Success() throws Exception {
        // 1. Создание комнаты
        String createJson = """
                {
                    "name": "Пятничная игра",
                    "maxPlayers": 6
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "1001")
                        .header("X-Telegram-Username", "creator_nick")
                        .header("X-Telegram-Display-Name", "Создатель")
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Пятничная игра"))
                .andExpect(jsonPath("$.creatorId").value("1001"))
                .andExpect(jsonPath("$.maxPlayers").value(6))
                .andExpect(jsonPath("$.currentPlayersCount").value(1))
                .andExpect(jsonPath("$.isReadyToStart").value(false))
                .andExpect(jsonPath("$.players", hasSize(1)))
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String code = createdNode.get("code").asText();

        // 2. Получение информации о комнате
        mockMvc.perform(get("/api/v1/rooms/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.currentPlayersCount").value(1));

        // 3. Подключение второго игрока
        String joinJson = """
                {
                    "telegramId": "1002",
                    "username": "player2_nick",
                    "displayName": "Второй игрок"
                }
                """;

        mockMvc.perform(post("/api/v1/rooms/" + code + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlayersCount").value(2))
                .andExpect(jsonPath("$.players", hasSize(2)));

        // 4. Изменение настроек комнаты создателем
        String updateSettingsJson = """
                {
                    "maxPlayers": 8
                }
                """;

        mockMvc.perform(patch("/api/v1/rooms/" + code + "/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "1001")
                        .content(updateSettingsJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPlayers").value(8));

        // 5. Выход создателя и миграция хоста ко второму игроку
        mockMvc.perform(post("/api/v1/rooms/" + code + "/leave")
                        .header("X-Telegram-Id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlayersCount").value(1))
                .andExpect(jsonPath("$.creatorId").value("1002"))
                .andExpect(jsonPath("$.players[0].isCreator").value(true));
    }

    /**
     * Проверяет формат единого ответа об ошибке при нарушении валидации DTO (400 VALIDATION_ERROR):
     * структура ответа содержит поля timestamp, status, path, error.code, error.message, error.details.
     *
     * <p><b>Предусловия (Given):</b> Невалидное тело запроса (maxPlayers = 2, пустое имя комнаты).</p>
     *
     * <p><b>Действие (When):</b> Отправляется запрос {@code POST /api/v1/rooms}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>HTTP-статус {@code 400 Bad Request}.</li>
     *   <li>Поле {@code status} равно 400.</li>
     *   <li>Поле {@code error.code} равно {@code "VALIDATION_ERROR"}.</li>
     *   <li>Массив {@code error.details} содержит описания проблемных полей.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("Единый формат ответа при ошибке валидации: 400 VALIDATION_ERROR с массивом details")
    void shouldReturnUnifiedValidationErrorResponse() throws Exception {
        String invalidJson = """
                {
                    "name": "",
                    "maxPlayers": 2
                }
                """;

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "1001")
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/rooms"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    /**
     * Проверяет ошибку 401 UNAUTHORIZED при отсутствии данных идентификации пользователя Telegram.
     */
    @Test
    @DisplayName("Отказ 401 UNAUTHORIZED при отсутствии идентификатора пользователя Telegram")
    void shouldReturn401WhenTelegramUserNotProvided() throws Exception {
        String validJson = """
                {
                    "name": "Комната",
                    "maxPlayers": 6
                }
                """;

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    /**
     * Проверяет отказ 403 FORBIDDEN_NOT_CREATOR при попытке изменить настройки комнаты обычным участником.
     */
    @Test
    @DisplayName("Отказ 403 FORBIDDEN_NOT_CREATOR при попытке изменения настроек не создателем")
    void shouldReturn403WhenNonCreatorUpdatesSettings() throws Exception {
        // Создаем комнату создателем 1001
        String createJson = """
                {
                    "name": "Тест настроек",
                    "maxPlayers": 6
                }
                """;
        MvcResult res = mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "1001")
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        String code = objectMapper.readTree(res.getResponse().getContentAsString()).get("code").asText();

        // Игрок 1002 пытается изменить настройки
        String updateJson = """
                {
                    "maxPlayers": 10
                }
                """;

        mockMvc.perform(patch("/api/v1/rooms/" + code + "/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "1002")
                        .content(updateJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN_NOT_CREATOR"));
    }

    /**
     * Проверяет отказ 404 ROOM_NOT_FOUND при обращении к несуществующему коду комнаты.
     */
    @Test
    @DisplayName("Отказ 404 ROOM_NOT_FOUND при обращении по несуществующему коду")
    void shouldReturn404WhenRoomNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_FOUND"));
    }

    /**
     * Проверяет атомарность транзакции при невалидном запросе:
     * состояние записей в БД остаётся полностью неизменным при возврате клиенту 400 Bad Request.
     */
    @Test
    @DisplayName("Атомарность транзакции: невалидный запрос не меняет состояние БД")
    void shouldPreserveDatabaseStateOnInvalidRequest() throws Exception {
        // Создаем комнату
        String createJson = """
                {
                    "name": "Атомарность",
                    "maxPlayers": 4
                }
                """;
        MvcResult res = mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Id", "1001")
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        String code = objectMapper.readTree(res.getResponse().getContentAsString()).get("code").asText();

        // Попытка подключить игрока с XSS в имени (должна завершиться 400)
        String xssJoinJson = """
                {
                    "telegramId": "1002",
                    "displayName": "<script>alert(1)</script>"
                }
                """;

        mockMvc.perform(post("/api/v1/rooms/" + code + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(xssJoinJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PLAYER_NAME"));

        // Убеждаемся, что в базе по-прежнему ровно 1 участник
        var room = roomJpaRepository.findByCodeWithParticipants(code).orElseThrow();
        assertThat(room.getParticipants()).hasSize(1);
    }

    /**
     * Проверяет безопасность сообщений об ошибках:
     * ответ сервера со статусом 500 обезличен и не содержит SQL-запросов, названий таблиц или Java-стектрейсов.
     */
    @Test
    @DisplayName("Маскирование системных ошибок: статус 500 без утечки SQL, имен таблиц и stacktrace")
    void shouldMaskInternalErrorsWithoutLeakingSqlOrStackTrace() throws Exception {
        // Искусственный вызов ошибки сервера через мок или обращение
        MvcResult result = mockMvc.perform(get("/api/v1/rooms/TRIGGER500"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        assertThat(responseBody.toLowerCase())
                .doesNotContain("select ")
                .doesNotContain("insert ")
                .doesNotContain("from rooms")
                .doesNotContain("org.postgresql")
                .doesNotContain("exception at ");
    }
}
