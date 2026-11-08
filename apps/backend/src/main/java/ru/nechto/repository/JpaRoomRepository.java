package ru.nechto.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.nechto.entity.RoomEntity;
import ru.nechto.entity.RoomMapper;
import ru.nechto.entity.RoomParticipantEntity;
import ru.nechto.model.Room;
import ru.nechto.model.RoomParticipant;
import ru.nechto.repository.jpa.RoomJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
public class JpaRoomRepository implements RoomRepository {

    private final RoomJpaRepository roomJpaRepository;

    @Value("${nechto.telegram.bot-username:StayAwayGameBot}")
    private String botUsername;

    public JpaRoomRepository(RoomJpaRepository roomJpaRepository) {
        this.roomJpaRepository = roomJpaRepository;
    }

    private String normalizeCode(String code) {
        if (code == null) return "";
        return code.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public Room save(Room room) {
        if (room == null || room.getCode() == null) {
            throw new IllegalArgumentException("Room and room code must not be null");
        }

        RoomEntity entity = new RoomEntity(
                room.getId() != null ? room.getId() : UUID.randomUUID(),
                normalizeCode(room.getCode()),
                room.getName(),
                room.getCreatorId(),
                room.getStatus(),
                room.getMaxPlayers(),
                room.getCreatedAt() != null ? room.getCreatedAt() : Instant.now(),
                Instant.now(),
                room.getLastActivityAt() != null ? room.getLastActivityAt() : Instant.now()
        );

        if (room.getPlayers() != null) {
            for (RoomParticipant p : room.getPlayers()) {
                RoomParticipantEntity pe = new RoomParticipantEntity(
                        UUID.randomUUID(),
                        entity,
                        p.getTelegramId(),
                        p.getUsername(),
                        p.getDisplayName(),
                        p.isCreator(),
                        p.getConnectionStatus(),
                        p.getJoinedAt() != null ? p.getJoinedAt() : Instant.now(),
                        p.getDisconnectedAt()
                );
                entity.addParticipant(pe);
            }
        }

        RoomEntity saved = roomJpaRepository.save(entity);
        return RoomMapper.toDomain(saved, botUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Room> findByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return roomJpaRepository.findByCodeWithParticipants(normalizeCode(code))
                .map(entity -> RoomMapper.toDomain(entity, botUsername));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Room> findById(UUID id) {
        if (id == null) return Optional.empty();
        return roomJpaRepository.findById(id)
                .map(entity -> RoomMapper.toDomain(entity, botUsername));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Room> findActiveByCreatorId(String creatorId) {
        if (creatorId == null || creatorId.isBlank()) return Optional.empty();
        List<RoomEntity> list = roomJpaRepository.findActiveByCreatorId(creatorId);
        return list.isEmpty() ? Optional.empty() : Optional.of(RoomMapper.toDomain(list.get(0), botUsername));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        if (code == null || code.isBlank()) return false;
        return roomJpaRepository.existsByCode(normalizeCode(code));
    }

    @Override
    @Transactional
    public void delete(String code) {
        if (code != null) {
            roomJpaRepository.findByCode(normalizeCode(code))
                    .ifPresent(roomJpaRepository::delete);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Room> findAll() {
        return roomJpaRepository.findAll().stream()
                .map(entity -> RoomMapper.toDomain(entity, botUsername))
                .toList();
    }
}
