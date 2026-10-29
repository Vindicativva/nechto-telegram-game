package ru.nechto.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.nechto.dto.request.CreateRoomRequest;
import ru.nechto.dto.response.RoomResponse;
import ru.nechto.dto.ws.HostChangedData;
import ru.nechto.dto.ws.PlayerJoinedData;
import ru.nechto.dto.ws.PlayerLeftData;
import ru.nechto.dto.ws.SettingsUpdatedData;
import ru.nechto.dto.ws.PlayerConnectionChangedData;
import ru.nechto.dto.ws.WsEvent;
import ru.nechto.exception.ActiveRoomExistsException;
import ru.nechto.exception.ForbiddenException;
import ru.nechto.exception.InvalidRoomSettingsException;
import ru.nechto.exception.RoomAlreadyStartedException;
import ru.nechto.exception.RoomFullException;
import ru.nechto.exception.RoomNotFoundException;
import ru.nechto.model.ConnectionStatus;
import ru.nechto.model.Room;
import ru.nechto.model.RoomParticipant;
import ru.nechto.model.RoomStatus;
import ru.nechto.model.TelegramUser;
import ru.nechto.repository.RoomRepository;
import ru.nechto.websocket.RoomWebSocketBroadcaster;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

    private final RoomRepository roomRepository;
    private final RoomWebSocketBroadcaster broadcaster;
    private final SecureRandom random = new SecureRandom();

    @Value("${nechto.telegram.bot-username:StayAwayGameBot}")
    private String botUsername;

    public RoomService(RoomRepository roomRepository, RoomWebSocketBroadcaster broadcaster) {
        this.roomRepository = roomRepository;
        this.broadcaster = broadcaster;
    }

    public RoomResponse createRoom(TelegramUser creator, CreateRoomRequest request) {
        if (creator == null || creator.telegramId() == null || creator.telegramId().isBlank()) {
            throw new IllegalArgumentException("Creator telegramId is required");
        }

        // Validate creator does not have an active room
        Optional<Room> activeRoom = roomRepository.findActiveByCreatorId(creator.telegramId());
        if (activeRoom.isPresent()) {
            throw new ActiveRoomExistsException();
        }

        String name = request.getName() != null ? request.getName().trim() : "";
        if (name.isEmpty() || name.length() > 32) {
            throw new InvalidRoomSettingsException("Название комнаты должно содержать от 1 до 32 символов");
        }

        int maxPlayers = request.getMaxPlayers() != null ? request.getMaxPlayers() : 8;
        if (maxPlayers < 4 || maxPlayers > 12) {
            throw new InvalidRoomSettingsException("Количество игроков должно быть от 4 до 12");
        }

        String code = generateUniqueRoomCode();
        String inviteLink = "https://t.me/" + botUsername + "/app?startapp=" + code;
        Instant now = Instant.now();

        RoomParticipant creatorParticipant = new RoomParticipant(
                creator.telegramId(),
                creator.username(),
                creator.displayName(),
                true,
                ConnectionStatus.ONLINE,
                now,
                null
        );

        Room room = new Room(
                UUID.randomUUID(),
                code,
                inviteLink,
                name,
                creator.telegramId(),
                RoomStatus.LOBBY,
                maxPlayers,
                now,
                now
        );
        room.getPlayers().add(creatorParticipant);

        roomRepository.save(room);
        log.info("Room created: code={}, creatorId={}, name={}", code, creator.telegramId(), name);
        return RoomResponse.from(room);
    }

    public RoomResponse getRoomByCode(String code) {
        Room room = findRoomOrThrow(code);
        return RoomResponse.from(room);
    }

    public Room findRawRoomByCode(String code) {
        return findRoomOrThrow(code);
    }

    public RoomResponse joinRoom(String code, TelegramUser user) {
        if (user == null || user.telegramId() == null || user.telegramId().isBlank()) {
            throw new IllegalArgumentException("User telegramId is required");
        }

        Room room = findRoomOrThrow(code);
        synchronized (room) {
            Instant now = Instant.now();
            Optional<RoomParticipant> existingOpt = room.getPlayers().stream()
                    .filter(p -> user.telegramId().equals(p.getTelegramId()))
                    .findFirst();

            if (existingOpt.isPresent()) {
                // Idempotent reconnection
                RoomParticipant existing = existingOpt.get();
                existing.setConnectionStatus(ConnectionStatus.ONLINE);
                existing.setDisconnectedAt(null);
                if (user.username() != null && !user.username().isBlank()) {
                    existing.setUsername(user.username());
                }
                if (user.displayName() != null && !user.displayName().isBlank()) {
                    existing.setDisplayName(user.displayName());
                }
                room.setLastActivityAt(now);

                broadcaster.broadcastToRoom(room.getCode(), WsEvent.of("room:player_connection_changed",
                        new PlayerConnectionChangedData(user.telegramId(), ConnectionStatus.ONLINE)));

                log.info("Player reconnected to room: code={}, telegramId={}", room.getCode(), user.telegramId());
                return RoomResponse.from(room);
            }

            // New participant joining
            if (room.getStatus() != RoomStatus.LOBBY) {
                throw new RoomAlreadyStartedException();
            }

            if (room.getPlayers().size() >= room.getMaxPlayers()) {
                throw new RoomFullException();
            }

            RoomParticipant newParticipant = new RoomParticipant(
                    user.telegramId(),
                    user.username(),
                    user.displayName(),
                    false,
                    ConnectionStatus.ONLINE,
                    now,
                    null
            );

            room.getPlayers().add(newParticipant);
            room.setLastActivityAt(now);

            RoomResponse response = RoomResponse.from(room);

            // Broadcast room:player_joined immediately to all participants
            broadcaster.broadcastToRoom(room.getCode(), WsEvent.of("room:player_joined",
                    new PlayerJoinedData(newParticipant, response)));

            log.info("Player joined room: code={}, telegramId={}, totalPlayers={}",
                    room.getCode(), user.telegramId(), room.getPlayers().size());

            return response;
        }
    }

    public RoomResponse leaveRoom(String code, String telegramId) {
        if (telegramId == null || telegramId.isBlank()) {
            throw new IllegalArgumentException("telegramId is required");
        }

        Room room = findRoomOrThrow(code);
        synchronized (room) {
            Optional<RoomParticipant> leavingOpt = room.getPlayers().stream()
                    .filter(p -> telegramId.equals(p.getTelegramId()))
                    .findFirst();

            if (leavingOpt.isEmpty()) {
                return RoomResponse.from(room);
            }

            RoomParticipant leaving = leavingOpt.get();
            room.getPlayers().remove(leaving);
            Instant now = Instant.now();
            room.setLastActivityAt(now);

            boolean wasCreator = leaving.isCreator() || telegramId.equals(room.getCreatorId());
            String newCreatorId = null;

            if (wasCreator) {
                if (!room.getPlayers().isEmpty()) {
                    RoomParticipant newHost = room.getPlayers().stream()
                            .min(Comparator.comparing(RoomParticipant::getJoinedAt))
                            .orElse(room.getPlayers().get(0));

                    newHost.setCreator(true);
                    room.setCreatorId(newHost.getTelegramId());
                    newCreatorId = newHost.getTelegramId();

                    broadcaster.broadcastToRoom(room.getCode(), WsEvent.of("room:host_changed",
                            new HostChangedData(telegramId, newCreatorId, new ArrayList<>(room.getPlayers()))));
                    log.info("Room host migrated: code={}, oldCreator={}, newCreator={}",
                            room.getCode(), telegramId, newCreatorId);
                } else {
                    if (room.getStatus() == RoomStatus.LOBBY) {
                        roomRepository.delete(room.getCode());
                        log.info("Empty room deleted: code={}", room.getCode());
                    }
                }
            }

            RoomResponse response = RoomResponse.from(room);
            broadcaster.broadcastToRoom(room.getCode(), WsEvent.of("room:player_left",
                    new PlayerLeftData(telegramId, newCreatorId, response)));

            return response;
        }
    }

    public RoomResponse updateSettings(String code, String telegramId, int maxPlayers) {
        Room room = findRoomOrThrow(code);
        synchronized (room) {
            if (room.getStatus() != RoomStatus.LOBBY) {
                throw new RoomAlreadyStartedException("Настройки комнаты можно изменять только в режиме LOBBY");
            }

            if (!room.getCreatorId().equals(telegramId)) {
                throw new ForbiddenException("Только создатель комнаты может изменять настройки");
            }

            if (maxPlayers < 4 || maxPlayers > 12) {
                throw new InvalidRoomSettingsException("Максимальное число игроков должно быть от 4 до 12");
            }

            if (maxPlayers < room.getPlayers().size()) {
                throw new InvalidRoomSettingsException(HttpStatus.CONFLICT, "INVALID_MAX_PLAYERS",
                        "Максимальное число игроков не может быть меньше текущего количества участников (" + room.getPlayers().size() + ")");
            }

            room.setMaxPlayers(maxPlayers);
            room.setLastActivityAt(Instant.now());

            RoomResponse response = RoomResponse.from(room);
            broadcaster.broadcastToRoom(room.getCode(), WsEvent.of("room:settings_updated",
                    new SettingsUpdatedData(maxPlayers, room.isReadyToStart(), response)));

            log.info("Room settings updated: code={}, newMaxPlayers={}", room.getCode(), maxPlayers);
            return response;
        }
    }

    public void updatePlayerConnectionStatus(String code, String telegramId, ConnectionStatus status) {
        Optional<Room> roomOpt = roomRepository.findByCode(code);
        if (roomOpt.isEmpty()) return;

        Room room = roomOpt.get();
        synchronized (room) {
            room.getPlayers().stream()
                    .filter(p -> p.getTelegramId().equals(telegramId))
                    .findFirst()
                    .ifPresent(p -> {
                        p.setConnectionStatus(status);
                        p.setDisconnectedAt(status == ConnectionStatus.DISCONNECTED ? Instant.now() : null);
                        room.setLastActivityAt(Instant.now());

                        broadcaster.broadcastToRoom(room.getCode(), WsEvent.of("room:player_connection_changed",
                                new PlayerConnectionChangedData(telegramId, status)));
                    });
        }
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupInactiveRooms() {
        Instant now = Instant.now();
        List<Room> allRooms = roomRepository.findAll();
        for (Room room : allRooms) {
            if (room.getStatus() == RoomStatus.LOBBY) {
                boolean allDisconnectedOrEmpty = room.getPlayers().isEmpty() ||
                        room.getPlayers().stream().allMatch(p -> p.getConnectionStatus() == ConnectionStatus.DISCONNECTED);

                if (allDisconnectedOrEmpty && room.getLastActivityAt().plus(INACTIVITY_TIMEOUT).isBefore(now)) {
                    roomRepository.delete(room.getCode());
                    log.info("Inactive room archived/deleted: code={}", room.getCode());
                }
            }
        }
    }

    private Room findRoomOrThrow(String code) {
        return roomRepository.findByCode(code)
                .filter(r -> r.getStatus() != RoomStatus.ARCHIVED)
                .orElseThrow(RoomNotFoundException::new);
    }

    private String generateUniqueRoomCode() {
        for (int i = 0; i < 1000; i++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!roomRepository.existsByCode(code)) {
                return code;
            }
        }
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }
}
