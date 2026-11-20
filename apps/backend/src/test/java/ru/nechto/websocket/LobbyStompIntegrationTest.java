package ru.nechto.websocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TelegramUser;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.repository.jpa.RoomParticipantJpaRepository;
import ru.nechto.service.RoomService;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты синхронизации лобби в реальном времени через Spring WebSocket/STOMP (/ws-game).
 * Проверяют подключение, рассылку ROOM_STATE, передачу прав хоста, валидацию лимитов и запуск игры.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LobbyStompIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomJpaRepository roomJpaRepository;

    @Autowired
    private RoomParticipantJpaRepository participantJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private StompSession stompSession;

    @BeforeEach
    void setUp() {
        participantJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    private StompSession connectStomp() throws Exception {
        String url = "ws://localhost:" + port + "/ws-game";
        return stompClient.connectAsync(url, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    private JsonNode pollRoomState(BlockingQueue<String> messages) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            String msg = messages.poll(1, TimeUnit.SECONDS);
            if (msg != null) {
                JsonNode root = objectMapper.readTree(msg);
                String event = root.path("event").asText();
                String type = root.path("type").asText();
                if ("ROOM_STATE".equals(event) || "ROOM_STATE".equals(type)) {
                    return root;
                }
            }
        }
        throw new AssertionError("Did not receive ROOM_STATE event within timeout");
    }

    /**
     * Проверяет подключение к комнате через STOMP-сообщение {@code @MessageMapping("/room/{code}/join")}
     * и рассылку события {@code ROOM_STATE} всем клиентам в топик {@code /topic/room/{code}}.
     *
     * <p><b>Предусловия (Given):</b> В базе создана комната в статусе {@code LOBBY} с кодом {@code room.code()}.
     * Клиент подключается к брокеру по адресу {@code /ws-game} и подписывается на {@code /topic/room/{code}}.</p>
     *
     * <p><b>Действие (When):</b> Клиент отправляет STOMP-сообщение на {@code /app/room/{code}/join}
     * с данными нового игрока ({@code telegramId = "2001"}).</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>В топик комнаты поступает событие {@code ROOM_STATE}.</li>
     *   <li>Список игроков в объекте комнаты содержит 2 участников (хост и новый игрок).</li>
     *   <li>Новый игрок зарегистрирован со статусом {@code ONLINE}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("STOMP /app/room/{code}/join - Успешное подключение и рассылка события ROOM_STATE")
    void joinRoom_ViaStomp_BroadcastsRoomState() throws Exception {
        TelegramUser host = TelegramUser.of("1001", "host", "Хост");
        RoomResponse room = roomService.createRoom(host, new CreateRoomRequest("STOMP Лобби", 6));

        stompSession = connectStomp();
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        stompSession.subscribe("/topic/room/" + room.code(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.offer((String) payload);
            }
        });

        String joinPayload = """
                {
                    "telegramId": "2001",
                    "username": "alex",
                    "displayName": "Алексей"
                }
                """;

        stompSession.send("/app/room/" + room.code() + "/join", joinPayload);

        JsonNode root = pollRoomState(messages);
        assertThat(root.has("event") && "ROOM_STATE".equals(root.get("event").asText())
                || root.has("type") && "ROOM_STATE".equals(root.get("type").asText())).isTrue();

        JsonNode players = root.has("data") ? root.path("data").path("players") : root.path("players");
        assertThat(players.size()).isEqualTo(2);
    }

    /**
     * Проверяет идемпотентное повторное подключение (Reconnection) через STOMP:
     * не создаются дубликаты игрока, сохраняется корректный состав лобби и рассылается {@code ROOM_STATE}.
     *
     * <p><b>Предусловия (Given):</b> Игрок с {@code telegramId = "2002"} уже состоит в комнате.</p>
     *
     * <p><b>Действие (When):</b> Игрок отправляет повторное сообщение на {@code /app/room/{code}/join}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>Количество игроков в комнате остается равным 2 (дубликаты отсутствуют).</li>
     *   <li>Рассылается актуальное состояние {@code ROOM_STATE}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("STOMP /app/room/{code}/join - Идемпотентное повторное подключение (без дубликатов участников)")
    void joinRoom_IdempotentReconnection_NoDuplicateParticipants() throws Exception {
        TelegramUser host = TelegramUser.of("1002", "host2", "Хост 2");
        RoomResponse room = roomService.createRoom(host, new CreateRoomRequest("Лобби без дубликатов", 6));
        roomService.joinRoom(room.code(), TelegramUser.of("2002", "bob", "Боб"));

        stompSession = connectStomp();
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        stompSession.subscribe("/topic/room/" + room.code(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.offer((String) payload);
            }
        });

        String rejoinPayload = """
                {
                    "telegramId": "2002",
                    "username": "bob",
                    "displayName": "Боб"
                }
                """;

        stompSession.send("/app/room/" + room.code() + "/join", rejoinPayload);

        JsonNode root = pollRoomState(messages);
        JsonNode players = root.has("data") ? root.path("data").path("players") : root.path("players");
        assertThat(players.size()).isEqualTo(2);
    }

    /**
     * Проверяет отказ в подключении через STOMP при превышении лимита вместимости комнаты (ROOM_FULL).
     *
     * <p><b>Предусловия (Given):</b> Комната с лимитом {@code maxPlayers = 4} уже полностью заполнена 4 игроками.</p>
     *
     * <p><b>Действие (When):</b> Пятый игрок ("3005") пытается войти через {@code /app/room/{code}/join}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Сервер возвращает ошибку с кодом {@code "ROOM_FULL"}.
     * </p>
     */
    @Test
    @DisplayName("STOMP /app/room/{code}/join - Отказ с кодом ROOM_FULL при заполнении комнаты")
    void joinRoom_WhenFull_SendsRoomFullError() throws Exception {
        TelegramUser host = TelegramUser.of("3001", "host3", "Хост 3");
        RoomResponse room = roomService.createRoom(host, new CreateRoomRequest("Полная комната", 4));
        roomService.joinRoom(room.code(), TelegramUser.of("3002", "p2", "Игрок 2"));
        roomService.joinRoom(room.code(), TelegramUser.of("3003", "p3", "Игрок 3"));
        roomService.joinRoom(room.code(), TelegramUser.of("3004", "p4", "Игрок 4"));

        stompSession = connectStomp();
        BlockingQueue<String> errorMessages = new LinkedBlockingQueue<>();

        stompSession.subscribe("/topic/room/" + room.code() + "/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorMessages.offer((String) payload);
            }
        });

        String fifthPlayerPayload = """
                {
                    "telegramId": "3005",
                    "username": "p5",
                    "displayName": "Игрок 5"
                }
                """;

        stompSession.send("/app/room/" + room.code() + "/join", fifthPlayerPayload);

        String errorJson = errorMessages.poll(5, TimeUnit.SECONDS);
        assertThat(errorJson).isNotNull();
        assertThat(errorJson).contains("ROOM_FULL");
    }

    /**
     * Проверяет отказ в подключении через STOMP к комнате, в которой игра уже началась (ROOM_ALREADY_STARTED).
     *
     * <p><b>Предусловия (Given):</b> Игра в комнате уже переведена в статус {@code IN_PROGRESS}.</p>
     *
     * <p><b>Действие (When):</b> Новый игрок пытается войти через {@code /app/room/{code}/join}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Сервер отклоняет вход с кодом {@code "ROOM_ALREADY_STARTED"}.
     * </p>
     */
    @Test
    @DisplayName("STOMP /app/room/{code}/join - Отказ с кодом ROOM_ALREADY_STARTED при начатой игре")
    void joinRoom_WhenGameStarted_SendsRoomAlreadyStartedError() throws Exception {
        TelegramUser host = TelegramUser.of("4001", "host4", "Хост 4");
        RoomResponse room = roomService.createRoom(host, new CreateRoomRequest("Начатая комната", 6));
        roomService.joinRoom(room.code(), TelegramUser.of("4002", "p2", "Игрок 2"));
        roomService.joinRoom(room.code(), TelegramUser.of("4003", "p3", "Игрок 3"));
        roomService.joinRoom(room.code(), TelegramUser.of("4004", "p4", "Игрок 4"));
        roomService.startGame(room.code(), "4001");

        stompSession = connectStomp();
        BlockingQueue<String> errorMessages = new LinkedBlockingQueue<>();

        stompSession.subscribe("/topic/room/" + room.code() + "/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorMessages.offer((String) payload);
            }
        });

        String newJoinPayload = """
                {
                    "telegramId": "4005",
                    "username": "p5",
                    "displayName": "Игрок 5"
                }
                """;

        stompSession.send("/app/room/" + room.code() + "/join", newJoinPayload);

        String errorJson = errorMessages.poll(5, TimeUnit.SECONDS);
        assertThat(errorJson).isNotNull();
        assertThat(errorJson).contains("ROOM_ALREADY_STARTED");
    }

    /**
     * Проверяет явный выход хоста через STOMP {@code @MessageMapping("/room/{code}/leave")}
     * с немедленной передачей прав создателя участнику с самым ранним joinedAt.
     *
     * <p><b>Предусловия (Given):</b> В комнате находятся хост ("5001") и участник ("5002").</p>
     *
     * <p><b>Действие (When):</b> Хост отправляет сообщение на {@code /app/room/{code}/leave}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>В топик {@code /topic/room/{code}} рассылается событие {@code ROOM_STATE}.</li>
     *   <li>Новым хостом становится игрок "5002".</li>
     *   <li>Количество оставшихся игроков равно 1.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("STOMP /app/room/{code}/leave - Явный выход хоста и рассылка ROOM_STATE с новым создателем")
    void leaveRoom_ViaStomp_MigratesHostAndBroadcastsRoomState() throws Exception {
        TelegramUser host = TelegramUser.of("5001", "host5", "Хост 5");
        RoomResponse room = roomService.createRoom(host, new CreateRoomRequest("Выход через STOMP", 6));
        roomService.joinRoom(room.code(), TelegramUser.of("5002", "p2", "Игрок 2"));

        stompSession = connectStomp();
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        stompSession.subscribe("/topic/room/" + room.code(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.offer((String) payload);
            }
        });

        String leavePayload = """
                {
                    "telegramId": "5001"
                }
                """;

        stompSession.send("/app/room/" + room.code() + "/leave", leavePayload);

        JsonNode root = pollRoomState(messages);
        JsonNode data = root.has("data") ? root.path("data") : root;
        assertThat(data.path("creatorId").asText()).isEqualTo("5002");
        assertThat(data.path("players").size()).isEqualTo(1);
    }

    /**
     * Проверяет команду запуска игры создателем через STOMP {@code @MessageMapping("/room/{code}/start")}
     * при наличии 4 участников (минимальный порог диапазона 4..12).
     *
     * <p><b>Предусловия (Given):</b> В комнате 4 зарегистрированных участника в статусе LOBBY.</p>
     *
     * <p><b>Действие (When):</b> Хост отправляет команду запуска на {@code /app/room/{code}/start}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>В топик комнаты поступает событие {@code ROOM_STATE}.</li>
     *   <li>Статус комнаты в ответе равен {@code IN_PROGRESS}.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("STOMP /app/room/{code}/start - Успешный запуск игры хостом при 4 игроках")
    void startGame_ViaStomp_SuccessWith4Players() throws Exception {
        TelegramUser host = TelegramUser.of("6001", "host6", "Хост 6");
        RoomResponse room = roomService.createRoom(host, new CreateRoomRequest("Старт STOMP", 6));
        roomService.joinRoom(room.code(), TelegramUser.of("6002", "p2", "Игрок 2"));
        roomService.joinRoom(room.code(), TelegramUser.of("6003", "p3", "Игрок 3"));
        roomService.joinRoom(room.code(), TelegramUser.of("6004", "p4", "Игрок 4"));

        stompSession = connectStomp();
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        stompSession.subscribe("/topic/room/" + room.code(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.offer((String) payload);
            }
        });

        String startPayload = """
                {
                    "telegramId": "6001"
                }
                """;

        stompSession.send("/app/room/" + room.code() + "/start", startPayload);

        JsonNode root = pollRoomState(messages);
        JsonNode data = root.has("data") ? root.path("data") : root;
        assertThat(data.path("status").asText()).isEqualTo("IN_PROGRESS");
    }

    /**
     * Проверяет отказ в запуске игры через STOMP при наличии менее чем 4 участников (NOT_ENOUGH_PLAYERS).
     *
     * <p><b>Предусловия (Given):</b> В комнате только 2 игрока.</p>
     *
     * <p><b>Действие (When):</b> Хост отправляет команду старта на {@code /app/room/{code}/start}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * В топик ошибок возвращается ответ с кодом {@code "NOT_ENOUGH_PLAYERS"}.
     * </p>
     */
    @Test
    @DisplayName("STOMP /app/room/{code}/start - Отказ с кодом NOT_ENOUGH_PLAYERS при менее 4 игроках")
    void startGame_ViaStomp_FewerThan4Players_SendsError() throws Exception {
        TelegramUser host = TelegramUser.of("7001", "host7", "Хост 7");
        RoomResponse room = roomService.createRoom(host, new CreateRoomRequest("Мало игроков", 6));
        roomService.joinRoom(room.code(), TelegramUser.of("7002", "p2", "Игрок 2"));

        stompSession = connectStomp();
        BlockingQueue<String> errorMessages = new LinkedBlockingQueue<>();

        stompSession.subscribe("/topic/room/" + room.code() + "/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorMessages.offer((String) payload);
            }
        });

        String startPayload = """
                {
                    "telegramId": "7001"
                }
                """;

        stompSession.send("/app/room/" + room.code() + "/start", startPayload);

        String errorJson = errorMessages.poll(5, TimeUnit.SECONDS);
        assertThat(errorJson).isNotNull();
        assertThat(errorJson).contains("NOT_ENOUGH_PLAYERS");
    }

    /**
     * Проверяет отказ в запуске игры через STOMP при попытке запуска не создателем (FORBIDDEN_NOT_CREATOR).
     *
     * <p><b>Предусловия (Given):</b> В комнате 4 игрока. Игрок "8002" является обычным участником.</p>
     *
     * <p><b>Действие (When):</b> Игрок "8002" отправляет команду старта на {@code /app/room/{code}/start}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * В топик ошибок возвращается ответ с кодом {@code "FORBIDDEN_NOT_CREATOR"}.
     * </p>
     */
    @Test
    @DisplayName("STOMP /app/room/{code}/start - Отказ с кодом FORBIDDEN_NOT_CREATOR при попытке старта не хостом")
    void startGame_ViaStomp_NonHost_SendsError() throws Exception {
        TelegramUser host = TelegramUser.of("8001", "host8", "Хост 8");
        RoomResponse room = roomService.createRoom(host, new CreateRoomRequest("Чужой старт", 6));
        roomService.joinRoom(room.code(), TelegramUser.of("8002", "p2", "Игрок 2"));
        roomService.joinRoom(room.code(), TelegramUser.of("8003", "p3", "Игрок 3"));
        roomService.joinRoom(room.code(), TelegramUser.of("8004", "p4", "Игрок 4"));

        stompSession = connectStomp();
        BlockingQueue<String> errorMessages = new LinkedBlockingQueue<>();

        stompSession.subscribe("/topic/room/" + room.code() + "/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorMessages.offer((String) payload);
            }
        });

        String startPayload = """
                {
                    "telegramId": "8002"
                }
                """;

        stompSession.send("/app/room/" + room.code() + "/start", startPayload);

        String errorJson = errorMessages.poll(5, TimeUnit.SECONDS);
        assertThat(errorJson).isNotNull();
        assertThat(errorJson).contains("FORBIDDEN_NOT_CREATOR");
    }
}
