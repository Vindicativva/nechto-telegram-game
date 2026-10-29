package ru.nechto.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.model.TelegramUser;
import ru.nechto.repository.RoomRepository;
import ru.nechto.service.RoomService;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        roomRepository.findAll().forEach(r -> roomRepository.delete(r.getCode()));
    }

    @Test
    @DisplayName("Подключение по WebSocket возвращает room:state_sync, а при входе второго игрока первый игрок получает room:player_joined")
    void webSocket_PlayerJoinedBroadcast_Success() throws Exception {
        // 1. Создаем комнату с первым игроком
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

        assertThat(session.isOpen()).isTrue();

        // 2. Первый игрок должен получить room:state_sync
        String stateSyncMsg = messagesReceived.poll(5, TimeUnit.SECONDS);
        assertThat(stateSyncMsg).isNotNull();

        JsonNode stateSyncJson = objectMapper.readTree(stateSyncMsg);
        assertThat(stateSyncJson.path("event").asString()).isEqualTo("room:state_sync");
        assertThat(stateSyncJson.path("data").path("code").asString()).isEqualTo(room.code());
        assertThat(stateSyncJson.path("data").path("currentPlayersCount").asInt()).isEqualTo(1);

        // 3. Второй игрок подключается к комнате через REST API (RoomService)
        TelegramUser player2 = TelegramUser.of("1002", "player2", "Игрок 2");
        roomService.joinRoom(room.code(), player2);

        // 4. Первый игрок гарантированно получает событие room:player_joined
        String playerJoinedMsg = messagesReceived.poll(5, TimeUnit.SECONDS);
        assertThat(playerJoinedMsg).isNotNull();

        JsonNode playerJoinedJson = objectMapper.readTree(playerJoinedMsg);
        assertThat(playerJoinedJson.path("event").asString()).isEqualTo("room:player_joined");
        assertThat(playerJoinedJson.path("data").path("joinedPlayer").path("telegramId").asString()).isEqualTo("1002");
        assertThat(playerJoinedJson.path("data").path("joinedPlayer").path("displayName").asString()).isEqualTo("Игрок 2");
        assertThat(playerJoinedJson.path("data").path("room").path("currentPlayersCount").asInt()).isEqualTo(2);

        session.close();
    }

    @Test
    @DisplayName("Подключение к /ws с отправкой lobby:subscribe возвращает room:state_sync")
    void webSocket_LobbySubscribeEvent_Success() throws Exception {
        RoomResponse room = roomService.createRoom(
                TelegramUser.of("2001", "creator", "Создатель"),
                new CreateRoomRequest("Лобби тест", 6)
        );

        BlockingQueue<String> messagesReceived = new LinkedBlockingQueue<>();

        StandardWebSocketClient client = new StandardWebSocketClient();
        String wsUrl = "ws://localhost:" + port + "/ws";

        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                messagesReceived.add(message.getPayload());
            }
        }, wsUrl).get(5, TimeUnit.SECONDS);

        assertThat(session.isOpen()).isTrue();

        // Отправляем lobby:subscribe
        String subscribeMessage = String.format("""
                {
                    "event": "lobby:subscribe",
                    "data": {
                        "code": "%s",
                        "telegramId": "2001",
                        "displayName": "Создатель"
                    }
                }
                """, room.code());

        session.sendMessage(new TextMessage(subscribeMessage));

        // Получаем room:state_sync
        String stateSyncMsg = messagesReceived.poll(5, TimeUnit.SECONDS);
        assertThat(stateSyncMsg).isNotNull();

        JsonNode stateSyncJson = objectMapper.readTree(stateSyncMsg);
        assertThat(stateSyncJson.path("event").asString()).isEqualTo("room:state_sync");
        assertThat(stateSyncJson.path("data").path("code").asString()).isEqualTo(room.code());

        session.close();
    }
}
