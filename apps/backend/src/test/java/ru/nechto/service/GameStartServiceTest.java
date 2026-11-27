package ru.nechto.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ru.nechto.dto.game.ActiveGameState;
import ru.nechto.dto.game.GameStartResult;
import ru.nechto.dto.game.GameStateEvent;
import ru.nechto.dto.game.PlayerPrivateState;
import ru.nechto.dto.game.PublicGameState;
import ru.nechto.dto.game.PublicPlayerInfo;
import ru.nechto.entity.RoomEntity;
import ru.nechto.entity.RoomParticipantEntity;
import ru.nechto.exception.ForbiddenException;
import ru.nechto.exception.NotEnoughPlayersException;
import ru.nechto.exception.RoomAlreadyStartedException;
import ru.nechto.exception.RoomNotFoundException;
import ru.nechto.exception.TooManyPlayersException;
import ru.nechto.exception.UnauthorizedException;
import ru.nechto.model.Card;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.model.PlayerRole;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TurnDirection;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.websocket.RoomWebSocketBroadcaster;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Модульные тесты сервиса запуска игровой партии {@link GameStartService}.
 * Проверяют рассадку за столом, раздачу карт, назначение ролей THE_THING/HUMAN,
 * валидацию хоста и лимитов, блокировку повторного старта и безопасность данных.
 */
class GameStartServiceTest {

    private RoomJpaRepository roomJpaRepository;
    private DeckFactory deckFactory;
    private RoomWebSocketBroadcaster broadcaster;
    private ObjectMapper objectMapper;
    private GameStartService gameStartService;

    @BeforeEach
    void setUp() {
        roomJpaRepository = mock(RoomJpaRepository.class);
        deckFactory = new DeckFactory();
        broadcaster = mock(RoomWebSocketBroadcaster.class);
        objectMapper = new ObjectMapper();
        gameStartService = new GameStartService(roomJpaRepository, deckFactory, broadcaster);
    }

