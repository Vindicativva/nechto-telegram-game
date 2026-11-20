package ru.nechto.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import ru.nechto.dto.game.ActiveGameState;
import ru.nechto.dto.game.GameStartResult;
import ru.nechto.dto.game.GameStateEvent;
import ru.nechto.dto.game.PlayerPrivateState;
import ru.nechto.dto.game.PlayerPrivateStateEvent;
import ru.nechto.dto.game.PublicGameState;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.dto.ws.WsEvent;
import ru.nechto.entity.RoomEntity;
import ru.nechto.entity.RoomMapper;
import ru.nechto.entity.RoomParticipantEntity;
import ru.nechto.exception.ForbiddenException;
import ru.nechto.exception.NotEnoughPlayersException;
import ru.nechto.exception.RoomAlreadyStartedException;
import ru.nechto.exception.RoomNotFoundException;
import ru.nechto.exception.TooManyPlayersException;
import ru.nechto.exception.UnauthorizedException;
import ru.nechto.model.InitialDeal;
import ru.nechto.model.PlayerRole;
import ru.nechto.model.Room;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TurnDirection;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.validation.InputValidator;
import ru.nechto.websocket.RoomWebSocketBroadcaster;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис запуска игровой партии:
 * <ul>
 *     <li>Фиксация списка участников, случайная рассадка игроков за столом и установка направления хода по часовой стрелке;</li>
 *     <li>Формирование стартовых рук (по 4 карты каждому) и общей колоды добора через {@link DeckFactory};</li>
 *     <li>Автоматическое назначение ролей по п. 4.2.4 ТЗ (THE_THING держателю карты «Нечто», остальным — HUMAN);</li>
 *     <li>Перевод комнаты в статус IN_PROGRESS (IN_GAME) с блокировкой повторного старта и изменений состава;</li>
 *     <li>Раздельная отправка публичного состояния GAME_STATE в топик /topic/room/{code} и персонального
 *         скрытого состояния PLAYER_PRIVATE_STATE в личную очередь /user/queue/game.</li>
 * </ul>
 */
@Service
public class GameStartService {

    private static final Logger log = LoggerFactory.getLogger(GameStartService.class);

    private final RoomJpaRepository roomJpaRepository;
    private final DeckFactory deckFactory;
    private final RoomWebSocketBroadcaster broadcaster;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Value("${nechto.telegram.bot-username:StayAwayGameBot}")
    private String botUsername = "StayAwayGameBot";

    /**
     * Потокобезопасное хранилище активных партий в оперативной памяти (код комнаты -> состояние игры).
     */
    private final Map<String, ActiveGameState> activeGames = new ConcurrentHashMap<>();

    public GameStartService(RoomJpaRepository roomJpaRepository,
                            DeckFactory deckFactory,
                            RoomWebSocketBroadcaster broadcaster) {
        this(roomJpaRepository, deckFactory, broadcaster, null, new ObjectMapper());
    }

