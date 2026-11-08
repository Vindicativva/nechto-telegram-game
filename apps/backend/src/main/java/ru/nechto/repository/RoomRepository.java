package ru.nechto.repository;

import ru.nechto.model.Room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository {
    Room save(Room room);
    Optional<Room> findByCode(String code);
    Optional<Room> findById(UUID id);
    Optional<Room> findActiveByCreatorId(String creatorId);
    boolean existsByCode(String code);
    void delete(String code);
    List<Room> findAll();
}