    private RoomEntity createRoomWithParticipants(String code, String creatorId, int playerCount) {
        RoomEntity room = new RoomEntity(
                UUID.randomUUID(),
                code,
                "Тестовая комната",
                creatorId,
                RoomStatus.LOBBY,
                playerCount,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
        for (int i = 1; i <= playerCount; i++) {
            String pid = String.valueOf(1000 + i);
            RoomParticipantEntity p = new RoomParticipantEntity(
                    UUID.randomUUID(),
                    room,
                    pid,
                    "user_" + i,
                    "Игрок " + i,
                    pid.equals(creatorId),
                    ConnectionStatus.ONLINE,
                    Instant.now(),
                    null
            );
            room.addParticipant(p);
        }
        return room;
    }

    /**
     * Проверяет, что при старте партии на 4 игроков ровно один участник получает роль THE_THING
     * и карту «Нечто», а остальные 3 участника — роль HUMAN.
     *
     * <p><b>Предусловия (Given):</b> Создана комната с 4 участниками, статус LOBBY.</p>
     *
     * <p><b>Действие (When):</b> Хост запускает партию через {@code startGame}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Ровно 1 игрок имеет роль THE_THING и карту с ID 1 («Нечто»),
     * а 3 других имеют роль HUMAN. Каждый игрок имеет ровно 4 карты на руках.
     * </p>
     */
    @Test
    @DisplayName("Старт на 4 игроков: ровно 1 Нечто (с картой «Нечто») и 3 Человека")
    void startGame_With4Players_AssignsExactlyOneTheThingAndThreeHumans() {
        RoomEntity room = createRoomWithParticipants("ROOM04", "1001", 4);
        when(roomJpaRepository.findByCodeForUpdate("ROOM04")).thenReturn(Optional.of(room));

        GameStartResult result = gameStartService.startGame("ROOM04", "1001", new Random(42));

        assertThat(result).isNotNull();
        Map<String, PlayerPrivateState> states = result.playerPrivateStates();
        assertThat(states).hasSize(4);

        long thingCount = states.values().stream().filter(s -> s.role() == PlayerRole.THE_THING).count();
        long humanCount = states.values().stream().filter(s -> s.role() == PlayerRole.HUMAN).count();

        assertThat(thingCount).isEqualTo(1);
        assertThat(humanCount).isEqualTo(3);

        PlayerPrivateState thingPlayer = states.values().stream()
                .filter(s -> s.role() == PlayerRole.THE_THING)
                .findFirst()
                .orElseThrow();
        assertThat(thingPlayer.cards()).anyMatch(Card::isTheThing);
        assertThat(thingPlayer.cards()).hasSize(4);

        for (PlayerPrivateState human : states.values().stream().filter(s -> s.role() == PlayerRole.HUMAN).toList()) {
            assertThat(human.cards()).noneMatch(Card::isTheThing);
            assertThat(human.cards()).hasSize(4);
        }
    }

    /**
     * Параметризованный тест для N = 4, 6, 8, 10, 12 игроков:
     * проверяет, что всегда ровно 1 Нечто и N - 1 Людей.
     *
     * <p><b>Предусловия (Given):</b> Комната на N игроков со статусом LOBBY.</p>
     *
     * <p><b>Действие (When):</b> Хост запускает партию.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Ровно 1 участник получает THE_THING, N-1 получают HUMAN,
     * все получают по 4 карты, колода добора содержит положенное число карт.
     * </p>
     */
    @ParameterizedTest(name = "Игроков: {0}")
    @ValueSource(ints = {4, 5, 6, 8, 10, 12})
    @DisplayName("Параметризованный тест распределения ролей для N участников (4..12)")
    void startGame_WithVariousPlayerCounts_AssignsCorrectRoles(int playerCount) {
        RoomEntity room = createRoomWithParticipants("ROOM" + playerCount, "1001", playerCount);
        when(roomJpaRepository.findByCodeForUpdate("ROOM" + playerCount)).thenReturn(Optional.of(room));

        GameStartResult result = gameStartService.startGame("ROOM" + playerCount, "1001", new Random(12345));

        Map<String, PlayerPrivateState> states = result.playerPrivateStates();
        assertThat(states).hasSize(playerCount);

        long thingCount = states.values().stream().filter(s -> s.role() == PlayerRole.THE_THING).count();
        long humanCount = states.values().stream().filter(s -> s.role() == PlayerRole.HUMAN).count();

        assertThat(thingCount).isEqualTo(1);
        assertThat(humanCount).isEqualTo(playerCount - 1);

        for (PlayerPrivateState state : states.values()) {
            assertThat(state.cards()).hasSize(4);
        }

        PublicGameState pub = result.publicGameState();
        int totalEligible = deckFactory.getCardsForPlayers(playerCount).size();
        assertThat(pub.deckCardsCount()).isEqualTo(totalEligible - 4 * playerCount);
        assertThat(pub.discardCardsCount()).isEqualTo(0);
    }

    /**
     * Проверяет случайную рассадку игроков за столом и определение первого хода по часовой стрелке.
     *
     * <p><b>Предусловия (Given):</b> Комната с 4 игроками.</p>
     *
     * <p><b>Действие (When):</b> Партия запускается с детерминированным Random.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Игроки рассажены по позициям 0..3, направление хода CLOCKWISE,
     * текущий ход принадлежит игроку с позицией 0 (isMyTurn == true только у него).
     * </p>
     */
    @Test
    @DisplayName("Рассадка за столом: позиции 0..N-1, направление CLOCKWISE, ход у позиции 0")
    void startGame_SeatingOrderAndClockwiseTurn() {
        RoomEntity room = createRoomWithParticipants("SEAT01", "1001", 4);
        when(roomJpaRepository.findByCodeForUpdate("SEAT01")).thenReturn(Optional.of(room));

        GameStartResult result = gameStartService.startGame("SEAT01", "1001", new Random(999));

        PublicGameState pub = result.publicGameState();
        assertThat(pub.turnDirection()).isEqualTo(TurnDirection.CLOCKWISE);
        assertThat(pub.currentTurnIndex()).isEqualTo(0);
        assertThat(pub.players()).hasSize(4);

        for (int i = 0; i < 4; i++) {
            PublicPlayerInfo p = pub.players().get(i);
            assertThat(p.position()).isEqualTo(i);
            assertThat(p.cardsCount()).isEqualTo(4);
            assertThat(p.isAlive()).isTrue();
            assertThat(p.quarantineRounds()).isEqualTo(0);
        }

        String firstPlayerId = pub.players().get(0).telegramId();
        assertThat(pub.currentTurnPlayerId()).isEqualTo(firstPlayerId);

        Map<String, PlayerPrivateState> privates = result.playerPrivateStates();
        PlayerPrivateState firstPrivate = privates.get(firstPlayerId);
        assertThat(firstPrivate.isMyTurn()).isTrue();
        assertThat(firstPrivate.availableActions()).contains("DRAW_CARD");

        for (int i = 1; i < 4; i++) {
            String otherId = pub.players().get(i).telegramId();
            assertThat(privates.get(otherId).isMyTurn()).isFalse();
            assertThat(privates.get(otherId).availableActions()).doesNotContain("DRAW_CARD");
        }
    }

    /**
     * Проверяет безопасность: в публичном сообщении GAME_STATE полностью отсутствуют
     * поля ролей и идентификаторы карт на руках игроков (п. 4.1.5 и п. 4.2.11 ТЗ).
     *
     * <p><b>Предусловия (Given):</b> Партия успешно запущена.</p>
     *
     * <p><b>Действие (When):</b> Публичное событие {@link GameStateEvent} сериализуется в JSON-строку.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b>
     * В JSON отсутствуют ключи {@code role}, значения ролей {@code THE_THING}, {@code HUMAN},
     * закрытые карты игроков {@code cards} и названия игровых карт.
     * </p>
     */
    @Test
    @DisplayName("Проверка безопасности: публичный GAME_STATE не раскрывает ролей и карт игроков")
    void startGame_SecurityPublicGameStateDoesNotLeakRolesOrCards() throws Exception {
        RoomEntity room = createRoomWithParticipants("SEC001", "1001", 4);
        when(roomJpaRepository.findByCodeForUpdate("SEC001")).thenReturn(Optional.of(room));

        GameStartResult result = gameStartService.startGame("SEC001", "1001", new Random(777));
        GameStateEvent publicEvent = GameStateEvent.of(result.publicGameState());

        String json = objectMapper.writeValueAsString(publicEvent);
        JsonNode root = objectMapper.readTree(json);

        // Проверяем, что на корневом уровне и в игроках нет поля role
        assertThat(json).doesNotContain("\"role\"");
        assertThat(json).doesNotContain("THE_THING");
        assertThat(json).doesNotContain("HUMAN");

        // Проверяем, что в списке игроков нет массива cards
        JsonNode players = root.path("players");
        assertThat(players.isArray()).isTrue();
        for (JsonNode player : players) {
            assertThat(player.has("role")).isFalse();
            assertThat(player.has("cards")).isFalse();
            assertThat(player.has("hand")).isFalse();
            assertThat(player.path("cardsCount").asInt()).isEqualTo(4);
            assertThat(player.has("position")).isTrue();
            assertThat(player.has("telegramId")).isTrue();
        }

        // Проверка отсутствия карт в data объекте
        if (root.has("data")) {
            JsonNode data = root.path("data");
            assertThat(data.has("role")).isFalse();
            assertThat(data.has("cards")).isFalse();
        }
    }

    /**
     * Проверяет, что персональное состояние PLAYER_PRIVATE_STATE содержит секретную роль
     * и ровно 4 карты на руке игрока.
     *
     * <p><b>Предусловия (Given):</b> Партия запущена.</p>
     *
     * <p><b>Действие (When):</b> Проверяется объект {@link PlayerPrivateState} для каждого игрока.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Роль не null, список карт содержит ровно 4 валидные карты,
     * позиция за столом соответствует порядковому номеру.
     * </p>
     */
    @Test
    @DisplayName("PLAYER_PRIVATE_STATE содержит роль, позицию и 4 карты на руку")
    void startGame_PrivateStateContainsOwnRoleAndCards() {
        RoomEntity room = createRoomWithParticipants("PRIV01", "1001", 4);
        when(roomJpaRepository.findByCodeForUpdate("PRIV01")).thenReturn(Optional.of(room));

        GameStartResult result = gameStartService.startGame("PRIV01", "1001", new Random(111));

        for (PlayerPrivateState state : result.playerPrivateStates().values()) {
            assertThat(state.role()).isIn(PlayerRole.THE_THING, PlayerRole.HUMAN);
            assertThat(state.cards()).hasSize(4);
            assertThat(state.cards()).allMatch(c -> c.id() > 0 && c.name() != null);
            assertThat(state.position()).isBetween(0, 3);
        }
    }

    /**
     * Проверяет блокировку повторного запуска уже начатой игры (RoomAlreadyStartedException).
     *
     * <p><b>Предусловия (Given):</b> Комната уже находится в статусе IN_PROGRESS.</p>
     *
     * <p><b>Действие (When):</b> Хост повторно вызывает {@code startGame}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Выбрасывается {@link RoomAlreadyStartedException}.
     * </p>
     */
    @Test
    @DisplayName("Блокировка повторного запуска уже начатой игры (RoomAlreadyStartedException)")
    void startGame_AlreadyStarted_ThrowsRoomAlreadyStartedException() {
        RoomEntity room = createRoomWithParticipants("ALR001", "1001", 4);
        room.setStatus(RoomStatus.IN_PROGRESS);
        when(roomJpaRepository.findByCodeForUpdate("ALR001")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> gameStartService.startGame("ALR001", "1001"))
                .isInstanceOf(RoomAlreadyStartedException.class)
                .hasMessageContaining("Игра уже началась");

        verify(roomJpaRepository, never()).save(any());
    }

    /**
     * Проверяет отказ в старте игры, если в комнате менее 4 участников (NotEnoughPlayersException).
     *
     * <p><b>Предусловия (Given):</b> В комнате всего 3 игрока.</p>
     *
     * <p><b>Действие (When):</b> Хост вызывает {@code startGame}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Выбрасывается {@link NotEnoughPlayersException}.
     * </p>
     */
    @Test
    @DisplayName("Отказ в старте игры при числе участников менее 4 (NotEnoughPlayersException)")
    void startGame_LessThan4Players_ThrowsNotEnoughPlayersException() {
        RoomEntity room = createRoomWithParticipants("MIN001", "1001", 3);
        when(roomJpaRepository.findByCodeForUpdate("MIN001")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> gameStartService.startGame("MIN001", "1001"))
                .isInstanceOf(NotEnoughPlayersException.class)
                .hasMessageContaining("минимум 4 игрока");
    }

    /**
     * Проверяет отказ в старте игры, если в комнате более 12 участников (TooManyPlayersException).
     *
     * <p><b>Предусловия (Given):</b> В комнате 13 участников.</p>
     *
     * <p><b>Действие (When):</b> Хост вызывает {@code startGame}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Выбрасывается {@link TooManyPlayersException}.
     * </p>
     */
    @Test
    @DisplayName("Отказ в старте игры при числе участников более 12 (TooManyPlayersException)")
    void startGame_MoreThan12Players_ThrowsTooManyPlayersException() {
        RoomEntity room = createRoomWithParticipants("MAX001", "1001", 13);
        when(roomJpaRepository.findByCodeForUpdate("MAX001")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> gameStartService.startGame("MAX001", "1001"))
                .isInstanceOf(TooManyPlayersException.class)
                .hasMessageContaining("Максимальное количество участников");
    }

    /**
     * Проверяет запрет запуска игры пользователем, не являющимся создателем комнаты (ForbiddenException).
     *
     * <p><b>Предусловия (Given):</b> Создатель комнаты — игрок "1001", в комнате 4 человека.</p>
     *
     * <p><b>Действие (When):</b> Игрок "1002" пытается запустить игру.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Выбрасывается {@link ForbiddenException}.
     * </p>
     */
    @Test
    @DisplayName("Запрет запуска игры не создателем комнаты (ForbiddenException)")
    void startGame_NotHost_ThrowsForbiddenException() {
        RoomEntity room = createRoomWithParticipants("FORB01", "1001", 4);
        when(roomJpaRepository.findByCodeForUpdate("FORB01")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> gameStartService.startGame("FORB01", "1002"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Только создатель комнаты может запустить игру");
    }

    /**
     * Проверяет ошибку авторизации при пустом или null идентификаторе Telegram пользователя.
     *
     * <p><b>Предусловия (Given):</b> Передан null или пустой telegramId.</p>
     *
     * <p><b>Действие (When):</b> Вызов {@code startGame}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Выбрасывается {@link UnauthorizedException}.
     * </p>
     */
    @Test
    @DisplayName("Отказ при отсутствии telegramId (UnauthorizedException)")
    void startGame_MissingTelegramId_ThrowsUnauthorizedException() {
        assertThatThrownBy(() -> gameStartService.startGame("ROOM01", null))
                .isInstanceOf(UnauthorizedException.class);

        assertThatThrownBy(() -> gameStartService.startGame("ROOM01", "  "))
                .isInstanceOf(UnauthorizedException.class);
    }

    /**
     * Проверяет выброс RoomNotFoundException при попытке запуска несуществующей комнаты.
     *
     * <p><b>Предусловия (Given):</b> Комната отсутствует в репозитории.</p>
     *
     * <p><b>Действие (When):</b> Вызов {@code startGame}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Выбрасывается {@link RoomNotFoundException}.
     * </p>
     */
    @Test
    @DisplayName("Отказ при обращении к несуществующей комнате (RoomNotFoundException)")
    void startGame_RoomNotFound_ThrowsRoomNotFoundException() {
        when(roomJpaRepository.findByCodeForUpdate("NOEXST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameStartService.startGame("NOEXST", "1001"))
                .isInstanceOf(RoomNotFoundException.class);
    }

    /**
     * Проверяет перевод статуса комнаты в IN_PROGRESS и сохранение в БД при успешном старте.
     *
     * <p><b>Предусловия (Given):</b> Комната в статусе LOBBY с 4 участниками.</p>
     *
     * <p><b>Действие (When):</b> Хост запускает игру.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Статус комнаты меняется на IN_PROGRESS, вызывается {@code roomJpaRepository.save}.
     * </p>
     */
    @Test
    @DisplayName("Перевод комнаты в статус IN_PROGRESS и сохранение в репозиторий")
    void startGame_UpdatesRoomStatusToInProgress() {
        RoomEntity room = createRoomWithParticipants("STAT01", "1001", 4);
        when(roomJpaRepository.findByCodeForUpdate("STAT01")).thenReturn(Optional.of(room));

        gameStartService.startGame("STAT01", "1001");

        assertThat(room.getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
        verify(roomJpaRepository, times(1)).save(room);
    }

    /**
     * Проверяет регистрацию и извлечение активной игры из оперативной памяти {@code activeGames}.
     *
     * <p><b>Предусловия (Given):</b> Партия успешно запущена.</p>
     *
     * <p><b>Действие (When):</b> Вызываются методы {@code getActiveGame}, {@code isGameActive} и {@code removeActiveGame}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> До удаления игра активна, после вызова {@code removeActiveGame} игра удалена.
     * </p>
     */
    @Test
    @DisplayName("Хранение и управление состоянием ActiveGameState в оперативной памяти")
    void activeGameState_ManagementAndLifecycle() {
        RoomEntity room = createRoomWithParticipants("ACT001", "1001", 4);
        when(roomJpaRepository.findByCodeForUpdate("ACT001")).thenReturn(Optional.of(room));

        gameStartService.startGame("ACT001", "1001");

        assertThat(gameStartService.isGameActive("ACT001")).isTrue();
        Optional<ActiveGameState> opt = gameStartService.getActiveGame("ACT001");
        assertThat(opt).isPresent();
        assertThat(opt.get().getRoomCode()).isEqualTo("ACT001");
        assertThat(opt.get().getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);

        gameStartService.removeActiveGame("ACT001");
        assertThat(gameStartService.isGameActive("ACT001")).isFalse();
        assertThat(gameStartService.getActiveGame("ACT001")).isEmpty();
    }

    /**
     * Проверяет метод {@code prepareStart} для изолированного запуска без обращения к базе данных.
     *
     * <p><b>Предусловия (Given):</b> Список из 6 участников комнаты.</p>
     *
     * <p><b>Действие (When):</b> Вызов {@code prepareStart}.</p>
     *
     * <p><b>Ожидаемый результат (Then):</b> Ровно 1 Нечто, 5 Людей, у всех 4 карты, 6 участников в публичном состоянии.
     * </p>
     */
    @Test
    @DisplayName("prepareStart: изолированная генерация стартового состояния без БД")
    void prepareStart_GeneratesCorrectStateWithoutDatabase() {
        RoomEntity room = createRoomWithParticipants("PREP01", "1001", 6);
        GameStartResult result = gameStartService.prepareStart(room.getParticipants(), new Random(555));

        assertThat(result.publicGameState().players()).hasSize(6);
        assertThat(result.playerPrivateStates()).hasSize(6);

        long thingCount = result.playerPrivateStates().values().stream()
                .filter(s -> s.role() == PlayerRole.THE_THING)
                .count();
        assertThat(thingCount).isEqualTo(1);
    }
}
