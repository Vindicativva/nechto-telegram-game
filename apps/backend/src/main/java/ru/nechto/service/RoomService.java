package ru.nechto.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.dto.ws.HostChangedData;
import ru.nechto.dto.ws.PlayerConnectionChangedData;
import ru.nechto.dto.ws.PlayerJoinedData;
import ru.nechto.dto.ws.PlayerLeftData;
import ru.nechto.dto.ws.SettingsUpdatedData;
import ru.nechto.dto.ws.WsEvent;
import ru.nechto.entity.RoomEntity;
import ru.nechto.entity.RoomMapper;
import ru.nechto.entity.RoomParticipantEntity;
import ru.nechto.exception.ActiveRoomExistsException;
import ru.nechto.exception.ForbiddenException;
import ru.nechto.exception.NotEnoughPlayersException;
import ru.nechto.exception.RoomAlreadyStartedException;
import ru.nechto.exception.RoomFullException;
import ru.nechto.exception.RoomNotFoundException;
import ru.nechto.exception.TooManyPlayersException;
import ru.nechto.exception.UnauthorizedException;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.model.InitialDeal;
import ru.nechto.model.Room;
import ru.nechto.model.RoomParticipant;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TelegramUser;
import ru.nechto.repository.jpa.RoomJpaRepository;
import ru.nechto.validation.InputValidator;
import ru.nechto.websocket.RoomWebSocketBroadcaster;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class RoomService {
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final Duration INACTIVITY_TIMEOUT = Duration.ofMinutes(30);

    private final RoomJpaRepository roomJpaRepository;
    private final RoomWebSocketBroadcaster broadcaster;
    private final RoomCodeGenerator codeGenerator;
    private final DeckFactory deckFactory;

    @Value("${nechto.telegram.bot-username:StayAwayGameBot}")
    private String botUsername;

    @Value("${nechto.lobby.host-disconnect-timeout:300s}")
    private Duration hostDisconnectTimeout = Duration.ofMinutes(5);

    public RoomService(RoomJpaRepository roomJpaRepository,
                       RoomWebSocketBroadcaster broadcaster,
                       RoomCodeGenerator codeGenerator) {
        this(roomJpaRepository, broadcaster, codeGenerator, new DeckFactory());
    }

    @Autowired
    public RoomService(RoomJpaRepository roomJpaRepository,
                       RoomWebSocketBroadcaster broadcaster,
                       RoomCodeGenerator codeGenerator,
                       DeckFactory deckFactory) {
        this.roomJpaRepository = roomJpaRepository;
        this.broadcaster = broadcaster;
        this.codeGenerator = codeGenerator;
        this.deckFactory = deckFactory != null ? deckFactory : new DeckFactory();
    }

    public Duration getHostDisconnectTimeout() {
        return hostDisconnectTimeout;
    }

    public void setHostDisconnectTimeout(Duration hostDisconnectTimeout) {
        this.hostDisconnectTimeout = hostDisconnectTimeout;
    }

    public Duration getHostDisconnectTimeout() {
        return hostDisconnectTimeout;
    }

    public void setHostDisconnectTimeout(Duration hostDisconnectTimeout) {
        this.hostDisconnectTimeout = hostDisconnectTimeout;
    }

    @Transactional
    public RoomResponse createRoom(TelegramUser creator, CreateRoomRequest request) {
        if (creator == null || creator.telegramId() == null || creator.telegramId().isBlank()) {
            throw new IllegalArgumentException("Creator telegramId is required");
        }

        // Validate creator display name
        String validCreatorName = InputValidator.validatePlayerName(creator.displayName());

        // Check if creator already has an active room
        List<RoomEntity> activeRooms = roomJpaRepository.findActiveByCreatorId(creator.telegramId());
        if (!activeRooms.isEmpty()) {
            throw new ActiveRoomExistsException();
        }

        // Validate name and maxPlayers
        String name = InputValidator.validateRoomName(request.getName());
        int maxPlayers = request.getMaxPlayers() != null ? request.getMaxPlayers() : 8;
        InputValidator.validateMaxPlayers(maxPlayers, 1);

        String code = codeGenerator.generateCode();
        Instant now = Instant.now();

        RoomEntity roomEntity = new RoomEntity(
                UUID.randomUUID(),
                code,
                name,
                creator.telegramId(),
                RoomStatus.LOBBY,
                maxPlayers,
                now,
                now,
                now
        );

        RoomParticipantEntity creatorParticipant = new RoomParticipantEntity(
                UUID.randomUUID(),
                roomEntity,
                creator.telegramId(),
                creator.username(),
                validCreatorName,
                true,
                ConnectionStatus.ONLINE,
                now,
                null
        );
        roomEntity.addParticipant(creatorParticipant);

        RoomEntity saved = roomJpaRepository.save(roomEntity);
        log.info("Room created: code={}, creatorId={}, name={}", code, creator.telegramId(), name);
        RoomResponse response = RoomResponse.from(RoomMapper.toDomain(saved, botUsername));
        broadcastAfterCommit(() -> broadcaster.broadcastRoomState(code, response));
        return response;
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoomByCode(String code) {
        String normalizedCode = InputValidator.validateAndNormalizeRoomCode(code);
        RoomEntity room = findRoomOrThrow(normalizedCode);
        return RoomResponse.from(RoomMapper.toDomain(room, botUsername));
    }

    @Transactional(readOnly = true)
    public Room findRawRoomByCode(String code) {
        String normalizedCode = InputValidator.validateAndNormalizeRoomCode(code);
        RoomEntity room = findRoomOrThrow(normalizedCode);
        return RoomMapper.toDomain(room, botUsername);
    }

    @Transactional
    public RoomResponse joinRoom(String code, TelegramUser user) {
        if (user == null || user.telegramId() == null || user.telegramId().isBlank()) {
            throw new IllegalArgumentException("User telegramId is required");
        }

        String normalizedCode = InputValidator.validateAndNormalizeRoomCode(code);
        String validDisplayName = InputValidator.validatePlayerName(user.displayName());

        // Pessimistic lock on the room row: SELECT ... FOR UPDATE
        RoomEntity room = roomJpaRepository.findByCodeForUpdate(normalizedCode)
                .filter(r -> r.getStatus() != RoomStatus.ARCHIVED)
                .orElseThrow(RoomNotFoundException::new);

        Instant now = Instant.now();
        Optional<RoomParticipantEntity> existingOpt = room.getParticipants().stream()
                .filter(p -> user.telegramId().equals(p.getTelegramId()))
                .findFirst();

        if (existingOpt.isPresent()) {
            // Idempotent reconnection
            RoomParticipantEntity existing = existingOpt.get();
            existing.setConnectionStatus(ConnectionStatus.ONLINE);
            existing.setDisconnectedAt(null);
            if (user.username() != null && !user.username().isBlank()) {
                existing.setUsername(user.username());
            }
            if (!validDisplayName.isBlank()) {
                existing.setDisplayName(validDisplayName);
            }
            room.setUpdatedAt(now);
            room.setLastActivityAt(now);
            roomJpaRepository.save(room);

            RoomResponse response = RoomResponse.from(RoomMapper.toDomain(room, botUsername));
            broadcastAfterCommit(() -> {
                broadcaster.broadcastToRoom(room.getCode(),
                        WsEvent.of("room:player_connection_changed",
                                new PlayerConnectionChangedData(user.telegramId(), ConnectionStatus.ONLINE)));
                broadcaster.broadcastRoomState(room.getCode(), response);
            });

            log.info("Player reconnected: code={}, telegramId={}", room.getCode(), user.telegramId());
            return response;
        }

        // New participant
        if (room.getStatus() != RoomStatus.LOBBY) {
            throw new RoomAlreadyStartedException();
        }

        if (room.getParticipants().size() >= room.getMaxPlayers()) {
            throw new RoomFullException();
        }

        RoomParticipantEntity newParticipant = new RoomParticipantEntity(
                UUID.randomUUID(),
                room,
                user.telegramId(),
                user.username(),
                validDisplayName,
                false,
                ConnectionStatus.ONLINE,
                now,
                null
        );

        room.addParticipant(newParticipant);
        room.setUpdatedAt(now);
        room.setLastActivityAt(now);
        roomJpaRepository.save(room);

        Room domainRoom = RoomMapper.toDomain(room, botUsername);
        RoomParticipant domainParticipant = RoomMapper.toDomain(newParticipant);
        RoomResponse response = RoomResponse.from(domainRoom);

        // Broadcast room:player_joined and ROOM_STATE strictly after transaction commit
        broadcastAfterCommit(() -> {
            broadcaster.broadcastToRoom(room.getCode(),
                    WsEvent.of("room:player_joined", new PlayerJoinedData(domainParticipant, response)));
            broadcaster.broadcastRoomState(room.getCode(), response);
        });

        log.info("Player joined: code={}, telegramId={}, totalPlayers={}",
                room.getCode(), user.telegramId(), room.getParticipants().size());

        return response;
    }

    @Transactional
    public RoomResponse leaveRoom(String code, String telegramId) {
        if (telegramId == null || telegramId.isBlank()) {
            throw new IllegalArgumentException("telegramId is required");
        }

        String normalizedCode = InputValidator.validateAndNormalizeRoomCode(code);

        // Pessimistic lock on the room row
        RoomEntity room = roomJpaRepository.findByCodeForUpdate(normalizedCode)
                .filter(r -> r.getStatus() != RoomStatus.ARCHIVED)
                .orElseThrow(RoomNotFoundException::new);

        Optional<RoomParticipantEntity> leavingOpt = room.getParticipants().stream()
                .filter(p -> telegramId.equals(p.getTelegramId()))
                .findFirst();

        if (leavingOpt.isEmpty()) {
            return RoomResponse.from(RoomMapper.toDomain(room, botUsername));
        }

        RoomParticipantEntity leaving = leavingOpt.get();
        room.removeParticipant(leaving);
        Instant now = Instant.now();
        room.setUpdatedAt(now);
        room.setLastActivityAt(now);

        boolean wasCreator = leaving.isCreator() || telegramId.equals(room.getCreatorTelegramId());
        String newCreatorId = null;

        if (wasCreator) {
            if (!room.getParticipants().isEmpty()) {
                RoomParticipantEntity nextHost = room.getParticipants().stream()
                        .min(Comparator.comparing(RoomParticipantEntity::getJoinedAt))
                        .orElse(room.getParticipants().get(0));

                nextHost.setCreator(true);
                room.setCreatorTelegramId(nextHost.getTelegramId());
                newCreatorId = nextHost.getTelegramId();

                final String finalNewCreatorId = newCreatorId;
                List<RoomParticipant> remainingParticipants = room.getParticipants().stream()
                        .map(RoomMapper::toDomain)
                        .toList();

                broadcastAfterCommit(() -> broadcaster.broadcastToRoom(room.getCode(),
                        WsEvent.of("room:host_changed",
                                new HostChangedData(telegramId, finalNewCreatorId, remainingParticipants))));

                log.info("Host migrated: code={}, oldCreator={}, newCreator={}",
                        room.getCode(), telegramId, newCreatorId);
            } else {
                if (room.getStatus() == RoomStatus.LOBBY) {
                    roomJpaRepository.delete(room);
                    log.info("Empty room deleted: code={}", room.getCode());
                }
            }
        }

        if (!room.getParticipants().isEmpty() || room.getStatus() != RoomStatus.LOBBY) {
            roomJpaRepository.save(room);
        }

        Room domainRoom = RoomMapper.toDomain(room, botUsername);
        RoomResponse response = RoomResponse.from(domainRoom);

        final String finalNewCreatorId = newCreatorId;
        broadcastAfterCommit(() -> {
            broadcaster.broadcastToRoom(room.getCode(),
                    WsEvent.of("room:player_left", new PlayerLeftData(telegramId, finalNewCreatorId, response)));
            broadcaster.broadcastRoomState(room.getCode(), response);
        });

        return response;
    }

    @Transactional
    public RoomResponse updateSettings(String code, String telegramId, int maxPlayers) {
        String normalizedCode = InputValidator.validateAndNormalizeRoomCode(code);

        // Pessimistic lock on the room row
        RoomEntity room = roomJpaRepository.findByCodeForUpdate(normalizedCode)
                .filter(r -> r.getStatus() != RoomStatus.ARCHIVED)
                .orElseThrow(RoomNotFoundException::new);

        if (room.getStatus() != RoomStatus.LOBBY) {
            throw new RoomAlreadyStartedException("Настройки комнаты можно изменять только в режиме LOBBY");
        }

        if (!room.getCreatorTelegramId().equals(telegramId)) {
            throw new ForbiddenException("Только создатель комнаты может изменять настройки");
        }

        // Validate maxPlayers against 4..12 and current players count
        InputValidator.validateMaxPlayers(maxPlayers, room.getParticipants().size());

        room.setMaxPlayers(maxPlayers);
        Instant now = Instant.now();
        room.setUpdatedAt(now);
        room.setLastActivityAt(now);
        roomJpaRepository.save(room);

        Room domainRoom = RoomMapper.toDomain(room, botUsername);
        RoomResponse response = RoomResponse.from(domainRoom);

        broadcastAfterCommit(() -> {
            broadcaster.broadcastToRoom(room.getCode(),
                    WsEvent.of("room:settings_updated",
                            new SettingsUpdatedData(maxPlayers, domainRoom.isReadyToStart(), response)));
            broadcaster.broadcastRoomState(room.getCode(), response);
        });

        log.info("Room settings updated: code={}, newMaxPlayers={}", room.getCode(), maxPlayers);
        return response;
    }

    @Transactional
    public void updatePlayerConnectionStatus(String code, String telegramId, ConnectionStatus status) {
        String normalizedCode = InputValidator.validateAndNormalizeRoomCode(code);
        Optional<RoomEntity> roomOpt = roomJpaRepository.findByCodeWithParticipants(normalizedCode);
        if (roomOpt.isEmpty()) return;

        RoomEntity room = roomOpt.get();
        room.getParticipants().stream()
                .filter(p -> p.getTelegramId().equals(telegramId))
                .findFirst()
                .ifPresent(p -> {
                    if (p.getConnectionStatus() == status) {
                        return;
                    }
                    p.setConnectionStatus(status);
                    p.setDisconnectedAt(status == ConnectionStatus.DISCONNECTED ? Instant.now() : null);
                    room.setLastActivityAt(Instant.now());
                    roomJpaRepository.save(room);

                    Room domainRoom = RoomMapper.toDomain(room, botUsername);
                    RoomResponse response = RoomResponse.from(domainRoom);
                    broadcastAfterCommit(() -> {
                        broadcaster.broadcastToRoom(room.getCode(),
                                WsEvent.of("room:player_connection_changed",
                                        new PlayerConnectionChangedData(telegramId, status)));
                        broadcaster.broadcastRoomState(room.getCode(), response);
                    });
                });
    }

    /**
     * Запуск игры создателем комнаты с валидацией числа участников (от 4 до 12 человек).
     */
    @Transactional
    public RoomResponse startGame(String code, String telegramId) {
        if (telegramId == null || telegramId.isBlank()) {
            throw new UnauthorizedException();
        }
        String normalizedCode = InputValidator.validateAndNormalizeRoomCode(code);

        RoomEntity room = roomJpaRepository.findByCodeForUpdate(normalizedCode)
                .filter(r -> r.getStatus() != RoomStatus.ARCHIVED)
                .orElseThrow(RoomNotFoundException::new);

        if (!telegramId.equals(room.getCreatorTelegramId())) {
            throw new ForbiddenException("Только создатель комнаты может запустить игру");
        }

        if (room.getStatus() != RoomStatus.LOBBY) {
            throw new RoomAlreadyStartedException("Игра уже началась");
        }

        int playerCount = room.getParticipants().size();
        if (playerCount < 4) {
            throw new NotEnoughPlayersException("Для старта игры необходимо минимум 4 игрока. Текущее количество: " + playerCount);
        }
        if (playerCount > 12) {
            throw new TooManyPlayersException("Максимальное количество участников для партии: 12. Текущее количество: " + playerCount);
        }

        room.setStatus(RoomStatus.IN_PROGRESS);
        Instant now = Instant.now();
        room.setUpdatedAt(now);
        room.setLastActivityAt(now);
        roomJpaRepository.save(room);

        List<String> participantTelegramIds = room.getParticipants().stream()
                .map(RoomParticipantEntity::getTelegramId)
                .toList();
        InitialDeal initialDeal = deckFactory.deal(participantTelegramIds);

        Room domainRoom = RoomMapper.toDomain(room, botUsername);
        RoomResponse response = RoomResponse.from(domainRoom);

        broadcastAfterCommit(() -> {
            broadcaster.broadcastToRoom(room.getCode(), WsEvent.of("room:game_started", response));
            broadcaster.broadcastRoomState(room.getCode(), response);
        });

        log.info("Game started in room: code={}, playersCount={}, thingPlayer={}",
                room.getCode(), playerCount, initialDeal.findTheThingPlayerId());
        return response;
    }

    /**
     * Проверяет статус подключения текущего создателя комнаты и передает права хоста следующему
     * участнику, если создатель отключен дольше заданного таймаута (по умолчанию 5 минут).
     *
     * @param code    код комнаты
     * @param timeout предельный таймаут отсутствия хоста
     * @return true, если произошла смена создателя
     */
    @Transactional
    public boolean checkAndMigrateDisconnectedHost(String code, Duration timeout) {
        String normalizedCode = InputValidator.validateAndNormalizeRoomCode(code);
        Optional<RoomEntity> roomOpt = roomJpaRepository.findByCodeForUpdate(normalizedCode)
                .filter(r -> r.getStatus() == RoomStatus.LOBBY || r.getStatus() == RoomStatus.IN_PROGRESS);
        if (roomOpt.isEmpty()) return false;

        RoomEntity room = roomOpt.get();
        String currentCreatorId = room.getCreatorTelegramId();

        Optional<RoomParticipantEntity> creatorOpt = room.getParticipants().stream()
                .filter(p -> p.getTelegramId().equals(currentCreatorId))
                .findFirst();

        if (creatorOpt.isEmpty()) {
            return migrateToNextHost(room, currentCreatorId);
        }

        RoomParticipantEntity creator = creatorOpt.get();
        if (creator.getConnectionStatus() == ConnectionStatus.DISCONNECTED && creator.getDisconnectedAt() != null) {
            Duration effectiveTimeout = timeout != null ? timeout : hostDisconnectTimeout;
            Instant threshold = Instant.now().minus(effectiveTimeout);
            if (!creator.getDisconnectedAt().isAfter(threshold)) {
                log.info("Host disconnect grace period expired for room {}. Migrating host from {}",
                        room.getCode(), currentCreatorId);
                return migrateToNextHost(room, currentCreatorId);
            }
        }
        return false;
    }

    private boolean migrateToNextHost(RoomEntity room, String oldCreatorId) {
        List<RoomParticipantEntity> candidates = room.getParticipants().stream()
                .filter(p -> !p.getTelegramId().equals(oldCreatorId))
                .sorted(Comparator.comparing(RoomParticipantEntity::getJoinedAt))
                .toList();

        if (candidates.isEmpty()) {
            return false;
        }

        RoomParticipantEntity nextHost = candidates.stream()
                .filter(p -> p.getConnectionStatus() == ConnectionStatus.ONLINE)
                .findFirst()
                .orElse(candidates.get(0));

        room.getParticipants().stream()
                .filter(p -> p.getTelegramId().equals(oldCreatorId))
                .findFirst()
                .ifPresent(p -> p.setCreator(false));

        nextHost.setCreator(true);
        room.setCreatorTelegramId(nextHost.getTelegramId());
        Instant now = Instant.now();
        room.setUpdatedAt(now);
        room.setLastActivityAt(now);
        roomJpaRepository.save(room);

        Room domainRoom = RoomMapper.toDomain(room, botUsername);
        RoomResponse response = RoomResponse.from(domainRoom);

        String newCreatorId = nextHost.getTelegramId();
        broadcastAfterCommit(() -> {
            broadcaster.broadcastToRoom(room.getCode(),
                    WsEvent.of("room:host_changed",
                            new HostChangedData(oldCreatorId, newCreatorId, domainRoom.getPlayers())));
            broadcaster.broadcastRoomState(room.getCode(), response);
        });

        log.info("Host successfully migrated after disconnect timeout: room={}, oldHost={}, newHost={}",
                room.getCode(), oldCreatorId, newCreatorId);
        return true;
    }

    /**
     * Фоновая периодическая проверка комнат на истечение 5-минутного таймаута удержания прав создателя.
     */
    @Scheduled(fixedRate = 5000)
    @Transactional
    public void processDisconnectedHosts() {
        List<RoomEntity> activeRooms = roomJpaRepository.findAll();
        for (RoomEntity room : activeRooms) {
            if (room.getStatus() == RoomStatus.LOBBY || room.getStatus() == RoomStatus.IN_PROGRESS) {
                try {
                    checkAndMigrateDisconnectedHost(room.getCode(), hostDisconnectTimeout);
                } catch (Exception e) {
                    log.error("Failed to process disconnected host in room {}", room.getCode(), e);
                }
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupInactiveRooms() {
        Instant now = Instant.now();
        Instant threshold = now.minus(INACTIVITY_TIMEOUT);
        List<RoomEntity> inactiveRooms = roomJpaRepository.findInactiveLobbies(threshold);

        for (RoomEntity room : inactiveRooms) {
            boolean allDisconnectedOrEmpty = room.getParticipants().isEmpty() ||
                    room.getParticipants().stream().allMatch(p -> p.getConnectionStatus() == ConnectionStatus.DISCONNECTED);

            if (allDisconnectedOrEmpty) {
                roomJpaRepository.delete(room);
                log.info("Inactive room deleted: code={}", room.getCode());
            }
        }
    }

    private RoomEntity findRoomOrThrow(String normalizedCode) {
        return roomJpaRepository.findByCodeWithParticipants(normalizedCode)
                .filter(r -> r.getStatus() != RoomStatus.ARCHIVED)
                .orElseThrow(RoomNotFoundException::new);
    }

    private void broadcastAfterCommit(Runnable broadcastAction) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        broadcastAction.run();
                    } catch (Exception e) {
                        log.error("Failed to broadcast WebSocket event after commit", e);
                    }
                }
            });
        } else {
            broadcastAction.run();
        }
    }
}