    @Autowired
    public GameStartService(RoomJpaRepository roomJpaRepository,
                            DeckFactory deckFactory,
                            RoomWebSocketBroadcaster broadcaster,
                            @Lazy SimpMessagingTemplate messagingTemplate,
                            ObjectMapper objectMapper) {
        this.roomJpaRepository = roomJpaRepository;
        this.deckFactory = deckFactory != null ? deckFactory : new DeckFactory();
        this.broadcaster = broadcaster;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public void setBotUsername(String botUsername) {
        this.botUsername = botUsername;
    }

    /**
     * Запуск игровой партии с генератором случайных чисел по умолчанию.
     *
     * @param roomCode       код комнаты
     * @param hostTelegramId ID хоста (создателя), инициирующего старт
     * @return результат запуска партии
     */
    @Transactional
    public GameStartResult startGame(String roomCode, String hostTelegramId) {
        return startGame(roomCode, hostTelegramId, new Random());
    }

    /**
     * Запуск игровой партии с передачей генератора псевдослучайных чисел (для детерминированности в тестах).
     *
     * @param roomCode       код комнаты
     * @param hostTelegramId ID хоста (создателя), инициирующего старт
     * @param random         генератор случайных чисел
     * @return результат запуска партии
     */
    @Transactional
    public GameStartResult startGame(String roomCode, String hostTelegramId, Random random) {
        if (hostTelegramId == null || hostTelegramId.isBlank()) {
            throw new UnauthorizedException();
        }
        String normalizedCode = InputValidator.validateAndNormalizeRoomCode(roomCode);

        // Пессимистическая блокировка комнаты для исключения race conditions
        RoomEntity room = roomJpaRepository.findByCodeForUpdate(normalizedCode)
                .filter(r -> r.getStatus() != RoomStatus.ARCHIVED)
                .orElseThrow(RoomNotFoundException::new);

        if (!hostTelegramId.equals(room.getCreatorTelegramId())) {
            throw new ForbiddenException("Только создатель комнаты может запустить игру");
        }

        if (room.getStatus() != RoomStatus.LOBBY) {
            throw new RoomAlreadyStartedException("Игра уже началась");
        }

        int playerCount = room.getParticipants().size();
        if (playerCount < DeckFactory.MIN_PLAYERS) {
            throw new NotEnoughPlayersException("Для старта игры необходимо минимум 4 игрока. Текущее количество: " + playerCount);
        }
        if (playerCount > DeckFactory.MAX_PLAYERS) {
            throw new TooManyPlayersException("Максимальное количество участников для партии: 12. Текущее количество: " + playerCount);
        }

        // Перевод комнаты в статус активной партии IN_PROGRESS (IN_GAME)
        room.setStatus(RoomStatus.IN_PROGRESS);
        Instant now = Instant.now();
        room.setUpdatedAt(now);
        room.setLastActivityAt(now);
        roomJpaRepository.save(room);

        // 1. Случайное перемешивание порядка мест за столом
        List<RoomParticipantEntity> participants = new ArrayList<>(room.getParticipants());
        Random rnd = random != null ? random : new Random();
        Collections.shuffle(participants, rnd);
        List<String> seatingOrder = participants.stream()
                .map(RoomParticipantEntity::getTelegramId)
                .toList();

        // 2. Раздача по 4 карты каждому и формирование колоды добора через DeckFactory
        InitialDeal initialDeal = deckFactory.deal(seatingOrder, rnd);
        String theThingPlayerId = initialDeal.findTheThingPlayerId();

        // 3. Автоматическое назначение ролей по п. 4.2.4 ТЗ
        Map<String, PlayerRole> roles = new HashMap<>();
        Map<String, Integer> quarantine = new HashMap<>();
        Map<String, Boolean> alive = new HashMap<>();
        for (String pid : seatingOrder) {
            if (pid.equals(theThingPlayerId)) {
                roles.put(pid, PlayerRole.THE_THING);
            } else {
                roles.put(pid, PlayerRole.HUMAN);
            }
            quarantine.put(pid, 0);
            alive.put(pid, true);
        }

        // 4. Формирование серверного состояния игры в памяти
        ActiveGameState activeState = new ActiveGameState(
                room.getCode(),
                RoomStatus.IN_PROGRESS,
                seatingOrder,
                roles,
                initialDeal.getPlayerHands(),
                initialDeal.getDrawPile(),
                Collections.emptyList(),
                0,
                TurnDirection.CLOCKWISE,
                quarantine,
                alive,
                now
        );
        activeGames.put(normalizedCode, activeState);

        // 5. Формирование публичного GAME_STATE (строго без карт и ролей)
        PublicGameState publicGameState = activeState.toPublicGameState(participants);
        GameStateEvent publicEvent = GameStateEvent.of(publicGameState);

        // 6. Формирование индивидуального PLAYER_PRIVATE_STATE для каждого игрока
        Map<String, PlayerPrivateState> privateStates = new HashMap<>();
        Map<String, PlayerPrivateStateEvent> privateEvents = new HashMap<>();
        for (String pid : seatingOrder) {
            PlayerPrivateState privateState = activeState.toPlayerPrivateState(pid);
            privateStates.put(pid, privateState);
            privateEvents.put(pid, PlayerPrivateStateEvent.of(privateState));
        }

        Room domainRoom = RoomMapper.toDomain(room, botUsername);
        RoomResponse roomResponse = RoomResponse.from(domainRoom);

        // 7. Рассылка WebSocket-сообщений строго после фиксации транзакции БД
        broadcastAfterCommit(() -> {
            // Публичное состояние в топик /topic/room/{code}
            broadcaster.broadcastGameState(room.getCode(), publicEvent);

            // Персональное приватное состояние в очередь /user/queue/game
            for (Map.Entry<String, PlayerPrivateStateEvent> entry : privateEvents.entrySet()) {
                broadcaster.sendPrivateState(entry.getKey(), entry.getValue());
            }

            // Обратная совместимость с клиентами лобби
            broadcaster.broadcastToRoom(room.getCode(), WsEvent.of("room:game_started", roomResponse));
            broadcaster.broadcastRoomState(room.getCode(), roomResponse);
        });

        log.info("Game started successfully: room={}, players={}, theThingPlayer={}",
                room.getCode(), playerCount, theThingPlayerId);

        return new GameStartResult(roomResponse, publicGameState, privateStates, activeState);
    }

    /**
     * Чистая подготовка старта партии без обращения к БД (для изолированных модульных тестов).
     *
     * @param participants список участников
     * @param random       генератор случайных чисел
     * @return результат запуска партии
     */
    public GameStartResult prepareStart(List<RoomParticipantEntity> participants, Random random) {
        Objects.requireNonNull(participants, "participants must not be null");
        int count = participants.size();
        if (count < DeckFactory.MIN_PLAYERS) {
            throw new NotEnoughPlayersException("Для старта игры необходимо минимум 4 игрока. Текущее количество: " + count);
        }
        if (count > DeckFactory.MAX_PLAYERS) {
            throw new TooManyPlayersException("Максимальное количество участников для партии: 12. Текущее количество: " + count);
        }

        List<RoomParticipantEntity> shuffled = new ArrayList<>(participants);
        Random rnd = random != null ? random : new Random();
        Collections.shuffle(shuffled, rnd);
        List<String> seatingOrder = shuffled.stream().map(RoomParticipantEntity::getTelegramId).toList();

        InitialDeal initialDeal = deckFactory.deal(seatingOrder, rnd);
        String theThingPlayerId = initialDeal.findTheThingPlayerId();

        Map<String, PlayerRole> roles = new HashMap<>();
        Map<String, Integer> quarantine = new HashMap<>();
        Map<String, Boolean> alive = new HashMap<>();
        for (String pid : seatingOrder) {
            roles.put(pid, pid.equals(theThingPlayerId) ? PlayerRole.THE_THING : PlayerRole.HUMAN);
            quarantine.put(pid, 0);
            alive.put(pid, true);
        }

        ActiveGameState activeState = new ActiveGameState(
                "TEST_ROOM",
                RoomStatus.IN_PROGRESS,
                seatingOrder,
                roles,
                initialDeal.getPlayerHands(),
                initialDeal.getDrawPile(),
                Collections.emptyList(),
                0,
                TurnDirection.CLOCKWISE,
                quarantine,
                alive,
                Instant.now()
        );

        PublicGameState publicGameState = activeState.toPublicGameState(shuffled);
        Map<String, PlayerPrivateState> privateStates = new HashMap<>();
        for (String pid : seatingOrder) {
            privateStates.put(pid, activeState.toPlayerPrivateState(pid));
        }

        return new GameStartResult(null, publicGameState, privateStates, activeState);
    }

    /**
     * Возвращает активное состояние игры в оперативной памяти.
     *
     * @param roomCode код комнаты
     * @return {@link Optional} с объектом активной партии
     */
    public Optional<ActiveGameState> getActiveGame(String roomCode) {
        if (roomCode == null) return Optional.empty();
        String normalized = roomCode.trim().toUpperCase(Locale.ROOT);
        return Optional.ofNullable(activeGames.get(normalized));
    }

    /**
     * Проверяет, активна ли игра в указанной комнате.
     *
     * @param roomCode код комнаты
     * @return true, если партия запущена
     */
    public boolean isGameActive(String roomCode) {
        return getActiveGame(roomCode)
                .map(g -> g.getStatus() == RoomStatus.IN_PROGRESS)
                .orElse(false);
    }

    /**
     * Удаляет состояние активной игры (например, при завершении партии или удалении комнаты).
     *
     * @param roomCode код комнаты
     */
    public void removeActiveGame(String roomCode) {
        if (roomCode != null) {
            activeGames.remove(roomCode.trim().toUpperCase(Locale.ROOT));
        }
    }

    private void broadcastAfterCommit(Runnable broadcastAction) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        broadcastAction.run();
                    } catch (Exception e) {
                        log.error("Failed to broadcast WebSocket events after commit", e);
                    }
                }
            });
        } else {
            broadcastAction.run();
        }
    }
}
