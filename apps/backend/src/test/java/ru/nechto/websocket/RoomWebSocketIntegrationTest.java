package ru.nechto.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.model.TelegramUser;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.repository.jpa.RoomParticipantJpaRepository;
import ru.nechto.service.RoomService;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты WebSocket-шлюза и рассылки событий реального времени (RoomWebSocketIntegrationTest).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoomWebSocketIntegrationTest {

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

    @BeforeEach
    void setUp() {
        participantJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
    }

    /**
     * Проверяет, что сразу после установки WebSocket-соединения с комнатой клиент гарантированно
     * получает серверное событие синхронизации {@code room:state_sync} с полным объектом комнаты.
     *
     * <p><b>Предусловия (Given):</b> В базе данных создана комната с кодом {@code room.code()}.</p>
     *
     * <p><b>Действие (When):</b> Клиент подключается к {@code ws://localhost:{port}/ws/rooms/{code}?telegramId=1001}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *   <li>WebSocket-сессия успешно открывается.</li>
     *   <li>Первое полученное сообщение имеет {@code event = "room:state_sync"}.</li>
     *   <li>Данные комнаты содержат актуальный код и текущее число участников (1).</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("WS: Получение начального события room:state_sync сразу после подключения к комнате")
    void shouldReceiveStateSyncOnWebSocketConnection() throws Exception {
        RoomResponse room = roomService.createRoom(
                TelegramUser.of("1001", "player1", "Игрок 1"),
                new CreateRoomRequest("Партия WS", 8)
        );

        BlockingQueue<String> messagesReceived = new LinkedBlockingQueue<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        String wsUrl = "ws://localhost:" + port + "/ws/rooms/" + room.code() + "?telegramId=1001&displayName=Игрок%201";

        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                messagesReceived.add(message.getPayload());
            }
        }, wsUrl).get(5, TimeUnit.SECONDS);

        assertThat(session.isOpen()).isTrue();

        String stateSyncMsg = messagesReceived.poll(5, TimeUnit.SECONDS);
        assertThat(stateSyncMsg).isNotNull();

        JsonNode stateSyncJson = objectMapper.readTree(stateSyncMsg);
        assertThat(stateSyncJson.path("event").asString()).isEqualTo("room:state_sync");
        assertThat(stateSyncJson.path("data").path("code").asString()).isEqualTo(room.code());
        assertThat(stateSyncJson.path("data").path("currentPlayersCount").asInt()).isEqualTo(1);

        session.close();
    }

    /**
     * Проверяет доставку события {@code room:player_joined} подключенному игроку (хосту),
     * когда второй участник подключается к комнате через REST API.
     *
     * <p><b>Предусловия (Given):</b> Хост подключен к WebSocket комнаты.</p>
     *
     * <p><b>Действие (When):</b> Второй игрок выполняет {@code roomService.joinRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Хост получает событие {@code room:player_joined} с информацией о новом участнике и обновленном лобби.
     * </p>
     */
    @Test
    @DisplayName("WS: Рассылка события room:player_joined при подключении второго игрока через REST")
    void shouldBroadcastPlayerJoinedWhenSecondPlayerEnters() throws Exception {
        RoomResponse room = roomService.createRoom(
                TelegramUser.of("1001", "player1", "Игрок 1"),
                new CreateRoomRequest("Партия", 8)
        );

        BlockingQueue<String> messagesReceived = new LinkedBlockingQueue<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        String wsUrl = "ws://localhost:" + port + "/ws/rooms/" + room.code() + "?telegramId=1001&displayName=Игрок%201";

        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                messagesReceived.add(message.getPayload());
            }
        }, wsUrl).get(5, TimeUnit.SECONDS);

        // Пропускаем начальный state_sync
        messagesReceived.poll(5, TimeUnit.SECONDS);

        // Второй игрок подключается
        TelegramUser player2 = TelegramUser.of("1002", "player2", "Игрок 2");
        roomService.joinRoom(room.code(), player2);

        String playerJoinedMsg = messagesReceived.poll(5, TimeUnit.SECONDS);
        assertThat(playerJoinedMsg).isNotNull();

        JsonNode json = objectMapper.readTree(playerJoinedMsg);
        assertThat(json.path("event").asString()).isEqualTo("room:player_joined");
        assertThat(json.path("data").path("joinedPlayer").path("telegramId").asString()).isEqualTo("1002");
        assertThat(json.path("data").path("room").path("currentPlayersCount").asInt()).isEqualTo(2);

        session.close();
    }

    /**
     * Проверяет рассылку событий {@code room:host_changed} и {@code room:player_left}
     * всем оставшимся участникам комнаты при выходе создателя.
     *
     * <p><b>Предусловия (Given):</b> В комнате два игрока: создатель (1001) и второй игрок (1002).
     * Второй игрок слушает WebSocket-канал комнаты.</p>
     *
     * <p><b>Действие (When):</b> Создатель (1001) вызывает {@code leaveRoom}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Второй игрок получает события {@code room:host_changed} (с назначением его новым создателем)
     * и {@code room:player_left} (об уходе старого хоста).
     * </p>
     */
    @Test
    @DisplayName("WS: Рассылка событий room:host_changed и room:player_left при выходе создателя")
    void shouldBroadcastPlayerLeftAndHostChangedWhenCreatorLeaves() throws Exception {
        RoomResponse room = roomService.createRoom(
                TelegramUser.of("1001", "creator", "Создатель"),
                new CreateRoomRequest("Партия Хост", 6)
        );
        roomService.joinRoom(room.code(), TelegramUser.of("1002", "p2", "Игрок 2"));

        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        String wsUrl = "ws://localhost:" + port + "/ws/rooms/" + room.code() + "?telegramId=1002&displayName=Игрок%202";

        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                messages.add(message.getPayload());
            }
        }, wsUrl).get(5, TimeUnit.SECONDS);

        // Пропускаем начальный state_sync
        messages.poll(5, TimeUnit.SECONDS);

        // Хост выходит из комнаты
        roomService.leaveRoom(room.code(), "1001");

        // Ожидаем события host_changed и player_left
        String msg1 = messages.poll(5, TimeUnit.SECONDS);
        String msg2 = messages.poll(5, TimeUnit.SECONDS);

        assertThat(msg1).isNotNull();
        assertThat(msg2).isNotNull();

        boolean hasHostChanged = msg1.contains("room:host_changed") || msg2.contains("room:host_changed");
        boolean hasPlayerLeft = msg1.contains("room:player_left") || msg2.contains("room:player_left");

        assertThat(hasHostChanged).isTrue();
        assertThat(hasPlayerLeft).isTrue();

        session.close();
    }

    /**
     * Проверяет рассылку события {@code room:settings_updated} при обновлении лимита игроков создателем.
     *
     * <p><b>Предусловия (Given):</b> Игрок слушает WebSocket-канал комнаты.</p>
     *
     * <p><b>Действие (When):</b> Создатель изменяет {@code maxPlayers} с 6 на 10.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Клиент получает событие {@code room:settings_updated} с новым значением {@code maxPlayers = 10}.
     * </p>
     */
    @Test
    @DisplayName("WS: Рассылка события room:settings_updated при изменении настроек создателем")
    void shouldBroadcastSettingsUpdatedWhenCreatorChangesMaxPlayers() throws Exception {
        RoomResponse room = roomService.createRoom(
                TelegramUser.of("1001", "creator", "Создатель"),
                new CreateRoomRequest("Настройки WS", 6)
        );

        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        String wsUrl = "ws://localhost:" + port + "/ws/rooms/" + room.code() + "?telegramId=1001&displayName=Создатель";

        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                messages.add(message.getPayload());
            }
        }, wsUrl).get(5, TimeUnit.SECONDS);

        // Пропускаем начальный state_sync
        messages.poll(5, TimeUnit.SECONDS);

        // Создатель меняет maxPlayers
        roomService.updateSettings(room.code(), "1001", 10);

        String settingsMsg = messages.poll(5, TimeUnit.SECONDS);
        assertThat(settingsMsg).isNotNull();

        JsonNode json = objectMapper.readTree(settingsMsg);
        assertThat(json.path("event").asString()).isEqualTo("room:settings_updated");
        assertThat(json.path("data").path("maxPlayers").asInt()).isEqualTo(10);

        session.close();
    }

    /**
     * Проверяет рассылку события {@code room:player_connection_changed} (статус DISCONNECTED)
     * при закрытии WebSocket-соединения участником.
     *
     * <p><b>Предусловия (Given):</b> В комнате находятся создатель и второй игрок, оба подключены по WS.</p>
     *
     * <p><b>Действие (When):</b> Второй игрок закрывает WebSocket-сессию.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * Создатель комнаты получает событие {@code room:player_connection_changed} со статусом {@code DISCONNECTED}.
     * </p>
     */
    @Test
    @DisplayName("WS: Рассылка события room:player_connection_changed при закрытии соединения участником")
    void shouldBroadcastPlayerConnectionChangedWhenSessionCloses() throws Exception {
        RoomResponse room = roomService.createRoom(
                TelegramUser.of("1001", "creator", "Создатель"),
                new CreateRoomRequest("Коннект тест", 6)
        );
        roomService.joinRoom(room.code(), TelegramUser.of("1002", "p2", "Игрок 2"));

        BlockingQueue<String> creatorMessages = new LinkedBlockingQueue<>();
        StandardWebSocketClient client = new StandardWebSocketClient();

        // Подключаем хоста
        String hostUrl = "ws://localhost:" + port + "/ws/rooms/" + room.code() + "?telegramId=1001&displayName=Создатель";
        WebSocketSession hostSession = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                creatorMessages.add(message.getPayload());
            }
        }, hostUrl).get(5, TimeUnit.SECONDS);

        // Пропускаем начальный state_sync хоста
        creatorMessages.poll(5, TimeUnit.SECONDS);

        // Подключаем второго игрока
        String p2Url = "ws://localhost:" + port + "/ws/rooms/" + room.code() + "?telegramId=1002&displayName=Игрок%202";
        WebSocketSession p2Session = client.execute(new TextWebSocketHandler(), p2Url).get(5, TimeUnit.SECONDS);

        // Второй игрок закрывает сессию
        p2Session.close();

        // Хост должен получить событие connection_changed со статусом DISCONNECTED
        String changeMsg = creatorMessages.poll(5, TimeUnit.SECONDS);
        assertThat(changeMsg).isNotNull();

        JsonNode json = objectMapper.readTree(changeMsg);
        assertThat(json.path("event").asString()).isEqualTo("room:player_connection_changed");
        assertThat(json.path("data").path("telegramId").asString()).isEqualTo("1002");
        assertThat(json.path("data").path("status").asString()).isEqualTo("DISCONNECTED");

        hostSession.close();
    }
}
