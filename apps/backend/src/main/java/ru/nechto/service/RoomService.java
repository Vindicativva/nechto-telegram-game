package ru.nechto.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import ru.nechto.exception.RoomAlreadyStartedException;
import ru.nechto.exception.RoomFullException;
import ru.nechto.exception.RoomNotFoundException;
import ru.nechto.model.ConnectionStatus;
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

    @Value("${nechto.telegram.bot-username:StayAwayGameBot}")
    private String botUsername;

    public RoomService(RoomJpaRepository roomJpaRepository,
                       RoomWebSocketBroadcaster broadcaster,
                       RoomCodeGenerator codeGenerator) {
        this.roomJpaRepository = roomJpaRepository;
        this.broadcaster = broadcaster;
        this.codeGenerator = codeGenerator;
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
        return RoomResponse.from(RoomMapper.toDomain(saved, botUsername));
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

            broadcastAfterCommit(() -> broadcaster.broadcastToRoom(room.getCode(),
                    WsEvent.of("room:player_connection_changed",
                            new PlayerConnectionChangedData(user.telegramId(), ConnectionStatus.ONLINE))));

            log.info("Player reconnected: code={}, telegramId={}", room.getCode(), user.telegramId());
            return RoomResponse.from(RoomMapper.toDomain(room, botUsername));
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

        // Broadcast room:player_joined strictly after transaction commit
        broadcastAfterCommit(() -> broadcaster.broadcastToRoom(room.getCode(),
                WsEvent.of("room:player_joined", new PlayerJoinedData(domainParticipant, response))));

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
        broadcastAfterCommit(() -> broadcaster.broadcastToRoom(room.getCode(),
                WsEvent.of("room:player_left", new PlayerLeftData(telegramId, finalNewCreatorId, response))));

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

        broadcastAfterCommit(() -> broadcaster.broadcastToRoom(room.getCode(),
                WsEvent.of("room:settings_updated",
                        new SettingsUpdatedData(maxPlayers, domainRoom.isReadyToStart(), response))));

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

                    broadcastAfterCommit(() -> broadcaster.broadcastToRoom(room.getCode(),
                            WsEvent.of("room:player_connection_changed",
                                    new PlayerConnectionChangedData(telegramId, status))));
                });
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
