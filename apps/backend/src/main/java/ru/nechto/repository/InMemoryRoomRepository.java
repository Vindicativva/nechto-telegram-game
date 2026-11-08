package ru.nechto.repository;

import org.springframework.stereotype.Repository;
import ru.nechto.model.Room;
import ru.nechto.model.RoomStatus;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryRoomRepository implements RoomRepository {
    private final Map<String, Room> roomsByCode = new ConcurrentHashMap<>();

    private String normalizeCode(String code) {
        if (code == null) return "";
        return code.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public Room save(Room room) {
        if (room == null || room.getCode() == null) {
            throw new IllegalArgumentException("Room and room code must not be null");
        }
        roomsByCode.put(normalizeCode(room.getCode()), room);
        return room;
    }

    @Override
    public Optional<Room> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(roomsByCode.get(normalizeCode(code)));
    }

    @Override
    public Optional<Room> findById(UUID id) {
        if (id == null) return Optional.empty();
        return roomsByCode.values().stream()
                .filter(room -> id.equals(room.getId()))
                .findFirst();
    }

    @Override
    public Optional<Room> findActiveByCreatorId(String creatorId) {
        if (creatorId == null || creatorId.isBlank()) return Optional.empty();
        return roomsByCode.values().stream()
                .filter(room -> creatorId.equals(room.getCreatorId()) &&
                        (room.getStatus() == RoomStatus.LOBBY || room.getStatus() == RoomStatus.IN_PROGRESS))
                .findFirst();
    }

    @Override
    public boolean existsByCode(String code) {
        if (code == null || code.isBlank()) return false;
        return roomsByCode.containsKey(normalizeCode(code));
    }

    @Override
    public void delete(String code) {
        if (code != null) {
            roomsByCode.remove(normalizeCode(code));
        }
    }

    @Override
    public List<Room> findAll() {
        return List.copyOf(roomsByCode.values());
    }
}
