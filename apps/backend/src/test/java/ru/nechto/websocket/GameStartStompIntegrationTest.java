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
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.response.RoomResponse;
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
 * Интеграционные тесты сценария запуска игры через Spring STOMP (/ws-game).
 * Проверяют:
 * <ul>
 *     <li>Рассылку публичного состояния GAME_STATE в топик /topic/room/{code} без карт и ролей;</li>
 *     <li>Персональную защищенную доставку скрытого PLAYER_PRIVATE_STATE каждому игроку в /user/queue/game;</li>
 *     <li>Блокировку повторного запуска уже начатой игры с кодом ROOM_ALREADY_STARTED.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GameStartStompIntegrationTest {

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
    private StompSession sessionHost;
    private StompSession sessionPlayer2;

    @BeforeEach
    void setUp() {
        participantJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (sessionHost != null && sessionHost.isConnected()) {
            sessionHost.disconnect();
        }
        if (sessionPlayer2 != null && sessionPlayer2.isConnected()) {
            sessionPlayer2.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    private StompSession connectStompWithUser(String telegramId) throws Exception {
        String url = "ws://localhost:" + port + "/ws-game";
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("telegramId", telegramId);
        return stompClient.connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private JsonNode pollEvent(BlockingQueue<String> messages, String targetEvent) throws Exception {
        long deadline = System.currentTimeMillis() + 7000;
        while (System.currentTimeMillis() < deadline) {
            String msg = messages.poll(1, TimeUnit.SECONDS);
            if (msg != null) {
                JsonNode root = objectMapper.readTree(msg);
                String event = root.path("event").asText();
                String type = root.path("type").asText();
                if (targetEvent.equals(event) || targetEvent.equals(type)) {
                    return root;
                }
            }
        }
        throw new AssertionError("Did not receive event '" + targetEvent + "' within timeout");
    }

    /**
     * Проверяет, что при старте игры через STOMP публичный топик получает GAME_STATE без ролей и карт,
     * а каждый подключенный игрок получает в персональную очередь /user/queue/game своё приватное состояние.
     *
     * <p><b>Предусловия (Given):</b> В комнате 4 игрока. Хост ("1001") и игрок 2 ("1002")
     * подключены по STOMP, подписаны на публичный топик комнаты и на персональную очередь {@code /user/queue/game}.</p>
     *
     * <p><b>Действие (When):</b> Хост отправляет команду запуска на {@code /app/room/{code}/start}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * <ul>
     *     <li>В топик комнаты приходит событие {@code GAME_STATE} со статусом {@code IN_PROGRESS}, 4 игроками,
     *         направлением {@code CLOCKWISE}, но без полей ролей и карт;</li>
     *     <li>Хост в {@code /user/queue/game} получает событие {@code PLAYER_PRIVATE_STATE} со своей ролью и 4 картами;</li>
     *     <li>Игрок 2 в {@code /user/queue/game} получает событие {@code PLAYER_PRIVATE_STATE} со своей ролью и 4 картами.</li>
     * </ul>
     * </p>
     */
    @Test
    @DisplayName("STOMP запуск: публичный GAME_STATE без ролей/карт и персональные PLAYER_PRIVATE_STATE")
    void gameStart_ViaStomp_BroadcastsPublicGameStateAndSendsPrivateStates() throws Exception {
        TelegramUser host = TelegramUser.of("1001", "host1", "Хост 1");
        RoomResponse room = roomService.createRoom(host, new CreateRoomRequest("STOMP Старт", 6));
        roomService.joinRoom(room.code(), TelegramUser.of("1002", "p2", "Игрок 2"));
        roomService.joinRoom(room.code(), TelegramUser.of("1003", "p3", "Игрок 3"));
        roomService.joinRoom(room.code(), TelegramUser.of("1004", "p4", "Игрок 4"));

        sessionHost = connectStompWithUser("1001");
        sessionPlayer2 = connectStompWithUser("1002");

        BlockingQueue<String> hostTopicMessages = new LinkedBlockingQueue<>();
        BlockingQueue<String> hostPrivateMessages = new LinkedBlockingQueue<>();
        BlockingQueue<String> player2PrivateMessages = new LinkedBlockingQueue<>();

        sessionHost.subscribe("/topic/room/" + room.code(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                hostTopicMessages.offer((String) payload);
            }
        });

        sessionHost.subscribe("/user/queue/game", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                hostPrivateMessages.offer((String) payload);
            }
        });

        sessionPlayer2.subscribe("/user/queue/game", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                player2PrivateMessages.offer((String) payload);
            }
        });

        // Отправка команды старта
        String startPayload = """
                {
                    "telegramId": "1001"
                }
                """;
        sessionHost.send("/app/room/" + room.code() + "/start", startPayload);

        // 1. Проверка публичного GAME_STATE
        JsonNode publicRoot = pollEvent(hostTopicMessages, "GAME_STATE");
        assertThat(publicRoot.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(publicRoot.path("turnDirection").asText()).isEqualTo("CLOCKWISE");
        assertThat(publicRoot.path("deckCardsCount").asInt()).isGreaterThan(0);
        assertThat(publicRoot.path("discardCardsCount").asInt()).isEqualTo(0);

        JsonNode players = publicRoot.path("players");
        assertThat(players.size()).isEqualTo(4);

        // Проверка отсутствия ролей и карт в публичном сообщении
        String publicJson = publicRoot.toString();
        assertThat(publicJson).doesNotContain("\"role\"");
        assertThat(publicJson).doesNotContain("THE_THING");
        assertThat(publicJson).doesNotContain("HUMAN");

        for (JsonNode p : players) {
            assertThat(p.has("role")).isFalse();
            assertThat(p.has("cards")).isFalse();
            assertThat(p.path("cardsCount").asInt()).isEqualTo(4);
            assertThat(p.path("isAlive").asBoolean()).isTrue();
        }

        // 2. Проверка персонального PLAYER_PRIVATE_STATE для хоста ("1001")
        JsonNode hostPrivateRoot = pollEvent(hostPrivateMessages, "PLAYER_PRIVATE_STATE");
        assertThat(hostPrivateRoot.path("telegramId").asText()).isEqualTo("1001");
        String hostRole = hostPrivateRoot.path("role").asText();
        assertThat(hostRole).isIn("THE_THING", "HUMAN");
        assertThat(hostPrivateRoot.path("cards").size()).isEqualTo(4);
        assertThat(hostPrivateRoot.path("position").asInt()).isBetween(0, 3);

        // 3. Проверка персонального PLAYER_PRIVATE_STATE для игрока 2 ("1002")
        JsonNode p2PrivateRoot = pollEvent(player2PrivateMessages, "PLAYER_PRIVATE_STATE");
        assertThat(p2PrivateRoot.path("telegramId").asText()).isEqualTo("1002");
        String p2Role = p2PrivateRoot.path("role").asText();
        assertThat(p2Role).isIn("THE_THING", "HUMAN");
        assertThat(p2PrivateRoot.path("cards").size()).isEqualTo(4);
        assertThat(p2PrivateRoot.path("position").asInt()).isBetween(0, 3);
    }

    /**
     * Проверяет отказ при попытке повторного запуска уже начатой игры через STOMP.
     *
     * <p><b>Предусловия (Given):</b> Игра уже успешно запущена.</p>
     *
     * <p><b>Действие (When):</b> Хост повторно отправляет команду запуска на {@code /app/room/{code}/start}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * В очередь ошибок {@code /user/queue/errors} возвращается сообщение с кодом {@code ROOM_ALREADY_STARTED}.
     * </p>
     */
    @Test
    @DisplayName("STOMP повторный запуск: отказ с кодом ROOM_ALREADY_STARTED")
    void gameStart_ViaStomp_SecondStart_SendsRoomAlreadyStartedError() throws Exception {
        TelegramUser host = TelegramUser.of("2001", "host2", "Хост 2");
        RoomResponse room = roomService.createRoom(host, new CreateRoomRequest("Повторный старт", 6));
        roomService.joinRoom(room.code(), TelegramUser.of("2002", "p2", "Игрок 2"));
        roomService.joinRoom(room.code(), TelegramUser.of("2003", "p3", "Игрок 3"));
        roomService.joinRoom(room.code(), TelegramUser.of("2004", "p4", "Игрок 4"));

        sessionHost = connectStompWithUser("2001");
        BlockingQueue<String> errorMessages = new LinkedBlockingQueue<>();

        sessionHost.subscribe("/topic/room/" + room.code() + "/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorMessages.offer((String) payload);
            }
        });

        // Первый успешный старт
        String startPayload = """
                {
                    "telegramId": "2001"
                }
                """;
        sessionHost.send("/app/room/" + room.code() + "/start", startPayload);
        Thread.sleep(1000);

        // Второй повторный запуск
        sessionHost.send("/app/room/" + room.code() + "/start", startPayload);

        String err = errorMessages.poll(5, TimeUnit.SECONDS);
        assertThat(err).isNotNull();
        assertThat(err).contains("ROOM_ALREADY_STARTED");
        JsonNode errJson = objectMapper.readTree(err);
        String code = errJson.has("error") ? errJson.path("error").path("code").asText() : errJson.path("code").asText();
        assertThat(code).isEqualTo("ROOM_ALREADY_STARTED");
    }
}
